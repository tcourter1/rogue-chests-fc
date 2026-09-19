package com.roguechestsfc;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Rectangle;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.WorldType;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

@Slf4j
public class RogueChestsFcThieverMode extends OverlayPanel
{
    private static final int ROGUES_CASTLE_REGION_ID = 13117;
    private static final int LOOTING_BAG_CONTAINER_ID = 516;
    private static final int MINIMUM_CHEST_XP_DROP = 700;
    private static final int TOTAL_RISK_WARNING_GP = 2_000_000;
    private static final int HIGH_RISK_ITEM_WARNING_GP = 500_000;
    private static final Duration BANK_SOON_WARNING_TIME =
            Duration.ofMinutes(15);
    private static final Duration BANK_NOW_WARNING_TIME =
            Duration.ofMinutes(20);
    private static final Duration BANK_LOCKOUT_WARNING_TIME =
            Duration.ofMinutes(30);
    private static final Duration MISSING_GEAR_POPUP_DURATION =
            Duration.ofSeconds(30);
    private static final Duration COMMUNITY_MESSAGE_INTERVAL =
            Duration.ofHours(4);
    private static final int WARNING_OVERLAY_WIDTH = 330;
    private static final Font WARNING_TITLE_FONT =
            new Font("Arial", Font.BOLD, 19);
    private static final Font WARNING_BODY_FONT =
            new Font("Arial", Font.BOLD, 17);
    private static final Color WARNING_BACKGROUND_COLOR =
            new Color(72, 18, 18, 225);
    private static final Color WARNING_TEXT_COLOR = Color.WHITE;
    private static final Color BANK_LOCKOUT_SCREEN_COLOR =
            new Color(255, 215, 0, 30);

    private static final EquipmentInventorySlot[] REQUIRED_EQUIPMENT_SLOTS =
            {
                    EquipmentInventorySlot.HEAD,
                    EquipmentInventorySlot.CAPE,
                    EquipmentInventorySlot.AMULET,
                    EquipmentInventorySlot.WEAPON,
                    EquipmentInventorySlot.BODY,
                    EquipmentInventorySlot.SHIELD,
                    EquipmentInventorySlot.LEGS,
                    EquipmentInventorySlot.GLOVES,
                    EquipmentInventorySlot.BOOTS,
                    EquipmentInventorySlot.RING
            };

    private final Client client;
    private final ItemManager itemManager;

    private boolean active;
    private boolean wasInWilderness;
    private boolean fifteenMinuteWarningSent;
    private boolean totalRiskWarningSent;
    private boolean gearStateDirty = true;
    private boolean missingLootingBag;
    private boolean wildernessSwordEquipped;
    private boolean highRiskExpensiveItem;
    private int lastThievingXp = -1;
    private int lastBagContentCount = -1;
    private int lastWorld = -1;
    private Instant thievingSessionStartedAt;
    private Instant missingGearPopupUntil;
    private Instant lastCommunityMessageAt;
    private String missingGearPopupText;

    @Inject
    public RogueChestsFcThieverMode(
            Client client,
            ItemManager itemManager)
    {
        this.client = client;
        this.itemManager = itemManager;

        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        panelComponent.setPreferredSize(
                new Dimension(WARNING_OVERLAY_WIDTH, 0)
        );
        panelComponent.setBackgroundColor(WARNING_BACKGROUND_COLOR);
        panelComponent.setBorder(new Rectangle(12, 10, 12, 10));
    }

    boolean shouldAutoEnable()
    {
        return client.getGameState() == GameState.LOGGED_IN
                && isInRoguesCastleRegion();
    }


