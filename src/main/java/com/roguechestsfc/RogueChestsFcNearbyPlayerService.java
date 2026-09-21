package com.roguechestsfc;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.WorldView;
import net.runelite.api.kit.KitType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.util.Text;

@Singleton
public class RogueChestsFcNearbyPlayerService
{
    private static final String CONFIG_GROUP = "roguechestsfc";
    private static final String CAPTURED_NEARBY_NAMES_KEY =
            "capturedNearbyNames";
    private static final String CAPTURED_NEARBY_NAME_TIMES_KEY =
            "capturedNearbyNameTimes";
    private static final String OVERTIME_WHITELIST_NAMES_KEY =
            "overtimeWhitelistNames";

    private static final long CAPTURED_NEARBY_FLUSH_INTERVAL_NANOS =
            Duration.ofSeconds(1).toNanos();
    private static final long CAPTURED_NEARBY_CLEANUP_INTERVAL_NANOS =
            Duration.ofSeconds(5).toNanos();

    private static final int HIGH_VALUE_VISIBLE_ITEM_GP = 2_000_000;
    private static final int RANKED_HIGH_VALUE_VISIBLE_ITEM_GP = 15_000_000;
    private static final Duration HIGH_VALUE_VISIBLE_ITEM_ALERT_COOLDOWN =
            Duration.ofMinutes(5);
    private static final long HIGH_VALUE_VISIBLE_ITEM_ALERT_CLEANUP_INTERVAL_NANOS =
            Duration.ofMinutes(10).toNanos();

    private static final KitType[] VISIBLE_EQUIPMENT_SLOTS =
            {
                    KitType.HEAD,
                    KitType.CAPE,
                    KitType.AMULET,
                    KitType.TORSO,
                    KitType.LEGS,
                    KitType.HANDS,
                    KitType.BOOTS,
                    KitType.WEAPON,
                    KitType.SHIELD
            };

    private static final String[] TWO_HANDED_WEAPON_NAME_MARKERS =
            {
                    "2h sword",
                    "godsword",
                    "halberd",
                    "spear",
                    "warspear",
                    "maul",
                    "ballista",
                    "shortbow",
                    "longbow",
                    "composite bow",
                    "crystal bow",
                    "dark bow",
                    "twisted bow",
                    "bow of faerdhinen",
                    "seercull",
                    "scythe",
                    "bulwark",
                    "colossal blade",
                    "barrelchest anchor",
                    "soulreaper axe",
                    "dharok's greataxe",
                    "torag's hammers",
                    "karil's crossbow",
                    "eclipse atlatl",
                    "toxic blowpipe"
            };

    private final Client client;
    private final ItemManager itemManager;
    private final ConfigManager configManager;
    private final RogueChestsFcConfig config;
    private final RogueChestsFcFriendsChatService friendsChatService;

    private final Map<String, NearbyMemberTracker> nearbyMemberTrackers =
            new ConcurrentHashMap<>();
    private final Set<String> overtimeTrackingSuppressedUntilExit =
            ConcurrentHashMap.newKeySet();
    private final Set<String> equipmentScannedVisibleMembers =
            ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> lastHighValueEquipmentAlertTimes =
            new ConcurrentHashMap<>();
    private final Map<String, String> pendingCapturedNearbyNames =
            new LinkedHashMap<>();

    private volatile Host host = Host.NO_OP;

    private volatile String cachedOvertimeWhitelistSource;
    private volatile Set<String> cachedOvertimeWhitelistNames =
            Collections.emptySet();

    private volatile String cachedEquipmentIgnoreSource;
    private volatile Set<String> cachedEquipmentInspectionIgnoredNames =
            Collections.emptySet();

    private boolean clearCapturedNearbyOnNextLogin;
    private boolean pendingPostHopOutsiderCapture;

    private long lastCapturedNearbyFlushNanos;
    private long lastCapturedNearbyCleanupNanos;
    private long lastHighValueEquipmentAlertCleanupNanos;

    private int cachedNearbyEnemyCount = -1;
    private int cachedNearbyFcCount = -1;

