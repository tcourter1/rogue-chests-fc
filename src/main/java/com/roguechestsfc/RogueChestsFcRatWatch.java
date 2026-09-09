package com.roguechestsfc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class RogueChestsFcRatWatch
{
    static final Duration EVIDENCE_WINDOW = Duration.ofDays(7);
    static final Duration SHORT_STAY_MAX = Duration.ofSeconds(60);
    static final Duration REJOIN_GRACE = Duration.ofSeconds(90);
    static final Duration JOIN_HIT_WINDOW = Duration.ofSeconds(90);
    static final Duration RECENT_JOIN_WINDOW = Duration.ofMinutes(5);
    static final Duration PRE_HIT_LEAVE_WINDOW = Duration.ofSeconds(30);
    static final Duration POST_HIT_LEAVE_WINDOW = Duration.ofSeconds(20);
    static final Duration SHORT_STAY_VALIDATION_WINDOW = Duration.ofHours(1);
    static final Duration HIT_COOLDOWN = Duration.ofMinutes(30);

    static final int SHORT_STAY_POINTS = 3;
    static final int SHORT_STAY_MAX_POINTS = 25;
    static final int JOIN_HIT_POINTS = 10;
    static final int JOIN_LEAVE_HIT_POINTS = 20;
    static final int PRE_HIT_DEPARTURE_POINTS = 20;

    private final Map<String, PlayerHistory> histories = new HashMap<>();
    private final Map<String, ActiveSession> activeSessions = new HashMap<>();
    private final Map<String, PendingDeparture> pendingDepartures = new HashMap<>();
    private final Map<String, Instant> dismissedUntil = new HashMap<>();
    private final Map<String, Set<Instant>> validatedShortStays = new HashMap<>();

    private HitEvent lastHit;

    synchronized void onJoin(String playerName, Instant now)
    {
        String normalizedName = normalizeName(playerName);
        if (normalizedName.isEmpty() || now == null)
        {
            return;
        }

        purgeExpired(now);

        PendingDeparture pending = pendingDepartures.remove(normalizedName);
        ActiveSession session = activeSessions.get(normalizedName);

        if (pending != null
                && session != null
                && Duration.between(pending.leftAt, now).compareTo(REJOIN_GRACE) <= 0)
        {
            return;
        }

        if (pending != null)
        {
            finalizePendingDeparture(normalizedName, pending);
        }

        activeSessions.put(
                normalizedName,
                new ActiveSession(displayName(playerName), now)
        );
    }

    synchronized void onLeave(
            String playerName,
            Instant now,
            boolean wasVisibleAtRogueCastle,
            boolean likelyThievingWorld)
    {
        String normalizedName = normalizeName(playerName);
        if (normalizedName.isEmpty() || now == null)
        {
            return;
        }

        purgeExpired(now);

        ActiveSession session = activeSessions.get(normalizedName);
        if (session == null)
        {
            return;
        }

        PendingDeparture departure = new PendingDeparture(
                session.displayName,
                session.startedAt,
                now,
                wasVisibleAtRogueCastle,
                likelyThievingWorld
        );

        pendingDepartures.put(normalizedName, departure);

        Duration membershipAge = Duration.between(session.startedAt, now);

        if (lastHit != null
                && !membershipAge.isNegative()
                && membershipAge.compareTo(RECENT_JOIN_WINDOW) <= 0
                && !now.isBefore(lastHit.occurredAt)
                && Duration.between(lastHit.occurredAt, now).compareTo(POST_HIT_LEAVE_WINDOW) <= 0
                && lastHit.visibleFcMembers.contains(normalizedName)
                && !hasStrongHitEvidence(normalizedName, lastHit.occurredAt))
        {
            addEvidence(
                    normalizedName,
                    departure.displayName,
                    EvidenceType.PRE_HIT_FC_DEPARTURE,
                    now,
                    PRE_HIT_DEPARTURE_POINTS,
                    "Left FC "
                            + Duration.between(lastHit.occurredAt, now).getSeconds()
                            + "s after a hit while visible at Rogue Castle."
            );
        }
    }

    synchronized void onTick(Instant now)
    {
        if (now == null)
        {
            return;
        }

        List<String> ready = new ArrayList<>();

        for (Map.Entry<String, PendingDeparture> entry : pendingDepartures.entrySet())
        {
            if (Duration.between(entry.getValue().leftAt, now).compareTo(REJOIN_GRACE) > 0)
            {
                ready.add(entry.getKey());
            }
        }

        for (String normalizedName : ready)
        {
            PendingDeparture departure = pendingDepartures.remove(normalizedName);
            if (departure != null)
            {
                finalizePendingDeparture(normalizedName, departure);
            }
        }

        purgeExpired(now);
    }

    synchronized boolean canRecordHit(Instant now)
    {
        if (now == null)
        {
            return false;
        }

        return lastHit == null
                || Duration.between(lastHit.occurredAt, now).compareTo(HIT_COOLDOWN) >= 0;
    }

    synchronized boolean onHit(
            Instant now,
            Set<String> visibleFcMembers)
    {
        if (now == null || !canRecordHit(now))
        {
            return false;
        }

        purgeExpired(now);

        Set<String> normalizedVisible = new HashSet<>();
        if (visibleFcMembers != null)
        {
            for (String name : visibleFcMembers)
            {
                String normalizedName = normalizeName(name);
                if (!normalizedName.isEmpty())
                {
                    normalizedVisible.add(normalizedName);
                }
            }
        }

        HitEvent hit = new HitEvent(now, normalizedVisible);
        lastHit = hit;

        validateRecentShortStays(hit.occurredAt);
        applyJoinHitEvidence(hit);
        applyPreHitDepartureEvidence(hit);

        return true;
    }

    synchronized List<RatWatchEntry> getEntries(Instant now)
    {
        if (now == null)
        {
            now = Instant.now();
        }

        purgeExpired(now);

        List<RatWatchEntry> entries = new ArrayList<>();

        for (Map.Entry<String, PlayerHistory> entry : histories.entrySet())
        {
            if (isDismissed(entry.getKey(), now))
            {
                continue;
            }

            int score = calculateScore(entry.getValue());
            if (score > 0)
            {
                entries.add(new RatWatchEntry(
                        entry.getValue().displayName,
                        score
                ));
            }
        }

        entries.sort(
                Comparator.comparingInt(RatWatchEntry::getScore)
                        .reversed()
                        .thenComparing(
                                RatWatchEntry::getName,
                                String.CASE_INSENSITIVE_ORDER
                        )
        );

        return entries;
    }

    synchronized List<EvidenceRecord> getEvidence(
            String playerName,
            Instant now)
    {
        String normalizedName = normalizeName(playerName);
        if (normalizedName.isEmpty())
        {
            return Collections.emptyList();
        }

        if (now == null)
        {
            now = Instant.now();
        }

        purgeExpired(now);

        PlayerHistory history = histories.get(normalizedName);
        if (history == null)
        {
            return Collections.emptyList();
        }

        return new ArrayList<>(history.evidence);
    }

    synchronized void dismiss(String playerName, Instant now)
    {
        String normalizedName = normalizeName(playerName);
        if (normalizedName.isEmpty())
        {
            return;
        }

        if (now == null)
        {
            now = Instant.now();
        }

        histories.remove(normalizedName);
        activeSessions.remove(normalizedName);
        pendingDepartures.remove(normalizedName);
        validatedShortStays.remove(normalizedName);
        dismissedUntil.put(normalizedName, now.plus(EVIDENCE_WINDOW));
    }

    synchronized boolean isDismissed(String playerName, Instant now)
    {
        return isDismissedNormalized(normalizeName(playerName), now);
    }

    private boolean isDismissedNormalized(String normalizedName, Instant now)
    {
        if (normalizedName.isEmpty())
        {
            return false;
        }

        Instant until = dismissedUntil.get(normalizedName);
        if (until == null)
        {
            return false;
        }

        if (now == null)
        {
            now = Instant.now();
        }

        if (!now.isBefore(until))
        {
            dismissedUntil.remove(normalizedName);
            return false;
        }

        return true;
    }

    private void finalizePendingDeparture(
            String normalizedName,
            PendingDeparture departure)
    {
        pendingDepartures.remove(normalizedName);
        ActiveSession session = activeSessions.remove(normalizedName);

        Instant startedAt = session != null
                ? session.startedAt
                : departure.joinedAt;

        Duration stayDuration = Duration.between(startedAt, departure.leftAt);

        if (!stayDuration.isNegative()
                && stayDuration.compareTo(SHORT_STAY_MAX) <= 0)
        {
            addEvidence(
                    normalizedName,
                    departure.displayName,
                    EvidenceType.SHORT_STAY,
                    departure.leftAt,
                    SHORT_STAY_POINTS,
                    "Left FC after "
                            + stayDuration.getSeconds()
                            + "s and did not rejoin within "
                            + REJOIN_GRACE.getSeconds()
                            + "s."
            );
        }
    }

    private void applyJoinHitEvidence(HitEvent hit)
    {
        for (Map.Entry<String, ActiveSession> entry : activeSessions.entrySet())
        {
            String normalizedName = entry.getKey();
            ActiveSession session = entry.getValue();

            Duration sinceJoin = Duration.between(session.startedAt, hit.occurredAt);

            if (sinceJoin.isNegative()
                    || sinceJoin.compareTo(JOIN_HIT_WINDOW) > 0)
            {
                continue;
            }

            PendingDeparture departure = pendingDepartures.get(normalizedName);

            if (departure != null
                    && !departure.leftAt.isAfter(hit.occurredAt)
                    && Duration.between(
                    departure.leftAt,
                    hit.occurredAt
            ).compareTo(PRE_HIT_LEAVE_WINDOW) <= 0)
            {
                addEvidence(
                        normalizedName,
                        session.displayName,
                        EvidenceType.JOIN_LEAVE_HIT,
                        hit.occurredAt,
                        JOIN_LEAVE_HIT_POINTS,
                        "Joined FC "
                                + sinceJoin.getSeconds()
                                + "s before hit and left before the hit."
                );
            }
            else
            {
                addEvidence(
                        normalizedName,
                        session.displayName,
                        EvidenceType.JOIN_HIT,
                        hit.occurredAt,
                        JOIN_HIT_POINTS,
                        "Joined FC "
                                + sinceJoin.getSeconds()
                                + "s before hit."
                );
            }
        }
    }

    private void applyPreHitDepartureEvidence(HitEvent hit)
    {
        for (Map.Entry<String, PendingDeparture> entry : pendingDepartures.entrySet())
        {
            String normalizedName = entry.getKey();
            PendingDeparture departure = entry.getValue();

            if (!departure.wasVisibleAtRogueCastle
                    || !departure.likelyThievingWorld)
            {
                continue;
            }

            Duration sinceLeave = Duration.between(
                    departure.leftAt,
                    hit.occurredAt
            );

            Duration membershipAge = Duration.between(
                    departure.joinedAt,
                    hit.occurredAt
            );

            if (membershipAge.isNegative()
                    || membershipAge.compareTo(RECENT_JOIN_WINDOW) > 0
                    || sinceLeave.isNegative()
                    || sinceLeave.compareTo(PRE_HIT_LEAVE_WINDOW) > 0
                    || hasStrongHitEvidence(
                    normalizedName,
                    hit.occurredAt
            ))
            {
                continue;
            }

            addEvidence(
                    normalizedName,
                    departure.displayName,
                    EvidenceType.PRE_HIT_FC_DEPARTURE,
                    hit.occurredAt,
                    PRE_HIT_DEPARTURE_POINTS,
                    "Left FC "
                            + sinceLeave.getSeconds()
                            + "s before a hit after being visible at Rogue Castle."
            );
        }
    }

    private void validateRecentShortStays(Instant hitAt)
    {
        Instant cutoff = hitAt.minus(SHORT_STAY_VALIDATION_WINDOW);

        for (Map.Entry<String, PlayerHistory> entry : histories.entrySet())
        {
            for (EvidenceRecord record : entry.getValue().evidence)
            {
                if (record.type == EvidenceType.SHORT_STAY
                        && !record.occurredAt.isBefore(cutoff)
                        && !record.occurredAt.isAfter(hitAt))
                {
                    validatedShortStays
                            .computeIfAbsent(
                                    entry.getKey(),
                                    ignored -> new HashSet<>()
                            )
                            .add(record.occurredAt);
                }
            }
        }
    }

    private boolean hasStrongHitEvidence(
            String normalizedName,
            Instant hitAt)
    {
        PlayerHistory history = histories.get(normalizedName);
        if (history == null)
        {
            return false;
        }

        for (EvidenceRecord record : history.evidence)
        {
            if (record.occurredAt.equals(hitAt)
                    && (record.type == EvidenceType.JOIN_LEAVE_HIT
                    || record.type == EvidenceType.PRE_HIT_FC_DEPARTURE))
            {
                return true;
            }
        }

        return false;
    }

    private void addEvidence(
            String normalizedName,
            String displayName,
            EvidenceType type,
            Instant occurredAt,
            int points,
            String details)
    {
        if (normalizedName.isEmpty()
                || occurredAt == null
                || points <= 0
                || isDismissedNormalized(normalizedName, occurredAt))
        {
            return;
        }

        PlayerHistory history = histories.computeIfAbsent(
                normalizedName,
                ignored -> new PlayerHistory(displayName)
        );

        history.displayName = displayName;
        history.evidence.add(new EvidenceRecord(
                type,
                occurredAt,
                points,
                details
        ));
    }

    private int calculateScore(PlayerHistory history)
    {
        int shortStayScore = 0;
        int otherScore = 0;

        for (EvidenceRecord record : history.evidence)
        {
            if (record.type == EvidenceType.SHORT_STAY)
            {
                shortStayScore += record.points;
            }
            else
            {
                otherScore += record.points;
            }
        }

        shortStayScore = Math.min(
                SHORT_STAY_MAX_POINTS,
                shortStayScore
        );

        return Math.min(100, shortStayScore + otherScore);
    }

    private void purgeExpired(Instant now)
    {
        Instant cutoff = now.minus(EVIDENCE_WINDOW);

        histories.entrySet().removeIf(entry ->
        {
            Set<Instant> validated =
                    validatedShortStays.getOrDefault(
                            entry.getKey(),
                            Collections.emptySet()
                    );

            entry.getValue().evidence.removeIf(
                    evidence ->
                            evidence.occurredAt.isBefore(cutoff)
                                    || (evidence.type == EvidenceType.SHORT_STAY
                                    && !validated.contains(evidence.occurredAt)
                                    && Duration.between(
                                    evidence.occurredAt,
                                    now
                            ).compareTo(
                                    SHORT_STAY_VALIDATION_WINDOW
                            ) >= 0)
            );

            return entry.getValue().evidence.isEmpty();
        });

        validatedShortStays.entrySet().removeIf(entry ->
        {
            entry.getValue().removeIf(
                    occurredAt -> occurredAt.isBefore(cutoff)
            );
            return entry.getValue().isEmpty();
        });

        dismissedUntil.entrySet().removeIf(
                entry -> !now.isBefore(entry.getValue())
        );
    }

    private static String normalizeName(String playerName)
    {
        if (playerName == null)
        {
            return "";
        }

        return playerName
                .trim()
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private static String displayName(String playerName)
    {
        if (playerName == null)
        {
            return "";
        }

        return playerName
                .trim()
                .replace('_', ' ')
                .replaceAll("\\s+", " ");
    }

    enum EvidenceType
    {
        SHORT_STAY,
        JOIN_HIT,
        JOIN_LEAVE_HIT,
        PRE_HIT_FC_DEPARTURE
    }

    static final class EvidenceRecord
    {
        private final EvidenceType type;
        private final Instant occurredAt;
        private final int points;
        private final String details;

        EvidenceRecord(
                EvidenceType type,
                Instant occurredAt,
                int points,
                String details)
        {
            this.type = type;
            this.occurredAt = occurredAt;
            this.points = points;
            this.details = details;
        }

        EvidenceType getType()
        {
            return type;
        }

        Instant getOccurredAt()
        {
            return occurredAt;
        }

        int getPoints()
        {
            return points;
        }

        String getDetails()
        {
            return details;
        }
    }

    static final class RatWatchEntry
    {
        private final String name;
        private final int score;

        RatWatchEntry(String name, int score)
        {
            this.name = name;
            this.score = score;
        }

        String getName()
        {
            return name;
        }

        int getScore()
        {
            return score;
        }
    }

    private static final class PlayerHistory
    {
        private String displayName;
        private final List<EvidenceRecord> evidence = new ArrayList<>();

        private PlayerHistory(String displayName)
        {
            this.displayName = displayName;
        }
    }

    private static final class ActiveSession
    {
        private final String displayName;
        private final Instant startedAt;

        private ActiveSession(String displayName, Instant startedAt)
        {
            this.displayName = displayName;
            this.startedAt = startedAt;
        }
    }

    private static final class PendingDeparture
    {
        private final String displayName;
        private final Instant joinedAt;
        private final Instant leftAt;
        private final boolean wasVisibleAtRogueCastle;
        private final boolean likelyThievingWorld;

        private PendingDeparture(
                String displayName,
                Instant joinedAt,
                Instant leftAt,
                boolean wasVisibleAtRogueCastle,
                boolean likelyThievingWorld)
        {
            this.displayName = displayName;
            this.joinedAt = joinedAt;
            this.leftAt = leftAt;
            this.wasVisibleAtRogueCastle = wasVisibleAtRogueCastle;
            this.likelyThievingWorld = likelyThievingWorld;
        }
    }

    private static final class HitEvent
    {
        private final Instant occurredAt;
        private final Set<String> visibleFcMembers;

        private HitEvent(
                Instant occurredAt,
                Set<String> visibleFcMembers)
        {
            this.occurredAt = occurredAt;
            this.visibleFcMembers = new HashSet<>(visibleFcMembers);
        }
    }
}