    void onGameTick(boolean thieverModeActive)
    {
        active = thieverModeActive;

        if (!active)
        {
            wasInWilderness = false;
            lastThievingXp = -1;
            lastCommunityMessageAt = null;
            return;
        }

        if (client.getGameState() != GameState.LOGGED_IN)
        {
            lastThievingXp = -1;
            return;
        }

        showCommunityMessageIfDue();

        int currentWorld = client.getWorld();
        if (currentWorld != lastWorld)
        {
            lastWorld = currentWorld;
            gearStateDirty = true;
        }

        updateLootingBagContentsState();

        boolean inWilderness = isInWilderness();
        boolean enteredWilderness = inWilderness && !wasInWilderness;
        wasInWilderness = inWilderness;

        if (!inWilderness)
        {
            totalRiskWarningSent = false;
        }

        updateThievingTimer();

        if (enteredWilderness)
        {
            gearStateDirty = true;
            checkWildernessEntryEquipment();
        }

        RiskSnapshot risk = null;

        if (gearStateDirty)
        {
            risk = refreshPersistentSafetyState();
            refreshMissingGearPopupState();
            gearStateDirty = false;
        }
        else
        {
            refreshRegionOnlyState();
        }

        if (inWilderness
                && !totalRiskWarningSent)
        {
            if (risk == null)
            {
                risk = calculateRiskSnapshot();
            }

            if (risk.totalValue >= TOTAL_RISK_WARNING_GP)
            {
                showRedChatMessage(
                        "Warning: Your equipment and inventory are worth approximately "
                                + formatGp(risk.totalValue)
                                + " gp."
                );
                totalRiskWarningSent = true;
            }
        }
    }