    @Inject
    public RogueChestsFcNearbyPlayerService(
            Client client,
            ItemManager itemManager,
            ConfigManager configManager,
            RogueChestsFcConfig config,
            RogueChestsFcFriendsChatService friendsChatService)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.configManager = configManager;
        this.config = config;
        this.friendsChatService = friendsChatService;
    }

    void setHost(Host host)
    {
        this.host = host == null ? Host.NO_OP : host;
    }

    void startUp()
    {
        clearCapturedNearbyOnNextLogin =
                client.getGameState() != GameState.LOGGED_IN;

        cachedOvertimeWhitelistSource = null;
        cachedEquipmentIgnoreSource = null;
    }

    void shutDown()
    {
        pendingCapturedNearbyNames.clear();
        pendingPostHopOutsiderCapture = false;
        clearCapturedNearbyOnNextLogin = false;
        lastCapturedNearbyFlushNanos = 0L;
        lastCapturedNearbyCleanupNanos = 0L;
        clearNearbyMemberTracking();
        equipmentScannedVisibleMembers.clear();
        lastHighValueEquipmentAlertTimes.clear();
        cachedNearbyEnemyCount = -1;
        cachedNearbyFcCount = -1;
    }

    void processTick()
    {
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            capturePostHopVisiblePlayers();
        }

        if (!host.isAuthorizedFeaturesActive())
        {
            return;
        }

        updateNearbyMemberTrackingAndCounts();
        flushCapturedNearbyNamesIfDue();
        removeExpiredCapturedNearbyNamesIfDue();
        cleanupHighValueEquipmentAlertCooldownsIfDue();
    }

    void onPlayerSpawned(Player player)
    {
        if (!host.isAuthorizedFeaturesActive()
                || client.getGameState() != GameState.LOGGED_IN
                || cannotTrackNearbyMembers()
                || player == null)
        {
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            return;
        }

        String playerName = player.getName();
        String localPlayerName = localPlayer.getName();
        if (playerName == null || localPlayerName == null)
        {
            return;
        }

        String normalizedName = normalizeName(playerName);
        String normalizedLocalName = normalizeName(localPlayerName);

        if (normalizedName.isEmpty()
                || normalizedName.equals(normalizedLocalName)
                || friendsChatService.getCurrentMembers().contains(normalizedName))
        {
            return;
        }

        queueCapturedNearbyName(playerName);
    }

    void onGameStateChanged(GameState gameState)
    {
        if (gameState != GameState.LOGGED_IN)
        {
            cachedNearbyEnemyCount = -1;
            cachedNearbyFcCount = -1;
        }

        if (gameState == GameState.LOGIN_SCREEN)
        {
            pendingPostHopOutsiderCapture = false;
            pendingCapturedNearbyNames.clear();
            lastCapturedNearbyFlushNanos = 0L;
            clearCapturedNearbyOnNextLogin = true;

            clearNearbyMemberTracking();
            equipmentScannedVisibleMembers.clear();
            host.refreshPanel();
            return;
        }

        if (gameState == GameState.HOPPING)
        {
            pendingPostHopOutsiderCapture = true;

            clearCapturedNearbyNames();
            clearCapturedNearbyOnNextLogin = false;

            clearNearbyMemberTracking();
            equipmentScannedVisibleMembers.clear();
            host.refreshPanel();
            return;
        }

        if (gameState != GameState.LOGGED_IN)
        {
            return;
        }

        if (clearCapturedNearbyOnNextLogin)
        {
            clearCapturedNearbyNames();
            clearCapturedNearbyOnNextLogin = false;
        }
    }

    int getNearbyEnemyCount()
    {
        return cachedNearbyEnemyCount;
    }

    int getNearbyFcCount()
    {
        return cachedNearbyFcCount;
    }

    List<OvertimeMember> getOvertimeMembers()
    {
        if (cannotTrackNearbyMembers())
        {
            return Collections.emptyList();
        }

        Instant now = Instant.now();
        Duration threshold = Duration.ofMinutes(config.overtimeMinutes());
        Set<String> currentMembers = friendsChatService.getCurrentMembers();
        Set<String> whitelist = getOvertimeWhitelistNames();

        List<OvertimeMember> members = new ArrayList<>();

        for (Map.Entry<String, NearbyMemberTracker> entry
                : nearbyMemberTrackers.entrySet())
        {
            if (!currentMembers.contains(entry.getKey())
                    || whitelist.contains(entry.getKey()))
            {
                continue;
            }

            NearbyMemberTracker tracker = entry.getValue();
            Duration elapsed = tracker.getElapsed(now);

            if (elapsed.compareTo(threshold) >= 0)
            {
                members.add(new OvertimeMember(
                        tracker.getDisplayName(),
                        elapsed,
                        tracker.isPaused()
                ));
            }
        }

        members.sort(
                Comparator.comparing(OvertimeMember::getElapsed)
                        .reversed()
                        .thenComparing(
                                OvertimeMember::getName,
                                String.CASE_INSENSITIVE_ORDER
                        )
        );

        return members;
    }

    List<String> getCapturedNearbyPlayerNames()
    {
        return new ArrayList<>(
                getConfiguredPlayerNameMap(
                        config.capturedNearbyNames()
                ).values()
        );
    }

    List<String> getOvertimeWhitelistPlayerNames()
    {
        return new ArrayList<>(
                getConfiguredPlayerNameMap(
                        config.overtimeWhitelistNames()
                ).values()
        );
    }

    void addOvertimeWhitelistNames(String names)
    {
        if (names == null || names.trim().isEmpty())
        {
            return;
        }

        Map<String, String> configured =
                getConfiguredPlayerNameMap(
                        config.overtimeWhitelistNames()
                );

        Arrays.stream(names.split("[,\\r\\n]+"))
                .map(String::trim)
                .map(Text::toJagexName)
                .filter(name -> !name.isEmpty())
                .forEach(name -> configured.putIfAbsent(
                        normalizeName(name),
                        name
                ));

        saveConfiguredNames(
                OVERTIME_WHITELIST_NAMES_KEY,
                configured
        );

        parseConfiguredNames(names).forEach(normalizedName ->
        {
            removeNearbyMemberTracking(normalizedName);
            overtimeTrackingSuppressedUntilExit.remove(normalizedName);
        });

        cachedOvertimeWhitelistSource = null;
    }

    void removeOvertimeWhitelistName(String playerName)
    {
        String normalizedName = normalizeName(playerName);
        if (normalizedName.isEmpty())
        {
            return;
        }

        Map<String, String> configured =
                getConfiguredPlayerNameMap(
                        config.overtimeWhitelistNames()
                );

        if (configured.remove(normalizedName) == null)
        {
            return;
        }

        saveConfiguredNames(
                OVERTIME_WHITELIST_NAMES_KEY,
                configured
        );

        cachedOvertimeWhitelistSource = null;
        removeNearbyMemberTracking(normalizedName);
        overtimeTrackingSuppressedUntilExit.add(normalizedName);
    }

    void removeCapturedNearbyName(String playerName)
    {
        String normalizedName = normalizeName(playerName);
        if (normalizedName.isEmpty())
        {
            return;
        }

        Map<String, String> capturedNames =
                getConfiguredPlayerNameMap(
                        config.capturedNearbyNames()
                );

        if (capturedNames.remove(normalizedName) != null)
        {
            saveConfiguredNames(
                    CAPTURED_NEARBY_NAMES_KEY,
                    capturedNames
            );
        }

        removeCapturedNearbyTimestamp(normalizedName);
    }

    void removeCurrentMembersFromCapturedList(Set<String> memberNames)
    {
        if (memberNames == null || memberNames.isEmpty())
        {
            return;
        }

        Map<String, String> capturedNames =
                getConfiguredPlayerNameMap(
                        config.capturedNearbyNames()
                );

        boolean changed = false;

        for (String normalizedName : memberNames)
        {
            if (capturedNames.remove(normalizedName) != null)
            {
                changed = true;
            }
        }

        if (!changed)
        {
            return;
        }

        saveConfiguredNames(
                CAPTURED_NEARBY_NAMES_KEY,
                capturedNames
        );

        Map<String, Instant> timestamps =
                getCapturedNearbyTimestamps();

        for (String normalizedName : memberNames)
        {
            timestamps.remove(normalizedName);
        }

        saveCapturedNearbyTimestamps(timestamps);
    }

    void clearCapturedNearbyNames()
    {
        pendingCapturedNearbyNames.clear();

        configManager.setConfiguration(
                CONFIG_GROUP,
                CAPTURED_NEARBY_NAMES_KEY,
                ""
        );

        configManager.setConfiguration(
                CONFIG_GROUP,
                CAPTURED_NEARBY_NAME_TIMES_KEY,
                ""
        );

        host.refreshPanel();
    }

    void removeNearbyMemberTracking(String normalizedName)
    {
        if (normalizedName != null)
        {
            nearbyMemberTrackers.remove(normalizedName);
        }
    }

    void removeEquipmentScannedVisibleMember(String normalizedName)
    {
        if (normalizedName != null)
        {
            equipmentScannedVisibleMembers.remove(normalizedName);
        }
    }

    void clearNearbyMemberTracking()
    {
        nearbyMemberTrackers.clear();
        overtimeTrackingSuppressedUntilExit.clear();
    }

    void clearEquipmentScanState()
    {
        equipmentScannedVisibleMembers.clear();
    }

    void clearRuntimeState()
    {
        clearNearbyMemberTracking();
        equipmentScannedVisibleMembers.clear();
        pendingCapturedNearbyNames.clear();
        lastCapturedNearbyFlushNanos = 0L;
        cachedNearbyEnemyCount = -1;
        cachedNearbyFcCount = -1;
    }

    private void capturePostHopVisiblePlayers()
    {
        if (!pendingPostHopOutsiderCapture
                || !host.isAuthorizedFeaturesActive()
                || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            return;
        }

        if (!RogueChestsFcRegions.isTrackingRegion(
                localPlayer.getWorldLocation().getRegionID()))
        {
            pendingPostHopOutsiderCapture = false;
            return;
        }

        String localPlayerName = normalizeName(localPlayer.getName());
        List<String> visiblePlayerNames = new ArrayList<>();

        for (Player player : getVisiblePlayers())
        {
            if (player == null || player.getName() == null)
            {
                continue;
            }

            String normalizedName = normalizeName(player.getName());
            if (normalizedName.isEmpty()
                    || normalizedName.equals(localPlayerName))
            {
                continue;
            }

            visiblePlayerNames.add(player.getName());
        }

        addCapturedNearbyNames(visiblePlayerNames);
        pendingPostHopOutsiderCapture = false;
    }

    private void updateNearbyMemberTrackingAndCounts()
    {
        if (cannotTrackNearbyMembers())
        {
            clearNearbyMemberTracking();
            equipmentScannedVisibleMembers.clear();
            cachedNearbyEnemyCount = -1;
            cachedNearbyFcCount = -1;
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            clearNearbyMemberTracking();
            equipmentScannedVisibleMembers.clear();
            cachedNearbyEnemyCount = -1;
            cachedNearbyFcCount = -1;
            return;
        }

        Set<String> currentMembers =
                friendsChatService.getCurrentMembers();

        String localPlayerName =
                normalizeName(localPlayer.getName());

        Instant now = Instant.now();
        Duration threshold =
                Duration.ofMinutes(config.overtimeMinutes());
        Duration renderGracePeriod =
                Duration.ofSeconds(
                        config.overtimeRenderGraceSeconds()
                );

        Set<String> visibleMembers = new HashSet<>();
        Set<String> overtimeWhitelistNames =
                getOvertimeWhitelistNames();

        boolean staffFeaturesActiveNow =
                host.isStaffFeaturesActive();

        Set<String> equipmentIgnoredNames =
                staffFeaturesActiveNow
                        ? getEquipmentInspectionIgnoredNames()
                        : Collections.emptySet();

        int enemyCount = 0;
        int fcCount = 0;

        for (Player player : getVisiblePlayers())
        {
            if (player == null || player.getName() == null)
            {
                continue;
            }

            String playerName = player.getName();
            String normalizedName = normalizeName(playerName);

            if (normalizedName.isEmpty())
            {
                continue;
            }

            boolean local = normalizedName.equals(localPlayerName);
            boolean currentMember =
                    currentMembers.contains(normalizedName);

            if (local || currentMember)
            {
                fcCount++;
            }
            else
            {
                enemyCount++;
            }

            if (local || !currentMember)
            {
                continue;
            }

            visibleMembers.add(normalizedName);

            if (staffFeaturesActiveNow
                    && !equipmentIgnoredNames.contains(normalizedName))
            {
                scanEquipmentIfNeeded(
                        player,
                        normalizedName,
                        playerName
                );
            }

            if (overtimeWhitelistNames.contains(normalizedName))
            {
                removeNearbyMemberTracking(normalizedName);
                overtimeTrackingSuppressedUntilExit.remove(
                        normalizedName
                );
                continue;
            }

            if (overtimeTrackingSuppressedUntilExit.contains(
                    normalizedName))
            {
                removeNearbyMemberTracking(normalizedName);
                continue;
            }

            NearbyMemberTracker tracker =
                    nearbyMemberTrackers.computeIfAbsent(
                            normalizedName,
                            ignored -> new NearbyMemberTracker(
                                    Text.toJagexName(playerName),
                                    now
                            )
                    );

            tracker.setDisplayName(
                    Text.toJagexName(playerName)
            );
            tracker.resume(now);

            Duration elapsed = tracker.getElapsed(now);

            if (elapsed.compareTo(threshold) >= 0
                    && config.showOvertimeNotification()
                    && tracker.markNotificationSent())
            {
                showOvertimeNotification(
                        playerName,
                        config.overtimeMinutes()
                );
            }
        }

        overtimeTrackingSuppressedUntilExit.removeIf(
                normalizedName ->
                        !visibleMembers.contains(normalizedName)
        );

        equipmentScannedVisibleMembers.removeIf(
                normalizedName ->
                        !visibleMembers.contains(normalizedName)
        );

        for (String normalizedName
                : new ArrayList<>(nearbyMemberTrackers.keySet()))
        {
            NearbyMemberTracker tracker =
                    nearbyMemberTrackers.get(normalizedName);

            if (tracker == null)
            {
                continue;
            }

            if (!currentMembers.contains(normalizedName))
            {
                removeNearbyMemberTracking(normalizedName);
                continue;
            }

            if (visibleMembers.contains(normalizedName))
            {
                continue;
            }

            tracker.pause(now);

            if (tracker.getPausedDuration(now)
                    .compareTo(renderGracePeriod) >= 0)
            {
                removeNearbyMemberTracking(normalizedName);
            }
        }

        cachedNearbyEnemyCount = enemyCount;
        cachedNearbyFcCount = fcCount;
    }

    private void scanEquipmentIfNeeded(
            Player player,
            String normalizedName,
            String playerName)
    {
        if (equipmentScannedVisibleMembers.contains(normalizedName))
        {
            return;
        }

        PlayerComposition composition =
                player.getPlayerComposition();

        if (composition == null)
        {
            return;
        }

        equipmentScannedVisibleMembers.add(normalizedName);

        if (config.showHighValueEquipmentWarning())
        {
            int threshold =
                    friendsChatService
                            .isCurrentMemberRankAtLeastCorporal(playerName)
                            ? RANKED_HIGH_VALUE_VISIBLE_ITEM_GP
                            : HIGH_VALUE_VISIBLE_ITEM_GP;

            showHighValueVisibleEquipmentNotification(
                    playerName,
                    composition,
                    threshold
            );
        }

        if (!config.showMissingEquipmentWarning())
        {
            return;
        }

        int missingSlots =
                countMissingVisibleEquipment(composition);

        if (missingSlots >= config.missingEquipmentThreshold())
        {
            showMissingEquipmentNotification(
                    playerName,
                    missingSlots
            );
        }
    }

    private void showHighValueVisibleEquipmentNotification(
            String playerName,
            PlayerComposition composition,
            int warningThresholdGp)
    {
        String normalizedName = normalizeName(playerName);
        Instant now = Instant.now();

        Instant lastAlert =
                lastHighValueEquipmentAlertTimes.get(
                        normalizedName
                );

        if (lastAlert != null
                && Duration.between(lastAlert, now)
                .compareTo(
                        HIGH_VALUE_VISIBLE_ITEM_ALERT_COOLDOWN
                ) < 0)
        {
            return;
        }

        int[] equipmentIds = composition.getEquipmentIds();
        if (equipmentIds == null || equipmentIds.length == 0)
        {
            return;
        }

        int highestItemId = -1;
        int highestItemPrice = 0;

        for (KitType slot : VISIBLE_EQUIPMENT_SLOTS)
        {
            int itemId = getEquippedItemId(
                    equipmentIds,
                    slot
            );

            if (itemId < 0)
            {
                continue;
            }

            int canonicalId = itemManager.canonicalize(itemId);
            int price = itemManager.getItemPrice(canonicalId);

            if (price >= warningThresholdGp
                    && price > highestItemPrice)
            {
                highestItemId = canonicalId;
                highestItemPrice = price;
            }
        }

        if (highestItemId < 0)
        {
            return;
        }

        String itemName =
                itemManager
                        .getItemComposition(highestItemId)
                        .getName();

        String message =
                new ChatMessageBuilder()
                        .append(
                                Color.RED,
                                Text.toJagexName(playerName)
                        )
                        .append(" is wearing ")
                        .append(Color.RED, itemName)
                        .append(" worth ")
                        .append(
                                Color.RED,
                                String.format(
                                        Locale.ROOT,
                                        "%,d gp",
                                        highestItemPrice
                                )
                        )
                        .append(".")
                        .build();

        client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                message,
                ""
        );

        lastHighValueEquipmentAlertTimes.put(
                normalizedName,
                now
        );
    }

    private int countMissingVisibleEquipment(
            PlayerComposition composition)
    {
        int[] equipmentIds = composition.getEquipmentIds();

        if (equipmentIds == null || equipmentIds.length == 0)
        {
            return VISIBLE_EQUIPMENT_SLOTS.length;
        }

        int weaponId = getEquippedItemId(
                equipmentIds,
                KitType.WEAPON
        );

        boolean twoHandedWeapon =
                isTwoHandedWeapon(weaponId);

        int missingSlots = 0;

        for (KitType slot : VISIBLE_EQUIPMENT_SLOTS)
        {
            if (slot == KitType.SHIELD && twoHandedWeapon)
            {
                continue;
            }

            if (getEquippedItemId(equipmentIds, slot) < 0)
            {
                missingSlots++;
            }
        }

        return missingSlots;
    }

    private int getEquippedItemId(
            int[] equipmentIds,
            KitType slot)
    {
        int slotIndex = slot.getIndex();

        if (slotIndex < 0 || slotIndex >= equipmentIds.length)
        {
            return -1;
        }

        int encodedId = equipmentIds[slotIndex];

        return encodedId >= PlayerComposition.ITEM_OFFSET
                ? encodedId - PlayerComposition.ITEM_OFFSET
                : -1;
    }

    private boolean isTwoHandedWeapon(int weaponId)
    {
        if (weaponId < 0)
        {
            return false;
        }

        ItemStats itemStats =
                itemManager.getItemStats(weaponId);

        if (itemStats != null)
        {
            ItemEquipmentStats equipmentStats =
                    itemStats.getEquipment();

            if (equipmentStats != null
                    && equipmentStats.isTwoHanded())
            {
                return true;
            }
        }

        ItemComposition itemComposition =
                itemManager.getItemComposition(weaponId);

        String name = itemComposition.getName();
        if (name == null)
        {
            return false;
        }

        String weaponName =
                name.toLowerCase(Locale.ROOT);

        for (String marker : TWO_HANDED_WEAPON_NAME_MARKERS)
        {
            if (weaponName.contains(marker))
            {
                return true;
            }
        }

        return false;
    }

    private void showMissingEquipmentNotification(
            String playerName,
            int missingSlots)
    {
        String itemText =
                missingSlots == 1
                        ? " item"
                        : " items";

        String message =
                new ChatMessageBuilder()
                        .append(
                                Color.RED,
                                Text.toJagexName(playerName)
                        )
                        .append(" is missing ")
                        .append(
                                Color.RED,
                                missingSlots + itemText
                        )
                        .append(
                                " from visible equipment slots."
                        )
                        .build();

        client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                message,
                ""
        );
    }

    private void showOvertimeNotification(
            String playerName,
            int limitMinutes)
    {
        String message =
                new ChatMessageBuilder()
                        .append(
                                Color.RED,
                                Text.toJagexName(playerName)
                        )
                        .append(
                                " has remained within render distance for over "
                        )
                        .append(
                                Color.RED,
                                limitMinutes + " minutes"
                        )
                        .append(".")
                        .build();

        client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                message,
                ""
        );
    }

    private void queueCapturedNearbyName(String playerName)
    {
        String normalizedName = normalizeName(playerName);

        if (normalizedName.isEmpty()
                || friendsChatService
                .getCurrentMembers()
                .contains(normalizedName))
        {
            return;
        }

        pendingCapturedNearbyNames.putIfAbsent(
                normalizedName,
                Text.toJagexName(playerName)
        );
    }

    private void flushCapturedNearbyNamesIfDue()
    {
        if (pendingCapturedNearbyNames.isEmpty())
        {
            return;
        }

        long now = System.nanoTime();

        if (lastCapturedNearbyFlushNanos != 0L
                && now - lastCapturedNearbyFlushNanos
                < CAPTURED_NEARBY_FLUSH_INTERVAL_NANOS)
        {
            return;
        }

        lastCapturedNearbyFlushNanos = now;

        List<String> queuedNames =
                new ArrayList<>(
                        pendingCapturedNearbyNames.values()
                );

        pendingCapturedNearbyNames.clear();
        addCapturedNearbyNames(queuedNames);
    }

    private void addCapturedNearbyNames(
            Iterable<String> playerNames)
    {
        Map<String, String> capturedNames =
                getConfiguredPlayerNameMap(
                        config.capturedNearbyNames()
                );

        Map<String, Instant> timestamps =
                getCapturedNearbyTimestamps();

        Set<String> currentMembers =
                friendsChatService.getCurrentMembers();

        Instant capturedAt = Instant.now();
        boolean changed = false;

        for (String playerName : playerNames)
        {
            String normalizedName =
                    normalizeName(playerName);

            if (normalizedName.isEmpty()
                    || currentMembers.contains(normalizedName)
                    || capturedNames.containsKey(normalizedName))
            {
                continue;
            }

            capturedNames.put(
                    normalizedName,
                    Text.toJagexName(playerName)
            );

            timestamps.put(
                    normalizedName,
                    capturedAt
            );

            changed = true;
        }

        if (!changed)
        {
            return;
        }

        saveConfiguredNames(
                CAPTURED_NEARBY_NAMES_KEY,
                capturedNames
        );

        saveCapturedNearbyTimestamps(timestamps);
    }

    private void removeExpiredCapturedNearbyNamesIfDue()
    {
        long now = System.nanoTime();

        if (lastCapturedNearbyCleanupNanos != 0L
                && now - lastCapturedNearbyCleanupNanos
                < CAPTURED_NEARBY_CLEANUP_INTERVAL_NANOS)
        {
            return;
        }

        lastCapturedNearbyCleanupNanos = now;
        removeExpiredCapturedNearbyNames();
    }

    private void removeExpiredCapturedNearbyNames()
    {
        Map<String, String> capturedNames =
                getConfiguredPlayerNameMap(
                        config.capturedNearbyNames()
                );

        if (capturedNames.isEmpty())
        {
            String configuredTimes =
                    config.capturedNearbyNameTimes();

            if (configuredTimes != null
                    && !configuredTimes.trim().isEmpty())
            {
                configManager.setConfiguration(
                        CONFIG_GROUP,
                        CAPTURED_NEARBY_NAME_TIMES_KEY,
                        ""
                );
            }

            return;
        }

        Map<String, Instant> timestamps =
                getCapturedNearbyTimestamps();

        Instant now = Instant.now();
        Duration retention =
                Duration.ofMinutes(
                        config.nearbyOutsiderRetentionMinutes()
                );

        boolean namesChanged = false;
        boolean timestampsChanged = false;

        for (String normalizedName
                : new ArrayList<>(capturedNames.keySet()))
        {
            Instant capturedAt =
                    timestamps.get(normalizedName);

            if (capturedAt == null)
            {
                timestamps.put(normalizedName, now);
                timestampsChanged = true;
                continue;
            }

            if (Duration.between(capturedAt, now)
                    .compareTo(retention) >= 0)
            {
                capturedNames.remove(normalizedName);
                timestamps.remove(normalizedName);
                namesChanged = true;
                timestampsChanged = true;
            }
        }

        for (String normalizedName
                : new ArrayList<>(timestamps.keySet()))
        {
            if (!capturedNames.containsKey(normalizedName))
            {
                timestamps.remove(normalizedName);
                timestampsChanged = true;
            }
        }

        if (namesChanged)
        {
            saveConfiguredNames(
                    CAPTURED_NEARBY_NAMES_KEY,
                    capturedNames
            );
        }

        if (timestampsChanged)
        {
            saveCapturedNearbyTimestamps(timestamps);
        }
    }

    private void removeCapturedNearbyTimestamp(
            String normalizedName)
    {
        Map<String, Instant> timestamps =
                getCapturedNearbyTimestamps();

        if (timestamps.remove(normalizedName) != null)
        {
            saveCapturedNearbyTimestamps(timestamps);
        }
    }

    private Map<String, Instant> getCapturedNearbyTimestamps()
    {
        Map<String, Instant> timestamps =
                new TreeMap<>();

        String configuredTimes =
                config.capturedNearbyNameTimes();

        if (configuredTimes == null
                || configuredTimes.trim().isEmpty())
        {
            return timestamps;
        }

        Arrays.stream(
                        configuredTimes.split("[\\r\\n]+")
                )
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .forEach(line ->
                {
                    int separatorIndex =
                            line.lastIndexOf('|');

                    if (separatorIndex <= 0
                            || separatorIndex
                            >= line.length() - 1)
                    {
                        return;
                    }

                    String normalizedName =
                            normalizeName(
                                    line.substring(
                                            0,
                                            separatorIndex
                                    )
                            );

                    if (normalizedName.isEmpty())
                    {
                        return;
                    }

                    try
                    {
                        long epochMilli =
                                Long.parseLong(
                                        line.substring(
                                                separatorIndex + 1
                                        )
                                );

                        timestamps.put(
                                normalizedName,
                                Instant.ofEpochMilli(epochMilli)
                        );
                    }
                    catch (NumberFormatException ignored)
                    {
                        // Ignore malformed timestamp entries.
                    }
                });

        return timestamps;
    }

    private void saveCapturedNearbyTimestamps(
            Map<String, Instant> timestamps)
    {
        List<String> lines =
                new ArrayList<>(timestamps.size());

        for (Map.Entry<String, Instant> entry
                : timestamps.entrySet())
        {
            lines.add(
                    entry.getKey()
                            + "|"
                            + entry.getValue().toEpochMilli()
            );
        }

        configManager.setConfiguration(
                CONFIG_GROUP,
                CAPTURED_NEARBY_NAME_TIMES_KEY,
                String.join("\n", lines)
        );
    }

    private void cleanupHighValueEquipmentAlertCooldownsIfDue()
    {
        long nowNanos = System.nanoTime();

        if (lastHighValueEquipmentAlertCleanupNanos != 0L
                && nowNanos
                - lastHighValueEquipmentAlertCleanupNanos
                < HIGH_VALUE_VISIBLE_ITEM_ALERT_CLEANUP_INTERVAL_NANOS)
        {
            return;
        }

        lastHighValueEquipmentAlertCleanupNanos = nowNanos;

        Instant cutoff =
                Instant.now().minus(
                        HIGH_VALUE_VISIBLE_ITEM_ALERT_COOLDOWN
                );

        lastHighValueEquipmentAlertTimes
                .entrySet()
                .removeIf(
                        entry ->
                                !entry.getValue().isAfter(cutoff)
                );
    }

    private Set<String> getOvertimeWhitelistNames()
    {
        String source = config.overtimeWhitelistNames();

        if (!Objects.equals(
                source,
                cachedOvertimeWhitelistSource))
        {
            cachedOvertimeWhitelistNames =
                    parseConfiguredNames(source);
            cachedOvertimeWhitelistSource = source;
        }

        return cachedOvertimeWhitelistNames;
    }

    private Set<String> getEquipmentInspectionIgnoredNames()
    {
        String source =
                config.equipmentInspectionIgnoredNames();

        if (!Objects.equals(
                source,
                cachedEquipmentIgnoreSource))
        {
            cachedEquipmentInspectionIgnoredNames =
                    parseConfiguredNames(source);
            cachedEquipmentIgnoreSource = source;
        }

        return cachedEquipmentInspectionIgnoredNames;
    }

    private Set<String> parseConfiguredNames(
            String configuredNames)
    {
        Set<String> names = new HashSet<>();

        if (configuredNames == null
                || configuredNames.trim().isEmpty())
        {
            return names;
        }

        Arrays.stream(
                        configuredNames.split("[,\\r\\n]+")
                )
                .map(String::trim)
                .map(RogueChestsFcNearbyPlayerService::normalizeName)
                .filter(name -> !name.isEmpty())
                .forEach(names::add);

        return names;
    }

    private Map<String, String> getConfiguredPlayerNameMap(
            String configuredNames)
    {
        Map<String, String> names =
                new TreeMap<>();

        if (configuredNames == null
                || configuredNames.trim().isEmpty())
        {
            return names;
        }

        Arrays.stream(
                        configuredNames.split("[,\\r\\n]+")
                )
                .map(String::trim)
                .map(Text::toJagexName)
                .filter(name -> !name.isEmpty())
                .forEach(name ->
                        names.putIfAbsent(
                                normalizeName(name),
                                name
                        )
                );

        return names;
    }

    private void saveConfiguredNames(
            String configKey,
            Map<String, String> names)
    {
        configManager.setConfiguration(
                CONFIG_GROUP,
                configKey,
                String.join("\n", names.values())
        );

        host.refreshPanel();
    }

    private Iterable<? extends Player> getVisiblePlayers()
    {
        WorldView worldView =
                client.getTopLevelWorldView();

        return worldView == null
                ? Collections.emptyList()
                : worldView.players();
    }

    private boolean cannotTrackNearbyMembers()
    {
        if (client.getGameState() != GameState.LOGGED_IN
                || client.getFriendsChatManager() == null)
        {
            return true;
        }

        Player localPlayer = client.getLocalPlayer();

        return localPlayer == null
                || !RogueChestsFcRegions.isTrackingRegion(
                localPlayer
                        .getWorldLocation()
                        .getRegionID()
        );
    }

    private static String normalizeName(String playerName)
    {
        if (playerName == null)
        {
            return "";
        }

        return Text.toJagexName(
                Text.removeTags(playerName)
        ).toLowerCase(Locale.ROOT);
    }

    interface Host
    {
        Host NO_OP = new Host()
        {
            @Override
            public boolean isAuthorizedFeaturesActive()
            {
                return false;
            }

            @Override
            public boolean isStaffFeaturesActive()
            {
                return false;
            }

            @Override
            public void refreshPanel()
            {
            }
        };

        boolean isAuthorizedFeaturesActive();

        boolean isStaffFeaturesActive();

        void refreshPanel();
    }

    static final class OvertimeMember
    {
        private final String name;
        private final Duration elapsed;
        private final boolean paused;

        OvertimeMember(
                String name,
                Duration elapsed,
                boolean paused)
        {
            this.name = name;
            this.elapsed = elapsed;
            this.paused = paused;
        }

        String getName()
        {
            return name;
        }

        Duration getElapsed()
        {
            return elapsed;
        }

        boolean isPaused()
        {
            return paused;
        }
    }

    private static final class NearbyMemberTracker
    {
        private String displayName;
        private Instant activeSince;
        private Duration accumulatedActiveTime =
                Duration.ZERO;
        private Instant pausedAt;
        private boolean notificationSent;

        NearbyMemberTracker(
                String displayName,
                Instant activeSince)
        {
            this.displayName = displayName;
            this.activeSince = activeSince;
        }

        String getDisplayName()
        {
            return displayName;
        }

        void setDisplayName(String displayName)
        {
            this.displayName = displayName;
        }

        void pause(Instant now)
        {
            if (pausedAt != null)
            {
                return;
            }

            if (activeSince != null)
            {
                accumulatedActiveTime =
                        accumulatedActiveTime.plus(
                                Duration.between(
                                        activeSince,
                                        now
                                )
                        );
            }

            pausedAt = now;
            activeSince = null;
        }

        void resume(Instant now)
        {
            if (pausedAt == null)
            {
                return;
            }

            pausedAt = null;
            activeSince = now;
        }

        Duration getElapsed(Instant now)
        {
            if (pausedAt != null || activeSince == null)
            {
                return accumulatedActiveTime;
            }

            return accumulatedActiveTime.plus(
                    Duration.between(
                            activeSince,
                            now
                    )
            );
        }

        Duration getPausedDuration(Instant now)
        {
            if (pausedAt == null)
            {
                return Duration.ZERO;
            }

            return Duration.between(pausedAt, now);
        }

        boolean isPaused()
        {
            return pausedAt != null;
        }

        boolean markNotificationSent()
        {
            if (notificationSent)
            {
                return false;
            }

            notificationSent = true;
            return true;
        }
    }
}