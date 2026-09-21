package com.roguechestsfc;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class RogueChestsFcLookupService
{
    private static final Duration LOOKUP_COOLDOWN = Duration.ofMinutes(2);
    private static final Duration JOIN_MESSAGE_COOLDOWN = Duration.ofMinutes(2);
    private static final Duration DEPARTED_DISPLAY_DURATION = Duration.ofMinutes(1);
    private static final Duration HIGH_LEVEL_CACHE_TTL = Duration.ofDays(60);
    private static final long HIGH_LEVEL_CACHE_WRITE_INTERVAL_NANOS =
            Duration.ofSeconds(30).toNanos();

    private static final int REQUIRED_THIEVING_LEVEL = 84;
    private static final int LOOKUPS_PER_TICK = 1;
    private static final String CACHE_DIRECTORY = "rogue-chests-fc";
    private static final String CACHE_FILENAME = "high-level-cache.txt";

    private final Client client;
    private final ClientThread clientThread;
    private final HiscoreClient hiscoreClient;
    private final RogueChestsFcConfig config;
    private final RogueChestsFcListRenderer listRenderer;

    private final Map<String, Integer> thievingLevels = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastLookupTimes = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastJoinMessageTimes = new ConcurrentHashMap<>();
    private final Map<String, String> displayNames = new ConcurrentHashMap<>();
    private final Map<String, LowLevelMember> lowLevelMembers = new ConcurrentHashMap<>();

    private final Set<String> pendingLookups = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingJoinMessages = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingF2pJoinMessages = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<String> lookupQueue = new ConcurrentLinkedQueue<>();

    private final Map<String, Instant> knownHighLevelPlayers = new ConcurrentHashMap<>();
    private final Object highLevelCacheFileLock = new Object();

    private volatile Context context = Context.EMPTY;
    private volatile boolean suppressJoinMessages = true;
    private volatile boolean highLevelCacheDirty;
    private File highLevelCacheFile;
    private long lastHighLevelCacheWriteNanos;

    @Inject
    public RogueChestsFcLookupService(
            Client client,
            ClientThread clientThread,
            HiscoreClient hiscoreClient,
            RogueChestsFcConfig config,
            RogueChestsFcListRenderer listRenderer)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.hiscoreClient = hiscoreClient;
        this.config = config;
        this.listRenderer = listRenderer;
    }

    void setContext(Context context)
    {
        this.context = context == null ? Context.EMPTY : context;
    }

    void startUp()
    {
        loadHighLevelCache();
    }

    void shutDown()
    {
        persistHighLevelCacheIfDirty();
        clearRuntimeState();
    }

    void processTick()
    {
        if (context.isStaffFeaturesActive())
        {
            for (int i = 0; i < LOOKUPS_PER_TICK; i++)
            {
                String normalizedName = lookupQueue.poll();
                if (normalizedName == null)
                {
                    break;
                }

                startLookup(normalizedName);
            }

            removeExpiredDepartedMembers();
        }

        persistHighLevelCacheIfDue();
    }

    void setJoinMessagesSuppressed(boolean suppressed)
    {
        suppressJoinMessages = suppressed;
    }

    void queueLookup(String playerName)
    {
        if (playerName == null || playerName.trim().isEmpty())
        {
            return;
        }

        String normalizedName = normalizeName(playerName);
        if (normalizedName.isEmpty() || isBanned(playerName))
        {
            return;
        }

        if (!context.getUnrankedF2pMembers().contains(normalizedName)
                && isKnownHighLevelPlayer(normalizedName))
        {
            pendingJoinMessages.remove(normalizedName);

            boolean changed = thievingLevels.remove(normalizedName) != null;
            changed |= lowLevelMembers.remove(normalizedName) != null;

            if (changed)
            {
                renderMemberList();
            }
            return;
        }

        Instant now = Instant.now();
        Instant lastLookup = lastLookupTimes.get(normalizedName);
        if (lastLookup != null
                && Duration.between(lastLookup, now).compareTo(LOOKUP_COOLDOWN) < 0)
        {
            Integer cachedLevel = thievingLevels.get(normalizedName);
            if (cachedLevel != null)
            {
                showF2pJoinMessage(normalizedName, playerName, cachedLevel);
            }

            updateLowLevelMemberFromCache(normalizedName, playerName);
            renderMemberList();
            return;
        }

        if (!pendingLookups.add(normalizedName))
        {
            return;
        }

        displayNames.put(normalizedName, Text.toJagexName(playerName));
        lookupQueue.add(normalizedName);
    }

    void cancelLookup(String normalizedName)
    {
        if (normalizedName == null || normalizedName.isEmpty())
        {
            return;
        }

        pendingJoinMessages.remove(normalizedName);
        pendingF2pJoinMessages.remove(normalizedName);
        pendingLookups.remove(normalizedName);
        displayNames.remove(normalizedName);
        lookupQueue.removeIf(normalizedName::equals);
    }

    void clearPendingWork()
    {
        lookupQueue.clear();
        pendingLookups.clear();
        pendingJoinMessages.clear();
        pendingF2pJoinMessages.clear();
        displayNames.clear();
    }

    void clearRuntimeState()
    {
        clearPendingWork();
        lastLookupTimes.clear();
        lastJoinMessageTimes.clear();
        thievingLevels.clear();
        lowLevelMembers.clear();
        suppressJoinMessages = true;
    }

    void onMemberJoined(String playerName)
    {
        String normalizedName = normalizeName(playerName);
        if (normalizedName.isEmpty())
        {
            return;
        }

        LowLevelMember existing = lowLevelMembers.get(normalizedName);
        if (existing != null)
        {
            existing.setDepartedAt(null);
            existing.setName(Text.toJagexName(playerName));
        }

        if (isBanned(playerName))
        {
            handleBannedMember(normalizedName);
            if (config.showBannedJoinMessage())
            {
                showJoinNotification(normalizedName, playerName, "(Banned player)");
            }
            renderMemberList();
            return;
        }

        boolean unrankedF2p = context.getUnrankedF2pMembers().contains(normalizedName);
        if (unrankedF2p
                && config.showF2pJoinMessage()
                && !context.getIgnoredNames().contains(normalizedName))
        {
            pendingF2pJoinMessages.add(normalizedName);

            Integer cachedLevel = thievingLevels.get(normalizedName);
            if (cachedLevel != null)
            {
                showF2pJoinMessage(normalizedName, playerName, cachedLevel);
            }
        }

        if (shouldQueueLowLevelJoinMessage(normalizedName))
        {
            pendingJoinMessages.add(normalizedName);

            Integer cachedLevel = thievingLevels.get(normalizedName);
            if (cachedLevel != null)
            {
                showLowLevelJoinMessage(normalizedName, playerName, cachedLevel);
            }
        }

        queueLookup(playerName);
    }

    void onMemberLeft(String normalizedName)
    {
        if (normalizedName == null || normalizedName.isEmpty())
        {
            return;
        }

        pendingJoinMessages.remove(normalizedName);
        pendingF2pJoinMessages.remove(normalizedName);
        markMemberDeparted(normalizedName);
    }

    void handleBannedMember(String normalizedName)
    {
        cancelLookup(normalizedName);
        thievingLevels.remove(normalizedName);
        lowLevelMembers.remove(normalizedName);
    }

    void refreshFromCurrentMember(String playerName)
    {
        String normalizedName = normalizeName(playerName);
        if (normalizedName.isEmpty())
        {
            return;
        }

        LowLevelMember member = lowLevelMembers.get(normalizedName);
        if (member != null)
        {
            member.setName(Text.toJagexName(playerName));
            member.setDepartedAt(null);
        }

        queueLookup(playerName);
    }

    Map<String, Integer> getThievingLevels()
    {
        return thievingLevels;
    }

    List<LowLevelMember> getLowLevelMembers()
    {
        Set<String> ignoredNames = context.getIgnoredNames();
        Set<String> bannedNames = context.getBannedNames();
        List<LowLevelMember> members = new ArrayList<>();

        for (Map.Entry<String, LowLevelMember> entry : lowLevelMembers.entrySet())
        {
            if (!ignoredNames.contains(entry.getKey())
                    && !bannedNames.contains(entry.getKey()))
            {
                members.add(entry.getValue());
            }
        }

        members.sort(
                Comparator.comparing(LowLevelMember::isDeparted)
                        .thenComparing(LowLevelMember::getName, String.CASE_INSENSITIVE_ORDER)
        );

        return members;
    }

    private void startLookup(String normalizedName)
    {
        String playerName = displayNames.getOrDefault(normalizedName, normalizedName);

        if (isBanned(playerName))
        {
            cancelLookup(normalizedName);
            return;
        }

        hiscoreClient.lookupAsync(playerName, HiscoreEndpoint.NORMAL)
                .whenComplete((result, throwable) ->
                {
                    pendingLookups.remove(normalizedName);
                    displayNames.remove(normalizedName);

                    if (throwable != null)
                    {
                        log.debug("Unable to retrieve Hiscores for {}", playerName, throwable);
                        return;
                    }

                    handleHiscoreResult(normalizedName, playerName, result);
                });
    }

    private void handleHiscoreResult(
            String normalizedName,
            String playerName,
            HiscoreResult result)
    {
        if (isBanned(playerName))
        {
            handleBannedMember(normalizedName);
            renderMemberList();
            return;
        }

        if (result == null || !context.isStaffFeaturesActive())
        {
            return;
        }

        Skill thieving = result.getSkill(HiscoreSkill.THIEVING);
        if (thieving == null || thieving.getLevel() < 1)
        {
            return;
        }

        int level = thieving.getLevel();
        Instant now = Instant.now();
        lastLookupTimes.put(normalizedName, now);

        showF2pJoinMessage(normalizedName, playerName, level);
        showLowLevelJoinMessage(normalizedName, playerName, level);

        boolean unrankedF2p = context.getUnrankedF2pMembers().contains(normalizedName);
        if (level >= REQUIRED_THIEVING_LEVEL)
        {
            rememberHighLevelPlayer(normalizedName, now);
            thievingLevels.remove(normalizedName);

            if (unrankedF2p)
            {
                upsertLowLevelMember(normalizedName, playerName);
            }
            else
            {
                lowLevelMembers.remove(normalizedName);
            }

            renderMemberList();
            return;
        }

        forgetHighLevelPlayer(normalizedName);
        thievingLevels.put(normalizedName, level);
        upsertLowLevelMember(normalizedName, playerName);
        renderMemberList();
    }

    private void upsertLowLevelMember(String normalizedName, String playerName)
    {
        Instant departedAt = context.getCurrentMembers().contains(normalizedName)
                ? null
                : Instant.now();

        lowLevelMembers.compute(normalizedName, (key, existing) ->
        {
            if (existing == null)
            {
                return new LowLevelMember(Text.toJagexName(playerName), departedAt);
            }

            existing.setName(Text.toJagexName(playerName));
            existing.setDepartedAt(departedAt);
            return existing;
        });
    }

    private void updateLowLevelMemberFromCache(String normalizedName, String playerName)
    {
        if (isBanned(playerName))
        {
            return;
        }

        Integer level = thievingLevels.get(normalizedName);
        boolean unrankedF2p = context.getUnrankedF2pMembers().contains(normalizedName);

        if (level == null && !unrankedF2p)
        {
            return;
        }

        if (!unrankedF2p && level >= REQUIRED_THIEVING_LEVEL)
        {
            return;
        }

        lowLevelMembers.compute(normalizedName, (key, existing) ->
        {
            if (existing == null)
            {
                return new LowLevelMember(
                        Text.toJagexName(playerName),
                        context.getCurrentMembers().contains(normalizedName)
                                ? null
                                : Instant.now()
                );
            }

            existing.setName(Text.toJagexName(playerName));
            if (context.getCurrentMembers().contains(normalizedName))
            {
                existing.setDepartedAt(null);
            }
            return existing;
        });
    }

    private void markMemberDeparted(String normalizedName)
    {
        LowLevelMember member = lowLevelMembers.get(normalizedName);
        if (member != null && !member.isDeparted())
        {
            member.setDepartedAt(Instant.now());
        }
    }

    private void removeExpiredDepartedMembers()
    {
        Instant now = Instant.now();
        boolean changed = lowLevelMembers.entrySet().removeIf(entry ->
        {
            LowLevelMember member = entry.getValue();
            Instant departedAt = member.getDepartedAt();
            return departedAt != null
                    && Duration.between(departedAt, now).compareTo(DEPARTED_DISPLAY_DURATION) >= 0;
        });

        if (changed)
        {
            renderMemberList();
        }
    }

    private boolean shouldQueueLowLevelJoinMessage(String normalizedName)
    {
        if (suppressJoinMessages
                || !config.showLowLevelJoinMessage()
                || context.getIgnoredNames().contains(normalizedName)
                || context.getBannedNames().contains(normalizedName)
                || context.getUnrankedF2pMembers().contains(normalizedName))
        {
            return false;
        }

        Instant lastMessageTime = lastJoinMessageTimes.get(normalizedName);
        return lastMessageTime == null
                || Duration.between(lastMessageTime, Instant.now())
                .compareTo(JOIN_MESSAGE_COOLDOWN) >= 0;
    }

    private void showF2pJoinMessage(
            String normalizedName,
            String playerName,
            int thievingLevel)
    {
        if (!pendingF2pJoinMessages.remove(normalizedName))
        {
            return;
        }

        if (!config.showF2pJoinMessage()
                || context.getIgnoredNames().contains(normalizedName)
                || context.getBannedNames().contains(normalizedName)
                || !context.getUnrankedF2pMembers().contains(normalizedName)
                || !context.getCurrentMembers().contains(normalizedName))
        {
            return;
        }

        showJoinNotification(
                normalizedName,
                playerName,
                "(F2P - " + thievingLevel + " Thieving)"
        );
    }

    private void showLowLevelJoinMessage(
            String normalizedName,
            String playerName,
            int thievingLevel)
    {
        if (!pendingJoinMessages.remove(normalizedName))
        {
            return;
        }

        if (!config.showLowLevelJoinMessage()
                || thievingLevel >= REQUIRED_THIEVING_LEVEL
                || context.getIgnoredNames().contains(normalizedName)
                || context.getBannedNames().contains(normalizedName)
                || context.getUnrankedF2pMembers().contains(normalizedName)
                || !context.getCurrentMembers().contains(normalizedName))
        {
            return;
        }

        showJoinNotification(
                normalizedName,
                playerName,
                "(" + thievingLevel + " Thieving)"
        );
    }

    private void showJoinNotification(
            String normalizedName,
            String playerName,
            String notificationText)
    {
        if (!context.getCurrentMembers().contains(normalizedName))
        {
            return;
        }

        Instant now = Instant.now();
        Instant lastMessageTime = lastJoinMessageTimes.get(normalizedName);
        if (lastMessageTime != null
                && Duration.between(lastMessageTime, now).compareTo(JOIN_MESSAGE_COOLDOWN) < 0)
        {
            return;
        }

        lastJoinMessageTimes.put(normalizedName, now);

        String message = new ChatMessageBuilder()
                .append(Text.toJagexName(playerName))
                .append(" has joined the channel - ")
                .append(Color.RED, notificationText)
                .build();

        clientThread.invoke(() -> client.addChatMessage(
                ChatMessageType.FRIENDSCHATNOTIFICATION,
                "",
                message,
                ""
        ));
    }

    private boolean isBanned(String playerName)
    {
        return context.getBannedNames().contains(normalizeName(playerName));
    }

    private void renderMemberList()
    {
        listRenderer.applyLevelsToMemberList(
                context.isStaffFeaturesActive(),
                context.getIgnoredNames(),
                context.getBannedNames(),
                context.getUnrankedF2pMembers(),
                thievingLevels,
                this::handleBannedMember
        );
    }

    private void loadHighLevelCache()
    {
        synchronized (highLevelCacheFileLock)
        {
            knownHighLevelPlayers.clear();
            highLevelCacheDirty = false;
            lastHighLevelCacheWriteNanos = System.nanoTime();

            File cacheDirectory = new File(RuneLite.RUNELITE_DIR, CACHE_DIRECTORY);
            highLevelCacheFile = new File(cacheDirectory, CACHE_FILENAME);
            if (!highLevelCacheFile.exists())
            {
                return;
            }

            Instant now = Instant.now();
            try
            {
                for (String line : Files.readAllLines(
                        highLevelCacheFile.toPath(),
                        StandardCharsets.UTF_8))
                {
                    if (line.trim().isEmpty())
                    {
                        continue;
                    }

                    int separatorIndex = line.lastIndexOf('|');
                    if (separatorIndex <= 0 || separatorIndex >= line.length() - 1)
                    {
                        highLevelCacheDirty = true;
                        continue;
                    }

                    String normalizedName = normalizeName(line.substring(0, separatorIndex));
                    if (normalizedName.isEmpty())
                    {
                        highLevelCacheDirty = true;
                        continue;
                    }

                    try
                    {
                        Instant verifiedAt = Instant.ofEpochMilli(
                                Long.parseLong(line.substring(separatorIndex + 1))
                        );

                        if (Duration.between(verifiedAt, now)
                                .compareTo(HIGH_LEVEL_CACHE_TTL) < 0)
                        {
                            knownHighLevelPlayers.put(normalizedName, verifiedAt);
                        }
                        else
                        {
                            highLevelCacheDirty = true;
                        }
                    }
                    catch (RuntimeException exception)
                    {
                        highLevelCacheDirty = true;
                    }
                }
            }
            catch (IOException exception)
            {
                log.debug("Unable to load 84+ Thieving cache", exception);
                knownHighLevelPlayers.clear();
                return;
            }

            persistHighLevelCacheIfDirtyLocked();
        }
    }

    private boolean isKnownHighLevelPlayer(String normalizedName)
    {
        if (normalizedName == null || normalizedName.isEmpty())
        {
            return false;
        }

        Instant verifiedAt = knownHighLevelPlayers.get(normalizedName);
        if (verifiedAt == null)
        {
            return false;
        }

        if (Duration.between(verifiedAt, Instant.now())
                .compareTo(HIGH_LEVEL_CACHE_TTL) < 0)
        {
            return true;
        }

        if (knownHighLevelPlayers.remove(normalizedName, verifiedAt))
        {
            highLevelCacheDirty = true;
        }
        return false;
    }

    private void rememberHighLevelPlayer(String normalizedName, Instant verifiedAt)
    {
        if (normalizedName == null || normalizedName.isEmpty() || verifiedAt == null)
        {
            return;
        }

        knownHighLevelPlayers.put(normalizedName, verifiedAt);
        highLevelCacheDirty = true;
    }

    private void forgetHighLevelPlayer(String normalizedName)
    {
        if (normalizedName != null
                && !normalizedName.isEmpty()
                && knownHighLevelPlayers.remove(normalizedName) != null)
        {
            highLevelCacheDirty = true;
        }
    }

    private void persistHighLevelCacheIfDue()
    {
        if (!highLevelCacheDirty)
        {
            return;
        }

        long now = System.nanoTime();
        if (lastHighLevelCacheWriteNanos != 0L
                && now - lastHighLevelCacheWriteNanos < HIGH_LEVEL_CACHE_WRITE_INTERVAL_NANOS)
        {
            return;
        }

        persistHighLevelCacheIfDirty();
    }

    private void persistHighLevelCacheIfDirty()
    {
        synchronized (highLevelCacheFileLock)
        {
            persistHighLevelCacheIfDirtyLocked();
        }
    }

    private void persistHighLevelCacheIfDirtyLocked()
    {
        if (!highLevelCacheDirty || highLevelCacheFile == null)
        {
            return;
        }

        try
        {
            File cacheDirectory = highLevelCacheFile.getParentFile();
            if (cacheDirectory != null)
            {
                Files.createDirectories(cacheDirectory.toPath());
            }

            List<String> lines = new ArrayList<>();
            Map<String, Instant> sortedEntries = new TreeMap<>(knownHighLevelPlayers);
            for (Map.Entry<String, Instant> entry : sortedEntries.entrySet())
            {
                lines.add(entry.getKey() + "|" + entry.getValue().toEpochMilli());
            }

            Files.write(
                    highLevelCacheFile.toPath(),
                    lines,
                    StandardCharsets.UTF_8
            );

            highLevelCacheDirty = false;
            lastHighLevelCacheWriteNanos = System.nanoTime();
        }
        catch (IOException exception)
        {
            log.debug("Unable to save 84+ Thieving cache", exception);
        }
    }

    private static String normalizeName(String playerName)
    {
        if (playerName == null)
        {
            return "";
        }

        return Text.toJagexName(Text.removeTags(playerName))
                .toLowerCase(Locale.ROOT);
    }

    interface Context
    {
        Context EMPTY = new Context()
        {
            @Override
            public boolean isStaffFeaturesActive()
            {
                return false;
            }

            @Override
            public Set<String> getCurrentMembers()
            {
                return Collections.emptySet();
            }

            @Override
            public Set<String> getUnrankedF2pMembers()
            {
                return Collections.emptySet();
            }

            @Override
            public Set<String> getIgnoredNames()
            {
                return Collections.emptySet();
            }

            @Override
            public Set<String> getBannedNames()
            {
                return Collections.emptySet();
            }
        };

        boolean isStaffFeaturesActive();

        Set<String> getCurrentMembers();

        Set<String> getUnrankedF2pMembers();

        Set<String> getIgnoredNames();

        Set<String> getBannedNames();
    }

    static final class LowLevelMember
    {
        private String name;
        private volatile Instant departedAt;

        LowLevelMember(String name, Instant departedAt)
        {
            this.name = name;
            this.departedAt = departedAt;
        }

        String getName()
        {
            return name;
        }

        void setName(String name)
        {
            this.name = name;
        }

        Instant getDepartedAt()
        {
            return departedAt;
        }

        void setDepartedAt(Instant departedAt)
        {
            this.departedAt = departedAt;
        }

        boolean isDeparted()
        {
            return departedAt != null;
        }
    }
}