    private void showCommunityMessageIfDue()
    {
        Instant now = Instant.now();

        if (lastCommunityMessageAt == null)
        {
            lastCommunityMessageAt = now;
            return;
        }

        if (Duration.between(
                lastCommunityMessageAt,
                now
        ).compareTo(COMMUNITY_MESSAGE_INTERVAL) < 0)
        {
            return;
        }

        lastCommunityMessageAt = now;

        client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                "Join the Rogue Chests community! Click the Discord link in the side panel.",
                ""
        );
    }

    void onItemContainerChanged(
            ItemContainerChanged event,
            boolean thieverModeActive)
    {
        if (event == null)
        {
            return;
        }

        int containerId = event.getContainerId();

        if (containerId == InventoryID.INV
                || containerId == InventoryID.WORN)
        {
            gearStateDirty = true;
        }

        if (containerId != LOOTING_BAG_CONTAINER_ID)
        {
            return;
        }

        handleLootingBagContents(
                event.getItemContainer(),
                thieverModeActive
        );
    }


    void reset()
    {
        active = false;
        wasInWilderness = false;
        fifteenMinuteWarningSent = false;
        totalRiskWarningSent = false;
        gearStateDirty = true;
        missingLootingBag = false;
        wildernessSwordEquipped = false;
        highRiskExpensiveItem = false;
        lastThievingXp = -1;
        lastBagContentCount = -1;
        lastWorld = -1;
        thievingSessionStartedAt = null;
        missingGearPopupUntil = null;
        missingGearPopupText = null;
        lastCommunityMessageAt = null;
        panelComponent.getChildren().clear();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!active
                || client.getGameState() != GameState.LOGGED_IN)
        {
            return null;
        }

        List<String> warnings = new ArrayList<>();

        if (isInRoguesCastleRegion())
        {
            if (missingLootingBag)
            {
                warnings.add("LOOTING BAG REQUIRED");
                warnings.add("Bring a looting bag before Thieving Rogue Chests.");
            }

            if (wildernessSwordEquipped)
            {
                warnings.add(
                        "Wilderness Sword is not an acceptable anti-pking weapon for Thieving Rogue Chests"
                );
            }

            if (isBankLockoutActive())
            {
                warnings.add("30+ MINUTES AT ROGUE CHESTS");
                warnings.add("STOP THIEVING. BANK YOUR LOOTING BAG IMMEDIATELY.");
            }
            else if (isBankNowWarningActive())
            {
                warnings.add("20+ MINUTES AT ROGUE CHESTS - BANK ASAP");
            }
        }

        if (highRiskExpensiveItem && isInWilderness())
        {
            warnings.add("HIGH RISK WORLD");
            warnings.add("Item worth over 500,000 gp detected.");
        }

        if (missingGearPopupUntil != null
                && Instant.now().isBefore(missingGearPopupUntil)
                && missingGearPopupText != null)
        {
            warnings.add(missingGearPopupText);
        }

        if (isBankLockoutActive()
                && isInRoguesCastleRegion())
        {
            Graphics2D screenGraphics = (Graphics2D) graphics.create();

            try
            {
                // OverlayPanel rendering is translated to the warning box's
                // position. Move back into canvas coordinates so the tint is
                // rendered by RuneLite every frame instead of painting directly
                // onto the AWT canvas between frames.
                screenGraphics.translate(
                        -getBounds().x,
                        -getBounds().y
                );
                screenGraphics.setColor(BANK_LOCKOUT_SCREEN_COLOR);
                screenGraphics.fillRect(
                        0,
                        0,
                        client.getCanvas().getWidth(),
                        client.getCanvas().getHeight()
                );
            }
            finally
            {
                screenGraphics.dispose();
            }
        }

        if (warnings.isEmpty())
        {
            return null;
        }

        Font originalFont = graphics.getFont();

        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(
                TitleComponent.builder()
                        .text("THIEVER WARNING")
                        .color(WARNING_TEXT_COLOR)
                        .build()
        );

        for (String warning : warnings)
        {
            panelComponent.getChildren().add(
                    new CenteredTextComponent(
                            warning,
                            WARNING_TEXT_COLOR,
                            WARNING_BODY_FONT
                    )
            );
        }

        graphics.setFont(WARNING_TITLE_FONT);

        try
        {
            return super.render(graphics);
        }
        finally
        {
            graphics.setFont(originalFont);
        }
    }

    private void updateThievingTimer()
    {
        int currentXp = client.getSkillExperience(Skill.THIEVING);

        if (lastThievingXp < 0)
        {
            lastThievingXp = currentXp;
            return;
        }

        int xpGain = currentXp - lastThievingXp;
        lastThievingXp = currentXp;

        if (xpGain >= MINIMUM_CHEST_XP_DROP
                && isInRoguesCastleRegion()
                && thievingSessionStartedAt == null)
        {
            thievingSessionStartedAt = Instant.now();
            fifteenMinuteWarningSent = false;

            log.debug(
                    "Thiever timer started from {} Thieving XP gain in Rogue's Castle.",
                    xpGain
            );
        }

        if (thievingSessionStartedAt == null)
        {
            return;
        }

        Duration elapsed = Duration.between(
                thievingSessionStartedAt,
                Instant.now()
        );

        if (!fifteenMinuteWarningSent
                && isInRoguesCastleRegion()
                && elapsed.compareTo(BANK_SOON_WARNING_TIME) >= 0)
        {
            showRedChatMessage(
                    "You have been Thieving Rogue Chests for 15 minutes. You should bank soon."
            );
            fifteenMinuteWarningSent = true;
        }
    }

    private void checkWildernessEntryEquipment()
    {
        EquipmentSnapshot equipment = inspectEquipment();

        if (equipment.missingSlots.size() == 1)
        {
            showRedChatMessage(
                    "You are missing required equipment: "
                            + equipment.missingSlots.get(0)
                            + "."
            );
        }
        else if (equipment.missingSlots.size() >= 2)
        {
            missingGearPopupText =
                    "Missing required equipment:\n"
                            + String.join(", ", equipment.missingSlots);
            missingGearPopupUntil =
                    Instant.now().plus(MISSING_GEAR_POPUP_DURATION);
        }
    }

    private void refreshMissingGearPopupState()
    {
        if (missingGearPopupUntil == null
                || !isInWilderness())
        {
            return;
        }

        EquipmentSnapshot equipment = inspectEquipment();

        if (equipment.missingSlots.size() < 2)
        {
            missingGearPopupUntil = null;
            missingGearPopupText = null;
            return;
        }

        missingGearPopupText =
                "Missing required equipment:\n"
                        + String.join(", ", equipment.missingSlots);
    }

    private RiskSnapshot refreshPersistentSafetyState()
    {
        refreshRegionOnlyState();

        RiskSnapshot risk = calculateRiskSnapshot();
        highRiskExpensiveItem =
                client.getWorldType().contains(WorldType.HIGH_RISK)
                        && risk.highestIndividualItemValue
                        >= HIGH_RISK_ITEM_WARNING_GP;
        return risk;
    }

    private void refreshRegionOnlyState()
    {
        if (!isInRoguesCastleRegion())
        {
            missingLootingBag = false;
            wildernessSwordEquipped = false;
            return;
        }

        ItemContainer inventory =
                client.getItemContainer(InventoryID.INV);
        ItemContainer equipment =
                client.getItemContainer(InventoryID.WORN);

        missingLootingBag = !containsLootingBag(inventory);
        wildernessSwordEquipped =
                isWildernessSwordEquipped(equipment);
    }

    private EquipmentSnapshot inspectEquipment()
    {
        ItemContainer equipment =
                client.getItemContainer(InventoryID.WORN);
        ItemContainer inventory =
                client.getItemContainer(InventoryID.INV);

        List<String> missingSlots = new ArrayList<>();
        boolean twoHandedWeapon = isTwoHandedEquipped(equipment);

        for (EquipmentInventorySlot slot : REQUIRED_EQUIPMENT_SLOTS)
        {
            if (slot == EquipmentInventorySlot.SHIELD
                    && twoHandedWeapon)
            {
                continue;
            }

            if (isEquipmentSlotEmpty(equipment, slot))
            {
                missingSlots.add(formatSlotName(slot));
            }
        }

        if (hasRangedWeapon(equipment, inventory)
                && isEquipmentSlotEmpty(
                equipment,
                EquipmentInventorySlot.AMMO
        ))
        {
            missingSlots.add("Ammo");
        }

        return new EquipmentSnapshot(missingSlots);
    }

    private RiskSnapshot calculateRiskSnapshot()
    {
        long total = 0L;
        int highestIndividual = 0;

        ItemContainer inventory =
                client.getItemContainer(InventoryID.INV);
        ItemContainer equipment =
                client.getItemContainer(InventoryID.WORN);

        for (ItemContainer container :
                new ItemContainer[]{inventory, equipment})
        {
            if (container == null)
            {
                continue;
            }

            for (Item item : container.getItems())
            {
                if (item == null
                        || item.getId() <= 0
                        || item.getQuantity() <= 0)
                {
                    continue;
                }

                int canonicalId = itemManager.canonicalize(item.getId());
                int price = itemManager.getItemPrice(canonicalId);

                if (price <= 0)
                {
                    continue;
                }

                highestIndividual = Math.max(
                        highestIndividual,
                        price
                );

                total += (long) price * item.getQuantity();
            }
        }

        return new RiskSnapshot(total, highestIndividual);
    }

    private boolean hasRangedWeapon(
            ItemContainer equipment,
            ItemContainer inventory)
    {
        return containerHasRangedWeapon(equipment)
                || containerHasRangedWeapon(inventory);
    }

    private boolean containerHasRangedWeapon(
            ItemContainer container)
    {
        if (container == null)
        {
            return false;
        }

        for (Item item : container.getItems())
        {
            if (item == null || item.getId() <= 0)
            {
                continue;
            }

            ItemStats stats = itemManager.getItemStats(
                    itemManager.canonicalize(item.getId())
            );

            if (stats == null || stats.getEquipment() == null)
            {
                continue;
            }

            ItemEquipmentStats equipmentStats = stats.getEquipment();

            if (equipmentStats.getSlot()
                    == EquipmentInventorySlot.WEAPON.getSlotIdx()
                    && equipmentStats.getArange() > 0)
            {
                return true;
            }
        }

        return false;
    }

    private boolean isTwoHandedEquipped(
            ItemContainer equipment)
    {
        Item weapon = getEquipmentItem(
                equipment,
                EquipmentInventorySlot.WEAPON
        );

        if (weapon == null || weapon.getId() <= 0)
        {
            return false;
        }

        ItemStats stats = itemManager.getItemStats(
                itemManager.canonicalize(weapon.getId())
        );

        return stats != null
                && stats.getEquipment() != null
                && stats.getEquipment().isTwoHanded();
    }

    private boolean isWildernessSwordEquipped(
            ItemContainer equipment)
    {
        Item weapon = getEquipmentItem(
                equipment,
                EquipmentInventorySlot.WEAPON
        );

        if (weapon == null || weapon.getId() <= 0)
        {
            return false;
        }

        ItemComposition composition =
                itemManager.getItemComposition(weapon.getId());

        String name = safeLower(composition.getName());

        return name.equals("wilderness sword 1")
                || name.equals("wilderness sword 2")
                || name.equals("wilderness sword 3")
                || name.equals("wilderness sword 4");
    }

    private boolean containsLootingBag(
            ItemContainer inventory)
    {
        if (inventory == null)
        {
            return false;
        }

        for (Item item : inventory.getItems())
        {
            if (item == null || item.getId() <= 0)
            {
                continue;
            }

            ItemComposition composition =
                    itemManager.getItemComposition(item.getId());

            if (safeLower(composition.getName()).equals("looting bag"))
            {
                return true;
            }
        }

        return false;
    }

    private boolean isEquipmentSlotEmpty(
            ItemContainer equipment,
            EquipmentInventorySlot slot)
    {
        Item item = getEquipmentItem(equipment, slot);
        return item == null || item.getId() <= 0;
    }

    private Item getEquipmentItem(
            ItemContainer equipment,
            EquipmentInventorySlot slot)
    {
        if (equipment == null)
        {
            return null;
        }

        return equipment.getItem(slot.getSlotIdx());
    }

    private boolean isInRoguesCastleRegion()
    {
        return client.getLocalPlayer() != null
                && client.getLocalPlayer().getWorldLocation() != null
                && client.getLocalPlayer()
                .getWorldLocation()
                .getRegionID() == ROGUES_CASTLE_REGION_ID;
    }

    @SuppressWarnings("deprecation")
    private boolean isInWilderness()
    {
        return client.getVarbitValue(Varbits.IN_WILDERNESS) == 1;
    }

    private boolean isBankNowWarningActive()
    {
        return active
                && thievingSessionStartedAt != null
                && isInRoguesCastleRegion()
                && Duration.between(
                thievingSessionStartedAt,
                Instant.now()
        ).compareTo(BANK_NOW_WARNING_TIME) >= 0;
    }

    boolean isBankLockoutActive()
    {
        return active
                && thievingSessionStartedAt != null
                && isInRoguesCastleRegion()
                && Duration.between(
                thievingSessionStartedAt,
                Instant.now()
        ).compareTo(BANK_LOCKOUT_WARNING_TIME) >= 0;
    }

    private void resetThievingSession()
    {
        thievingSessionStartedAt = null;
        fifteenMinuteWarningSent = false;
    }

    private void updateLootingBagContentsState()
    {
        ItemContainer bagContents =
                client.getItemContainer(LOOTING_BAG_CONTAINER_ID);

        if (bagContents == null)
        {
            return;
        }

        handleLootingBagContents(
                bagContents,
                active
        );
    }

    private void handleLootingBagContents(
            ItemContainer container,
            boolean thieverModeActive)
    {
        int currentCount = countContainerItems(container);

        if (currentCount < 0)
        {
            return;
        }

        if (thieverModeActive
                && thievingSessionStartedAt != null
                && !isInRoguesCastleRegion()
                && lastBagContentCount > 0
                && currentCount == 0)
        {
            resetThievingSession();
        }

        lastBagContentCount = currentCount;
    }

    private int countContainerItems(
            ItemContainer container)
    {
        return container == null
                ? -1
                : container.count();
    }

    private void showRedChatMessage(String text)
    {
        String message =
                new ChatMessageBuilder()
                        .append(Color.RED, text)
                        .build();

        client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                message,
                ""
        );
    }

    private String formatSlotName(
            EquipmentInventorySlot slot)
    {
        String lower = slot.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0))
                + lower.substring(1);
    }

    private String formatGp(long value)
    {
        return String.format(Locale.US, "%,d", value);
    }

    private String safeLower(String text)
    {
        return text == null
                ? ""
                : text.toLowerCase(Locale.ROOT);
    }

    private static class EquipmentSnapshot
    {
        private final List<String> missingSlots;

        private EquipmentSnapshot(List<String> missingSlots)
        {
            this.missingSlots = missingSlots;
        }
    }

    private static class RiskSnapshot
    {
        private final long totalValue;
        private final int highestIndividualItemValue;

        private RiskSnapshot(
                long totalValue,
                int highestIndividualItemValue)
        {
            this.totalValue = totalValue;
            this.highestIndividualItemValue = highestIndividualItemValue;
        }
    }
    private static class CenteredTextComponent implements LayoutableRenderableEntity
    {
        private final String text;
        private final Color color;
        private final Font font;
        private final Rectangle bounds = new Rectangle();
        private Point preferredLocation = new Point();
        private Dimension preferredSize = new Dimension();

        private CenteredTextComponent(
                String text,
                Color color,
                Font font)
        {
            this.text = text == null ? "" : text;
            this.color = color;
            this.font = font;
        }

        @Override
        public Dimension render(Graphics2D graphics)
        {
            FontMetrics metrics = graphics.getFontMetrics(font);
            List<String> lines = wrapText(text, preferredSize.width, metrics);
            TextComponent textComponent = new TextComponent();
            int y = preferredLocation.y + metrics.getAscent();

            for (String line : lines)
            {
                int lineWidth = metrics.stringWidth(line);
                int x = preferredLocation.x
                        + Math.max(0, (preferredSize.width - lineWidth) / 2);

                textComponent.setPosition(x, y);
                textComponent.setText(line);
                textComponent.setColor(color);
                textComponent.setFont(font);
                textComponent.render(graphics);
                y += metrics.getHeight();
            }

            Dimension dimension = new Dimension(
                    preferredSize.width,
                    lines.size() * metrics.getHeight()
            );

            bounds.setLocation(preferredLocation);
            bounds.setSize(dimension);
            return dimension;
        }

        private static List<String> wrapText(
                String text,
                int maxWidth,
                FontMetrics metrics)
        {
            List<String> lines = new ArrayList<>();

            for (String paragraph : text.split("\\R", -1))
            {
                String trimmed = paragraph.trim();

                if (trimmed.isEmpty())
                {
                    lines.add("");
                    continue;
                }

                String[] words = trimmed.split("\\s+");
                StringBuilder line = new StringBuilder();

                for (String word : words)
                {
                    String candidate = line.length() == 0
                            ? word
                            : line + " " + word;

                    if (line.length() > 0
                            && metrics.stringWidth(candidate) > maxWidth)
                    {
                        lines.add(line.toString());
                        line.setLength(0);
                        line.append(word);
                    }
                    else
                    {
                        line.setLength(0);
                        line.append(candidate);
                    }
                }

                if (line.length() > 0)
                {
                    lines.add(line.toString());
                }
            }

            if (lines.isEmpty())
            {
                lines.add("");
            }

            return lines;
        }

        @Override
        public Rectangle getBounds()
        {
            return bounds;
        }

        @Override
        public void setPreferredLocation(Point position)
        {
            preferredLocation = position == null ? new Point() : position;
        }

        @Override
        public void setPreferredSize(Dimension dimension)
        {
            preferredSize = dimension == null ? new Dimension() : dimension;
        }
    }
}