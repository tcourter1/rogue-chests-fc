package com.roguechestsfc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.FriendsChatRank;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.WorldService;
import net.runelite.client.util.Text;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

@Slf4j
@Singleton
public class RogueChestsFcFriendsChatService
{
    private static final String REQUIRED_FRIENDS_CHAT = "Rogue Chests";

    private static final Duration STAFF_RANK_CACHE_DURATION =
            Duration.ofMinutes(30);
    private static final Duration STAFF_RANK_REVOCATION_GRACE =
            Duration.ofSeconds(10);

    private static final Duration RANKED_MEMBER_CACHE_TTL =
            Duration.ofDays(7);
    private static final Duration RANKED_MEMBER_CACHE_REFRESH_INTERVAL =
            Duration.ofDays(1);
    private static final long RANKED_MEMBER_CACHE_WRITE_INTERVAL_NANOS =
            Duration.ofSeconds(30).toNanos();
    private static final long RANKED_MEMBER_CACHE_CLEANUP_INTERVAL_NANOS =
            Duration.ofDays(1).toNanos();

    private static final String CACHE_DIRECTORY = "rogue-chests-fc";
    private static final String RANKED_MEMBER_CACHE_FILENAME =
            "ranked-member-cache.txt";

    private final Client client;
    private final ClientThread clientThread;
    private final WorldService worldService;
    private final RogueChestsFcLookupService lookupService;
    private final RogueChestsFcListRenderer listRenderer;

    private final Set<String> currentMembers =
            ConcurrentHashMap.newKeySet();
    private final Set<String> unrankedF2pMembers =
            ConcurrentHashMap.newKeySet();
    private final Map<String, FriendsChatRank> currentMemberRanks =
            new ConcurrentHashMap<>();

    private final Map<String, Instant> knownRankedMembers =
            new ConcurrentHashMap<>();
    private final Object rankedMemberCacheFileLock = new Object();

    private volatile Host host = Host.NO_OP;

    private File rankedMemberCacheFile;
    private volatile boolean rankedMemberCacheDirty;
    private long lastRankedMemberCacheWriteNanos;
    private long lastRankedMemberCacheCleanupNanos;

    private volatile boolean cachedStaffRankAuthorized;
    private volatile Instant cachedStaffRankCheckedAt;
    private volatile Instant unauthorizedStaffRankObservedAt;

