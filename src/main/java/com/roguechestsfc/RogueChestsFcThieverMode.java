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
import javax.inject.Singleton;
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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class RogueChestsFcThieverMode extends OverlayPanel
{
    private static final String CONFIG_GROUP = "roguechestsfc";
    private static final String ANTI_PKER_MODE_KEY = "antiPkerMode";
    private static final int ROGUES_CASTLE_REGION_ID = 13117;
    private static final int LOOTING_BAG_CONTAINER_ID = 516;
    private static final int LOOTING_BAG_ITEM_ID = 11941;
    private static final int LOOTING_BAG_CHECK_ITEM_OP = 2;
    private static final Duration LOOTING_BAG_CHECK_TIMEOUT =
            Duration.ofSeconds(5);
    private static final int MINIMUM_CHEST_XP_DROP = 700;
    private static final int TOTAL_RISK_WARNING_GP = 2_000_000;
    private static final int HIGH_RISK_ITEM_WARNING_GP = 500_000;
    private static final Duration BANK_SOON_WARNING_TIME =
            Duration.ofMinutes(15);
    private static final Duration BANK_NOW_WARNING_TIME =
            Duration.ofMinutes(20);
    private static final Duration BANK_LOCKOUT_WARNING_TIME =
            Duration.ofMinutes(30);
    private static final Duration THIEVING_INACTIVITY_PAUSE =
            Duration.ofSeconds(30);
    private static final Duration AWAY_FROM_CASTLE_RESET_TIME =
            Duration.ofMinutes(10);
    private static final Duration MAX_ACTIVE_TICK_GAP =
            Duration.ofSeconds(2);
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
    private final ConfigManager configManager;

    private boolean antiPkerMode;

    private boolean active;
    private boolean wasInRoguesCastleForAutoEnable;
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
    private Boolean lastInventoryHadLootingBag;
    private boolean bankInterfaceOpen;
    private Instant pendingLootingBagCheckUntil;
    private Instant thievingSessionStartedAt;
    private Instant lastQualifyingThievingXpAt;
    private Instant lastThievingTimerUpdateAt;
    private boolean inRequiredFriendsChat;
    private Instant lastAwayTimerUpdateAt;
    private Duration accumulatedThievingTime = Duration.ZERO;
    private Duration accumulatedAwayFromCastleTime = Duration.ZERO;
    private final List<String> missingEquipmentSlots = new ArrayList<>();
    private Instant lastCommunityMessageAt;

    @Inject
    public RogueChestsFcThieverMode(
            Client client,
            ItemManager itemManager,
            ConfigManager configManager)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.configManager = configManager;
        this.antiPkerMode = Boolean.parseBoolean(
                configManager.getConfiguration(
                        CONFIG_GROUP,
                        ANTI_PKER_MODE_KEY
                )
        );

        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        panelComponent.setPreferredSize(
                new Dimension(WARNING_OVERLAY_WIDTH, 0)
        );
        panelComponent.setBackgroundColor(WARNING_BACKGROUND_COLOR);
        panelComponent.setBorder(new Rectangle(12, 10, 12, 10));
    }

    boolean toggleAntiPkerMode()
    {
        antiPkerMode = !antiPkerMode;
        configManager.setConfiguration(
                CONFIG_GROUP,
                ANTI_PKER_MODE_KEY,
                antiPkerMode
        );

        totalRiskWarningSent = false;
        gearStateDirty = true;

        if (antiPkerMode)
        {
            missingLootingBag = false;
            missingEquipmentSlots.clear();
        }
        else if (active
                && client.getGameState() == GameState.LOGGED_IN
                && isInWilderness())
        {
            refreshMissingEquipmentState(true);
        }

        return antiPkerMode;
    }

    boolean consumeAutoEnableTrigger()
    {
        boolean inRoguesCastle =
                client.getGameState() == GameState.LOGGED_IN
                        && isInRoguesCastleRegion();

        boolean enteredRoguesCastle =
                inRoguesCastle
                        && !wasInRoguesCastleForAutoEnable;

        wasInRoguesCastleForAutoEnable = inRoguesCastle;
        return enteredRoguesCastle;
    }


    void onGameTick(
            boolean thieverModeActive,
            boolean inRequiredFriendsChat)
    {
        active = thieverModeActive;
        this.inRequiredFriendsChat = inRequiredFriendsChat;

        if (!active)
        {
            wasInWilderness = false;
            lastThievingXp = -1;
            lastThievingTimerUpdateAt = null;
            lastAwayTimerUpdateAt = null;
            lastCommunityMessageAt = null;
            return;
        }

        if (client.getGameState() != GameState.LOGGED_IN)
        {
            lastThievingXp = -1;
            lastThievingTimerUpdateAt = null;
            lastAwayTimerUpdateAt = null;
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
        processPendingLootingBagCheck();

        boolean inWilderness = isInWilderness();
        boolean enteredWilderness = inWilderness && !wasInWilderness;
        boolean leftWilderness = !inWilderness && wasInWilderness;
        wasInWilderness = inWilderness;

        if (!inWilderness)
        {
            totalRiskWarningSent = false;
        }

        updateThievingTimer(inRequiredFriendsChat);

        if (enteredWilderness || leftWilderness)
        {
            gearStateDirty = true;
        }

        RiskSnapshot risk = null;

        if (gearStateDirty)
        {
            risk = refreshPersistentSafetyState();
            refreshMissingEquipmentState(enteredWilderness);
            gearStateDirty = false;
        }
        else
        {
            refreshRegionOnlyState();
        }

        if (inWilderness
                && !antiPkerMode
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

        if (containerId == InventoryID.INV)
        {
            handleLootingBagPresence(event.getItemContainer());
        }

        if (containerId != LOOTING_BAG_CONTAINER_ID)
        {
            return;
        }

        ItemContainer bagContents = event.getItemContainer();

        handleLootingBagContents(
                bagContents,
                thieverModeActive
        );
    }


    void onGameStateChanged(GameState gameState)
    {
        if (gameState == GameState.LOGGED_IN)
        {
            return;
        }

        lastThievingXp = -1;
        lastThievingTimerUpdateAt = null;
        lastAwayTimerUpdateAt = null;
    }

    void onWidgetLoaded(int groupId)
    {
        if (groupId == InterfaceID.BANKMAIN
                || groupId == InterfaceID.BANK_DEPOSITBOX)
        {
            bankInterfaceOpen = true;
        }
    }

    void onWidgetClosed(int groupId)
    {
        if (groupId == InterfaceID.BANKMAIN
                || groupId == InterfaceID.BANK_DEPOSITBOX)
        {
            bankInterfaceOpen = false;
        }
    }

    void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (event == null)
        {
            return;
        }

        String rawOption = event.getMenuOption();
        String rawTarget = event.getMenuTarget();
        String option = safeLower(Text.removeTags(rawOption)).trim();
        String target = safeLower(Text.removeTags(rawTarget)).trim();

        boolean lootingBagCheck =
                active
                        && thievingSessionStartedAt != null
                        && event.isItemOp()
                        && event.getItemId() == LOOTING_BAG_ITEM_ID
                        && event.getItemOp() == LOOTING_BAG_CHECK_ITEM_OP
                        && option.equals("check");

        if (lootingBagCheck)
        {
            log.info(
                    "[RogueChestsFC][LootingBagDebug] CHECK detected: action={} id={} param0={} param1={} itemId={} itemOp={}",
                    event.getMenuAction(),
                    event.getId(),
                    event.getParam0(),
                    event.getParam1(),
                    event.getItemId(),
                    event.getItemOp()
            );

            pendingLootingBagCheckUntil =
                    Instant.now().plus(LOOTING_BAG_CHECK_TIMEOUT);
            return;
        }

        if (!active || thievingSessionStartedAt == null)
        {
            return;
        }

        if (!bankInterfaceOpen)
        {
            return;
        }

        if (target.contains("looting bag")
                || option.equals("empty containers"))
        {
            resetThievingSession();
        }
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
        lastInventoryHadLootingBag = null;
        bankInterfaceOpen = false;
        pendingLootingBagCheckUntil = null;
        inRequiredFriendsChat = false;
        thievingSessionStartedAt = null;
        lastQualifyingThievingXpAt = null;
        lastThievingTimerUpdateAt = null;
        lastAwayTimerUpdateAt = null;
        accumulatedThievingTime = Duration.ZERO;
        accumulatedAwayFromCastleTime = Duration.ZERO;
        missingEquipmentSlots.clear();
        lastCommunityMessageAt = null;
        panelComponent.getChildren().clear();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!active
                || !inRequiredFriendsChat
                || client.getGameState() != GameState.LOGGED_IN)
        {
            return null;
        }

        boolean inRoguesCastle = isInRoguesCastleRegion();
        boolean bankLockoutActive = isBankLockoutActive();
        List<String> warnings = getActiveWarnings(
                inRoguesCastle,
                isInWilderness(),
                bankLockoutActive
        );

        if (bankLockoutActive && inRoguesCastle)
        {
            Graphics2D screenGraphics = (Graphics2D) graphics.create();

            try
            {
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

    private List<String> getActiveWarnings(
            boolean inRoguesCastle,
            boolean inWilderness,
            boolean bankLockoutActive)
    {
        List<String> warnings = new ArrayList<>();

        if (inRoguesCastle)
        {
            if (bankLockoutActive)
            {
                warnings.add("30+ MINUTES AT ROGUE CHESTS");
                warnings.add("STOP THIEVING. BANK YOUR LOOTING BAG IMMEDIATELY.");
            }
            else if (isBankNowWarningActive())
            {
                warnings.add("20+ MINUTES AT ROGUE CHESTS - BANK ASAP");
            }
        }

        if (!antiPkerMode
                && inWilderness
                && missingEquipmentSlots.size() >= 2)
        {
            warnings.add("MISSING REQUIRED EQUIPMENT");
            warnings.add(String.join(", ", missingEquipmentSlots));
        }

        if (inRoguesCastle && missingLootingBag)
        {
            warnings.add("LOOTING BAG REQUIRED");
            warnings.add("Bring a looting bag before Thieving Rogue Chests.");
        }

        if (inRoguesCastle && wildernessSwordEquipped)
        {
            warnings.add(
                    "Wilderness Sword is not an acceptable anti-pking weapon for Thieving Rogue Chests"
            );
        }

        if (highRiskExpensiveItem && inWilderness)
        {
            warnings.add("HIGH RISK WORLD");
            warnings.add("Item worth over 500,000 gp detected.");
        }

        return warnings;
    }

    private void updateThievingTimer(
            boolean inRequiredFriendsChat)
    {
        Instant now = Instant.now();
        int currentXp = client.getSkillExperience(Skill.THIEVING);
        boolean inRoguesCastle = isInRoguesCastleRegion();

        updateAwayFromCastleResetState(inRoguesCastle, now);

        if (thievingSessionStartedAt == null)
        {
            lastAwayTimerUpdateAt = now;
        }

        if (lastThievingXp < 0)
        {
            lastThievingXp = currentXp;
            lastThievingTimerUpdateAt = now;
            return;
        }

        int xpGain = currentXp - lastThievingXp;
        lastThievingXp = currentXp;

        boolean qualifyingChestXp =
                xpGain >= MINIMUM_CHEST_XP_DROP
                        && inRoguesCastle;

        if (qualifyingChestXp)
        {
            if (thievingSessionStartedAt == null)
            {
                thievingSessionStartedAt = now;
                accumulatedThievingTime = Duration.ZERO;
                accumulatedAwayFromCastleTime = Duration.ZERO;
                fifteenMinuteWarningSent = false;

                log.debug(
                        "Thiever timer started from {} Thieving XP gain in Rogue's Castle.",
                        xpGain
                );
            }

            lastQualifyingThievingXpAt = now;
        }

        if (thievingSessionStartedAt == null)
        {
            lastThievingTimerUpdateAt = now;
            return;
        }

        boolean xpGraceActive =
                lastQualifyingThievingXpAt != null
                        && Duration.between(
                        lastQualifyingThievingXpAt,
                        now
                ).compareTo(THIEVING_INACTIVITY_PAUSE) <= 0;

        if (lastThievingTimerUpdateAt != null
                && inRoguesCastle
                && xpGraceActive)
        {
            Duration tickDelta =
                    Duration.between(lastThievingTimerUpdateAt, now);

            if (!tickDelta.isNegative()
                    && tickDelta.compareTo(MAX_ACTIVE_TICK_GAP) <= 0)
            {
                accumulatedThievingTime =
                        accumulatedThievingTime.plus(tickDelta);
            }
        }

        lastThievingTimerUpdateAt = now;

        if (!fifteenMinuteWarningSent
                && inRoguesCastle
                && accumulatedThievingTime.compareTo(
                BANK_SOON_WARNING_TIME
        ) >= 0)
        {
            showRedChatMessage(
                    "You have been Thieving Rogue Chests for 15 minutes. You should bank soon."
            );
            fifteenMinuteWarningSent = true;
        }
    }

    private void updateAwayFromCastleResetState(
            boolean inRoguesCastle,
            Instant now)
    {
        if (thievingSessionStartedAt == null)
        {
            accumulatedAwayFromCastleTime = Duration.ZERO;
            lastAwayTimerUpdateAt = now;
            return;
        }

        if (inRoguesCastle)
        {
            accumulatedAwayFromCastleTime = Duration.ZERO;
            lastAwayTimerUpdateAt = now;
            return;
        }

        if (lastAwayTimerUpdateAt != null)
        {
            Duration tickDelta =
                    Duration.between(lastAwayTimerUpdateAt, now);

            if (!tickDelta.isNegative()
                    && tickDelta.compareTo(MAX_ACTIVE_TICK_GAP) <= 0)
            {
                accumulatedAwayFromCastleTime =
                        accumulatedAwayFromCastleTime.plus(tickDelta);
            }
        }

        lastAwayTimerUpdateAt = now;

        if (accumulatedAwayFromCastleTime.compareTo(
                AWAY_FROM_CASTLE_RESET_TIME
        ) >= 0)
        {
            resetThievingSession();
        }
    }

    private void refreshMissingEquipmentState(
            boolean notifySingleMissingSlot)
    {
        missingEquipmentSlots.clear();

        if (antiPkerMode || !isInWilderness())
        {
            return;
        }

        EquipmentSnapshot equipment = inspectEquipment();

        if (notifySingleMissingSlot
                && equipment.missingSlots.size() == 1)
        {
            showRedChatMessage(
                    "You are missing required equipment: "
                            + equipment.missingSlots.get(0)
                            + "."
            );
        }

        if (equipment.missingSlots.size() >= 2)
        {
            missingEquipmentSlots.addAll(equipment.missingSlots);
        }
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

        missingLootingBag = !antiPkerMode
                && !containsLootingBag(inventory);
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

            if (safeLower(composition.getName()).startsWith("looting bag"))
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
                && accumulatedThievingTime.compareTo(
                BANK_NOW_WARNING_TIME
        ) >= 0;
    }

    boolean isBankLockoutActive()
    {
        return active
                && thievingSessionStartedAt != null
                && isInRoguesCastleRegion()
                && accumulatedThievingTime.compareTo(
                BANK_LOCKOUT_WARNING_TIME
        ) >= 0;
    }

    void resetThievingSession()
    {
        lastThievingXp = -1;
        thievingSessionStartedAt = null;
        lastQualifyingThievingXpAt = null;
        lastThievingTimerUpdateAt = null;
        lastAwayTimerUpdateAt = null;
        accumulatedThievingTime = Duration.ZERO;
        accumulatedAwayFromCastleTime = Duration.ZERO;
        fifteenMinuteWarningSent = false;
        pendingLootingBagCheckUntil = null;
    }

    private void handleLootingBagPresence(
            ItemContainer inventory)
    {
        if (client.getGameState() != GameState.LOGGED_IN
                || inventory == null)
        {
            return;
        }

        boolean hasLootingBag = containsLootingBag(inventory);

        if (active
                && thievingSessionStartedAt != null
                && Boolean.TRUE.equals(lastInventoryHadLootingBag)
                && !hasLootingBag)
        {
            resetThievingSession();
        }

        lastInventoryHadLootingBag = hasLootingBag;
    }

    private void processPendingLootingBagCheck()
    {
        if (pendingLootingBagCheckUntil == null)
        {
            return;
        }

        Instant now = Instant.now();

        if (now.isAfter(pendingLootingBagCheckUntil))
        {
            log.info(
                    "[RogueChestsFC][LootingBagDebug] CHECK timed out before an empty-bag result was observed."
            );
            pendingLootingBagCheckUntil = null;
            return;
        }

        Widget universe =
                client.getWidget(InterfaceID.WildernessLootingbag.UNIVERSE);

        boolean interfaceVisible =
                universe != null && !universe.isHidden();
        boolean emptyTextVisible =
                interfaceVisible && isLootingBagEmptyTextVisible();

        log.info(
                "[RogueChestsFC][LootingBagDebug] CHECK pending: interfaceVisible={} emptyTextVisible={}",
                interfaceVisible,
                emptyTextVisible
        );

        if (!emptyTextVisible)
        {
            return;
        }

        pendingLootingBagCheckUntil = null;

        log.info(
                "[RogueChestsFC][LootingBagDebug] Empty checked bag confirmed from looting-bag interface text; resetting thieving session."
        );
        resetThievingSession();
    }

    private boolean isLootingBagEmptyTextVisible()
    {
        Widget[] widgets =
                {
                        client.getWidget(InterfaceID.WildernessLootingbag.UNIVERSE),
                        client.getWidget(InterfaceID.WildernessLootingbag.TITLE),
                        client.getWidget(InterfaceID.WildernessLootingbag.UNIVERSE_GRAPHIC1),
                        client.getWidget(InterfaceID.WildernessLootingbag.FRAME),
                        client.getWidget(InterfaceID.WildernessLootingbag.FRAME_GRAPHIC0),
                        client.getWidget(InterfaceID.WildernessLootingbag.ITEMS),
                        client.getWidget(InterfaceID.WildernessLootingbag.TOTAL),
                        client.getWidget(InterfaceID.WildernessLootingbag.TOOLTIP)
                };

        for (Widget widget : widgets)
        {
            if (widgetContainsEmptyLootingBagText(widget, 0))
            {
                return true;
            }
        }

        return false;
    }

    private boolean widgetContainsEmptyLootingBagText(
            Widget widget,
            int depth)
    {
        if (widget == null
                || depth > 4
                || widget.isHidden())
        {
            return false;
        }

        String widgetText = widget.getText();
        String text =
                safeLower(
                        widgetText == null
                                ? ""
                                : Text.removeTags(widgetText)
                ).trim();

        if (text.contains("the bag is empty"))
        {
            return true;
        }

        Widget[] children = widget.getDynamicChildren();

        for (Widget child : children)
        {
            if (widgetContainsEmptyLootingBagText(child, depth + 1))
            {
                return true;
            }
        }

        children = widget.getStaticChildren();

        for (Widget child : children)
        {
            if (widgetContainsEmptyLootingBagText(child, depth + 1))
            {
                return true;
            }
        }

        return false;
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