    @Inject
    public RogueChestsFcFriendsChatService(
            Client client,
            ClientThread clientThread,
            WorldService worldService,
            RogueChestsFcLookupService lookupService,
            RogueChestsFcListRenderer listRenderer)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.worldService = worldService;
        this.lookupService = lookupService;
        this.listRenderer = listRenderer;
    }

    void setHost(Host host)
    {
        this.host = host == null ? Host.NO_OP : host;
    }

    void startUp()
    {
        loadRankedMemberCache();
    }

    void shutDown()
    {
        persistRankedMemberCacheIfDirty();
        clearRosterState();
    }

    void processTick()
    {
        cleanupRankedMemberCacheIfDue();
        persistRankedMemberCacheIfDue();
    }

    boolean isInRequiredFriendsChat()
    {
        FriendsChatManager manager = client.getFriendsChatManager();
        if (manager == null || manager.getName() == null)
        {
            return false;
        }

        return normalizeName(REQUIRED_FRIENDS_CHAT)
                .equals(normalizeName(manager.getName()));
    }

    boolean isStaffAuthorized()
    {
        boolean inRequiredChat = isInRequiredFriendsChat();

        if (!inRequiredChat)
        {
            unauthorizedStaffRankObservedAt = null;
            return cachedStaffRankCheckedAt != null
                    && cachedStaffRankAuthorized;
        }

        Instant now = Instant.now();

        if (cachedStaffRankAuthorized
                && cachedStaffRankCheckedAt != null
                && Duration.between(cachedStaffRankCheckedAt, now)
                .compareTo(STAFF_RANK_CACHE_DURATION) < 0)
        {
            return true;
        }

        FriendsChatManager manager = client.getFriendsChatManager();
        FriendsChatRank rank = manager == null
                ? null
                : manager.getMyRank();

        if (rank == null)
        {
            return cachedStaffRankCheckedAt != null
                    && cachedStaffRankAuthorized;
        }

        boolean authorized = isStaffRank(rank);
        if (authorized)
        {
            cachedStaffRankAuthorized = true;
            cachedStaffRankCheckedAt = now;
            unauthorizedStaffRankObservedAt = null;
            return true;
        }

        if (cachedStaffRankAuthorized)
        {
            if (unauthorizedStaffRankObservedAt == null)
            {
                unauthorizedStaffRankObservedAt = now;
                return true;
            }

            if (Duration.between(unauthorizedStaffRankObservedAt, now)
                    .compareTo(STAFF_RANK_REVOCATION_GRACE) < 0)
            {
                return true;
            }
        }

        cachedStaffRankAuthorized = false;
        cachedStaffRankCheckedAt = now;
        unauthorizedStaffRankObservedAt = null;
        return false;
    }

    Set<String> getCurrentMembers()
    {
        return currentMembers;
    }

    Set<String> getUnrankedF2pMembers()
    {
        return unrankedF2pMembers;
    }

    FriendsChatRank getCurrentMemberRank(String playerName)
    {
        String normalizedName = normalizeName(playerName);
        if (normalizedName.isEmpty())
        {
            return null;
        }

        return currentMemberRanks.get(normalizedName);
    }

    boolean isCurrentMemberRankAtLeastCorporal(String playerName)
    {
        FriendsChatRank rank = getCurrentMemberRank(playerName);
        return rank != null
                && rank.getValue() >= FriendsChatRank.CORPORAL.getValue();
    }

    boolean isKnownRankedMember(String playerName)
    {
        String normalizedName = normalizeName(playerName);
        if (normalizedName.isEmpty())
        {
            return false;
        }

        Instant seenAt = knownRankedMembers.get(normalizedName);
        if (seenAt == null)
        {
            return false;
        }

        if (Duration.between(seenAt, Instant.now())
                .compareTo(RANKED_MEMBER_CACHE_TTL) < 0)
        {
            return true;
        }

        if (knownRankedMembers.remove(normalizedName, seenAt))
        {
            rankedMemberCacheDirty = true;
        }

        return false;
    }

    void onMemberJoined(FriendsChatMember member)
    {
        if (member == null || !isInRequiredFriendsChat())
        {
            return;
        }

        String playerName = member.getName();
        String normalizedName = normalizeName(playerName);
        if (normalizedName.isEmpty())
        {
            return;
        }

        currentMembers.add(normalizedName);
        rememberRankStatus(member);
        updateCurrentRank(member);
        host.removeCapturedNearbyName(playerName);

        if (!host.isStaffFeaturesActive())
        {
            return;
        }

        updateF2pMemberState(member);
        lookupService.onMemberJoined(playerName);
        renderMemberList();
    }

    void onMemberLeft(FriendsChatMember member)
    {
        if (member == null)
        {
            return;
        }

        String normalizedName = normalizeName(member.getName());
        if (normalizedName.isEmpty())
        {
            return;
        }

        currentMembers.remove(normalizedName);
        currentMemberRanks.remove(normalizedName);
        host.removeNearbyMemberTracking(normalizedName);

        if (!host.isStaffFeaturesActive())
        {
            return;
        }

        unrankedF2pMembers.remove(normalizedName);
        host.removeEquipmentScannedVisibleMember(normalizedName);
        lookupService.onMemberLeft(normalizedName);
        renderMemberList();
    }

    void reconcileMembers()
    {
        if (!isInRequiredFriendsChat())
        {
            return;
        }

        FriendsChatManager manager = client.getFriendsChatManager();
        if (manager == null)
        {
            return;
        }

        FriendsChatMember[] members = manager.getMembers();
        if (members == null)
        {
            return;
        }

        Set<String> actualMembers = new HashSet<>(members.length);

        for (FriendsChatMember member : members)
        {
            if (member == null)
            {
                continue;
            }

            rememberRankStatus(member);
            updateCurrentRank(member);

            String normalizedName = normalizeName(member.getName());
            if (!normalizedName.isEmpty())
            {
                actualMembers.add(normalizedName);
            }
        }

        for (String normalizedName : new HashSet<>(currentMembers))
        {
            if (actualMembers.contains(normalizedName))
            {
                continue;
            }

            currentMembers.remove(normalizedName);
            currentMemberRanks.remove(normalizedName);
            unrankedF2pMembers.remove(normalizedName);
            host.removeEquipmentScannedVisibleMember(normalizedName);
            host.removeNearbyMemberTracking(normalizedName);
            lookupService.onMemberLeft(normalizedName);
        }

        currentMembers.addAll(actualMembers);
    }

    void refreshF2pMemberStates()
    {
        if (!host.isStaffFeaturesActive())
        {
            return;
        }

        FriendsChatManager manager = client.getFriendsChatManager();
        if (manager == null)
        {
            if (!unrankedF2pMembers.isEmpty())
            {
                unrankedF2pMembers.clear();
                renderMemberList();
            }
            return;
        }

        FriendsChatMember[] members = manager.getMembers();
        if (members == null)
        {
            return;
        }

        Set<String> refreshedF2pNames = new HashSet<>();

        for (FriendsChatMember member : members)
        {
            if (member == null)
            {
                continue;
            }

            String playerName = member.getName();
            String normalizedName = normalizeName(playerName);

            if (normalizedName.isEmpty()
                    || host.getBannedNames().contains(normalizedName))
            {
                continue;
            }

            if (updateF2pMemberState(member))
            {
                refreshedF2pNames.add(normalizedName);
            }
        }

        boolean changed = unrankedF2pMembers.retainAll(refreshedF2pNames);
        if (changed)
        {
            for (FriendsChatMember member : members)
            {
                if (member != null)
                {
                    lookupService.refreshFromCurrentMember(member.getName());
                }
            }
        }

        renderMemberList();
    }

    void queueCurrentMembersForStaff()
    {
        clientThread.invokeLater(() ->
        {
            if (!host.isStaffFeaturesActive())
            {
                return true;
            }

            FriendsChatManager manager = client.getFriendsChatManager();
            if (manager == null)
            {
                return true;
            }

            FriendsChatMember[] members = manager.getMembers();
            if (members == null || members.length == 0)
            {
                return false;
            }

            Set<String> loadedMembers = new HashSet<>(members.length);

            for (FriendsChatMember member : members)
            {
                if (member == null)
                {
                    continue;
                }

                String playerName = member.getName();
                String normalizedName = normalizeName(playerName);
                if (normalizedName.isEmpty())
                {
                    continue;
                }

                loadedMembers.add(normalizedName);
                currentMembers.add(normalizedName);
                rememberRankStatus(member);
                updateCurrentRank(member);

                /*
                 * Preserve the old initial-load behavior:
                 * banned-member notification may fire, but F2P/low-level join
                 * notifications stay suppressed while the roster is seeded.
                 */
                if (host.getBannedNames().contains(normalizedName))
                {
                    unrankedF2pMembers.remove(normalizedName);
                    lookupService.onMemberJoined(playerName);
                    continue;
                }

                lookupService.onMemberJoined(playerName);
                updateF2pMemberState(member);
                lookupService.refreshFromCurrentMember(playerName);
            }

            host.removeCurrentMembersFromCapturedList(loadedMembers);
            removeMembersMissingFrom(loadedMembers);
            lookupService.setJoinMessagesSuppressed(false);
            renderMemberList();
            return true;
        });
    }

    void queueCurrentMembersForThiever()
    {
        clientThread.invokeLater(() ->
        {
            if (!host.isThieverFeaturesActive())
            {
                return true;
            }

            FriendsChatManager manager = client.getFriendsChatManager();
            if (manager == null)
            {
                return true;
            }

            FriendsChatMember[] members = manager.getMembers();
            if (members == null || members.length == 0)
            {
                return false;
            }

            Set<String> loadedMembers = new HashSet<>(members.length);

            for (FriendsChatMember member : members)
            {
                if (member == null)
                {
                    continue;
                }

                String normalizedName = normalizeName(member.getName());
                if (normalizedName.isEmpty())
                {
                    continue;
                }

                loadedMembers.add(normalizedName);
                currentMembers.add(normalizedName);
                rememberRankStatus(member);
                updateCurrentRank(member);
            }

            host.removeCurrentMembersFromCapturedList(loadedMembers);
            removeMembersMissingFrom(loadedMembers);
            lookupService.setJoinMessagesSuppressed(false);
            return true;
        });
    }

    void clearRosterState()
    {
        currentMembers.clear();
        currentMemberRanks.clear();
        unrankedF2pMembers.clear();
    }

    private void removeMembersMissingFrom(Set<String> loadedMembers)
    {
        for (String normalizedName : new HashSet<>(currentMembers))
        {
            if (loadedMembers.contains(normalizedName))
            {
                continue;
            }

            currentMembers.remove(normalizedName);
            currentMemberRanks.remove(normalizedName);
            unrankedF2pMembers.remove(normalizedName);
            host.removeEquipmentScannedVisibleMember(normalizedName);
            host.removeNearbyMemberTracking(normalizedName);
            lookupService.onMemberLeft(normalizedName);
        }
    }

    private boolean updateF2pMemberState(FriendsChatMember member)
    {
        if (member == null)
        {
            return false;
        }

        String playerName = member.getName();
        String normalizedName = normalizeName(playerName);
        if (normalizedName.isEmpty())
        {
            return false;
        }

        boolean wasF2p = unrankedF2pMembers.contains(normalizedName);
        boolean isF2p = isUnrankedF2p(member);

        if (isF2p)
        {
            unrankedF2pMembers.add(normalizedName);
        }
        else
        {
            unrankedF2pMembers.remove(normalizedName);
        }

        if (wasF2p != isF2p)
        {
            lookupService.refreshFromCurrentMember(playerName);
        }

        return isF2p;
    }

    private boolean isUnrankedF2p(FriendsChatMember member)
    {
        if (member == null || member.getRank() != FriendsChatRank.UNRANKED)
        {
            return false;
        }

        WorldResult worlds = worldService.getWorlds();
        if (worlds == null)
        {
            return false;
        }

        World world = worlds.findWorld(member.getWorld());
        return world != null
                && !world.getTypes().contains(WorldType.MEMBERS);
    }

    private void updateCurrentRank(FriendsChatMember member)
    {
        if (member == null)
        {
            return;
        }

        String normalizedName = normalizeName(member.getName());
        if (normalizedName.isEmpty())
        {
            return;
        }

        FriendsChatRank rank = member.getRank();
        if (rank == null)
        {
            currentMemberRanks.remove(normalizedName);
        }
        else
        {
            currentMemberRanks.put(normalizedName, rank);
        }
    }

    private void rememberRankStatus(FriendsChatMember member)
    {
        if (member == null)
        {
            return;
        }

        String normalizedName = normalizeName(member.getName());
        if (normalizedName.isEmpty())
        {
            return;
        }

        FriendsChatRank rank = member.getRank();
        if (rank != null && rank != FriendsChatRank.UNRANKED)
        {
            Instant now = Instant.now();
            Instant previousSeenAt = knownRankedMembers.get(normalizedName);

            if (previousSeenAt == null
                    || Duration.between(previousSeenAt, now)
                    .compareTo(RANKED_MEMBER_CACHE_REFRESH_INTERVAL) >= 0)
            {
                knownRankedMembers.put(normalizedName, now);
                rankedMemberCacheDirty = true;
            }
        }
        else if (knownRankedMembers.remove(normalizedName) != null)
        {
            rankedMemberCacheDirty = true;
        }
    }

    private boolean isStaffRank(FriendsChatRank rank)
    {
        switch (rank)
        {
            case LIEUTENANT:
            case CAPTAIN:
            case GENERAL:
            case OWNER:
                return true;
            default:
                return false;
        }
    }

    private void renderMemberList()
    {
        listRenderer.applyLevelsToMemberList(
                host.isStaffFeaturesActive(),
                host.getIgnoredNames(),
                host.getBannedNames(),
                unrankedF2pMembers,
                lookupService.getThievingLevels(),
                lookupService::handleBannedMember
        );
    }

    private void loadRankedMemberCache()
    {
        synchronized (rankedMemberCacheFileLock)
        {
            knownRankedMembers.clear();
            rankedMemberCacheDirty = false;
            lastRankedMemberCacheWriteNanos = System.nanoTime();
            lastRankedMemberCacheCleanupNanos = System.nanoTime();

            File cacheDirectory = new File(
                    RuneLite.RUNELITE_DIR,
                    CACHE_DIRECTORY
            );

            rankedMemberCacheFile = new File(
                    cacheDirectory,
                    RANKED_MEMBER_CACHE_FILENAME
            );

            if (!rankedMemberCacheFile.exists())
            {
                return;
            }

            Instant now = Instant.now();

            try
            {
                for (String line : Files.readAllLines(
                        rankedMemberCacheFile.toPath(),
                        StandardCharsets.UTF_8))
                {
                    if (line.trim().isEmpty())
                    {
                        continue;
                    }

                    int separatorIndex = line.lastIndexOf('|');
                    if (separatorIndex <= 0
                            || separatorIndex >= line.length() - 1)
                    {
                        rankedMemberCacheDirty = true;
                        continue;
                    }

                    String normalizedName = normalizeName(
                            line.substring(0, separatorIndex)
                    );

                    if (normalizedName.isEmpty())
                    {
                        rankedMemberCacheDirty = true;
                        continue;
                    }

                    try
                    {
                        Instant seenAt = Instant.ofEpochMilli(
                                Long.parseLong(
                                        line.substring(separatorIndex + 1)
                                )
                        );

                        if (Duration.between(seenAt, now)
                                .compareTo(RANKED_MEMBER_CACHE_TTL) < 0)
                        {
                            knownRankedMembers.put(normalizedName, seenAt);
                        }
                        else
                        {
                            rankedMemberCacheDirty = true;
                        }
                    }
                    catch (RuntimeException exception)
                    {
                        rankedMemberCacheDirty = true;
                    }
                }
            }
            catch (IOException exception)
            {
                log.debug(
                        "Unable to load ranked FC member cache",
                        exception
                );
                knownRankedMembers.clear();
                return;
            }

            persistRankedMemberCacheIfDirtyLocked();
        }
    }

    private void cleanupRankedMemberCacheIfDue()
    {
        long nowNanos = System.nanoTime();

        if (lastRankedMemberCacheCleanupNanos != 0L
                && nowNanos - lastRankedMemberCacheCleanupNanos
                < RANKED_MEMBER_CACHE_CLEANUP_INTERVAL_NANOS)
        {
            return;
        }

        lastRankedMemberCacheCleanupNanos = nowNanos;

        Instant cutoff = Instant.now().minus(RANKED_MEMBER_CACHE_TTL);
        boolean removed = knownRankedMembers.entrySet().removeIf(
                entry -> entry.getValue().isBefore(cutoff)
        );

        if (removed)
        {
            rankedMemberCacheDirty = true;
        }
    }

    private void persistRankedMemberCacheIfDue()
    {
        if (!rankedMemberCacheDirty)
        {
            return;
        }

        long now = System.nanoTime();
        if (lastRankedMemberCacheWriteNanos != 0L
                && now - lastRankedMemberCacheWriteNanos
                < RANKED_MEMBER_CACHE_WRITE_INTERVAL_NANOS)
        {
            return;
        }

        persistRankedMemberCacheIfDirty();
    }

    private void persistRankedMemberCacheIfDirty()
    {
        synchronized (rankedMemberCacheFileLock)
        {
            persistRankedMemberCacheIfDirtyLocked();
        }
    }

    private void persistRankedMemberCacheIfDirtyLocked()
    {
        if (!rankedMemberCacheDirty || rankedMemberCacheFile == null)
        {
            return;
        }

        try
        {
            File cacheDirectory = rankedMemberCacheFile.getParentFile();
            if (cacheDirectory != null)
            {
                Files.createDirectories(cacheDirectory.toPath());
            }

            Map<String, Instant> sortedEntries =
                    new TreeMap<>(knownRankedMembers);
            ArrayList<String> lines =
                    new ArrayList<>(sortedEntries.size());

            for (Map.Entry<String, Instant> entry : sortedEntries.entrySet())
            {
                lines.add(
                        entry.getKey()
                                + "|"
                                + entry.getValue().toEpochMilli()
                );
            }

            Files.write(
                    rankedMemberCacheFile.toPath(),
                    lines,
                    StandardCharsets.UTF_8
            );

            rankedMemberCacheDirty = false;
            lastRankedMemberCacheWriteNanos = System.nanoTime();
        }
        catch (IOException exception)
        {
            log.debug(
                    "Unable to save ranked FC member cache",
                    exception
            );
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

    interface Host
    {
        Host NO_OP = new Host()
        {
            @Override
            public boolean isStaffFeaturesActive()
            {
                return false;
            }

            @Override
            public boolean isThieverFeaturesActive()
            {
                return false;
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

            @Override
            public void removeCapturedNearbyName(String playerName)
            {
            }

            @Override
            public void removeCurrentMembersFromCapturedList(
                    Set<String> normalizedNames)
            {
            }

            @Override
            public void removeNearbyMemberTracking(String normalizedName)
            {
            }

            @Override
            public void removeEquipmentScannedVisibleMember(
                    String normalizedName)
            {
            }
        };

        boolean isStaffFeaturesActive();

        boolean isThieverFeaturesActive();

        Set<String> getIgnoredNames();

        Set<String> getBannedNames();

        void removeCapturedNearbyName(String playerName);

        void removeCurrentMembersFromCapturedList(Set<String> normalizedNames);

        void removeNearbyMemberTracking(String normalizedName);

        void removeEquipmentScannedVisibleMember(String normalizedName);
    }
}