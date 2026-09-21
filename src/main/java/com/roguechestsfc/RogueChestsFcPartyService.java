package com.roguechestsfc;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.party.messages.TilePing;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class RogueChestsFcPartyService
{
    private static final Duration PARTY_FC_CHECK_GRACE =
            Duration.ofSeconds(10);

    private final net.runelite.api.Client client;
    private final PartyService partyService;
    private final RogueChestsFcConfig config;
    private final RogueChestsFcSyncService syncService;
    private final RogueChestsFcFriendsChatService friendsChatService;
    private final RogueChestsFcPartyPingBeamOverlay partyPingBeamOverlay;

    private final Map<Long, Instant> pendingMembershipChecks =
            new ConcurrentHashMap<>();

    private volatile Host host = Host.NO_OP;
    private boolean partyJoinBannerVisible;
    private boolean partyReminderDismissedForLogin;

    @Inject
    public RogueChestsFcPartyService(
            net.runelite.api.Client client,
            PartyService partyService,
            RogueChestsFcConfig config,
            RogueChestsFcSyncService syncService,
            RogueChestsFcFriendsChatService friendsChatService,
            RogueChestsFcPartyPingBeamOverlay partyPingBeamOverlay)
    {
        this.client = client;
        this.partyService = partyService;
        this.config = config;
        this.syncService = syncService;
        this.friendsChatService = friendsChatService;
        this.partyPingBeamOverlay = partyPingBeamOverlay;
    }

    void setHost(Host host)
    {
        this.host = host == null ? Host.NO_OP : host;
    }

    void processTick()
    {
        processPendingMembershipChecks();
    }

    void onUserJoin(long memberId)
    {
        if (!host.isStaffInfrastructureActive())
        {
            return;
        }

        pendingMembershipChecks.putIfAbsent(
                memberId,
                Instant.now()
        );
    }

    void onUserPart(long memberId)
    {
        pendingMembershipChecks.remove(memberId);
    }

    void onTilePing(TilePing event)
    {
        if (!host.isAuthorizedFeaturesActive()
                || !config.showPartyPingBeam()
                || event == null
                || event.getPoint() == null)
        {
            return;
        }

        partyPingBeamOverlay.addPing(event.getPoint());
    }

    void onGameStateChanged(GameState gameState)
    {
        if (gameState != GameState.LOGGED_IN)
        {
            partyJoinBannerVisible = false;
        }

        if (gameState == GameState.LOGIN_SCREEN)
        {
            pendingMembershipChecks.clear();
            partyReminderDismissedForLogin = false;
            partyPingBeamOverlay.clearPings();
            host.refreshPanel();
        }
        else if (gameState == GameState.HOPPING)
        {
            partyPingBeamOverlay.clearPings();
            host.refreshPanel();
        }
    }

    void updateJoinBannerForLogin()
    {
        partyJoinBannerVisible =
                host.isStaffFeaturesActive()
                        && !partyService.isInParty();

        host.refreshPanel();
    }

    boolean shouldShowPartyJoinBanner()
    {
        return host.isStaffFeaturesActive()
                && partyJoinBannerVisible
                && !partyService.isInParty();
    }

    boolean shouldShowPartyReminder()
    {
        return host.isAuthorizedFeaturesActive()
                && config.showPartyPopup()
                && !partyReminderDismissedForLogin
                && !partyService.isInParty();
    }

    void dismissPartyJoinBanner()
    {
        partyJoinBannerVisible = false;
        partyReminderDismissedForLogin = true;
        host.refreshPanel();
    }

    void joinStaffParty()
    {
        if (!host.isStaffFeaturesActive())
        {
            return;
        }

        String passphrase = syncService.getPartyPassphrase();
        if (passphrase == null || passphrase.isEmpty())
        {
            return;
        }

        try
        {
            if (!partyService.isInParty()
                    || !passphrase.equals(
                    partyService.getPartyPassphrase()))
            {
                partyService.changeParty(passphrase);
            }

            partyJoinBannerVisible = false;
            partyReminderDismissedForLogin = true;
            host.refreshPanel();
        }
        catch (RuntimeException exception)
        {
            log.debug(
                    "Unable to join staff Party",
                    exception
            );
        }
    }

    void leaveStaffParty()
    {
        if (!host.isStaffFeaturesActive())
        {
            return;
        }

        try
        {
            if (partyService.isInParty())
            {
                partyService.changeParty(null);
            }

            partyJoinBannerVisible = false;
            partyReminderDismissedForLogin = true;
            host.refreshPanel();
        }
        catch (RuntimeException exception)
        {
            log.debug(
                    "Unable to leave staff Party",
                    exception
            );
        }
    }

    boolean isInParty()
    {
        return host.isStaffFeaturesActive()
                && partyService.isInParty();
    }

    void clearPendingMembershipChecks()
    {
        pendingMembershipChecks.clear();
    }

    void clearStaffOnlyRuntimeForThieverSwitch()
    {
        pendingMembershipChecks.clear();
        partyJoinBannerVisible = false;
    }

    void onModeDeactivationStarted()
    {
        partyJoinBannerVisible = false;
        partyReminderDismissedForLogin = false;
    }

    void clearRuntimeState()
    {
        pendingMembershipChecks.clear();
        partyJoinBannerVisible = false;
        partyReminderDismissedForLogin = false;
        partyPingBeamOverlay.clearPings();
    }

    private void processPendingMembershipChecks()
    {
        if (pendingMembershipChecks.isEmpty()
                || !host.isStaffInfrastructureActive()
                || !partyService.isInParty()
                || !host.isInRequiredFriendsChat())
        {
            return;
        }

        PartyMember localMember = partyService.getLocalMember();
        Instant now = Instant.now();

        for (Map.Entry<Long, Instant> entry
                : new ArrayList<>(pendingMembershipChecks.entrySet()))
        {
            Long memberId = entry.getKey();

            if (Duration.between(entry.getValue(), now)
                    .compareTo(PARTY_FC_CHECK_GRACE) < 0)
            {
                continue;
            }

            PartyMember partyMember =
                    partyService.getMemberById(memberId);

            if (partyMember == null)
            {
                pendingMembershipChecks.remove(memberId);
                continue;
            }

            if (localMember != null
                    && Objects.equals(
                    localMember.getMemberId(),
                    memberId))
            {
                pendingMembershipChecks.remove(memberId);
                continue;
            }

            String displayName = partyMember.getDisplayName();

            if (displayName == null
                    || displayName.trim().isEmpty())
            {
                continue;
            }

            String normalizedName = normalizeName(displayName);
            if (normalizedName.isEmpty())
            {
                pendingMembershipChecks.remove(memberId);
                continue;
            }

            boolean inFriendsChat =
                    friendsChatService
                            .getCurrentMembers()
                            .contains(normalizedName);

            pendingMembershipChecks.remove(memberId);

            if (inFriendsChat
                    || friendsChatService
                    .isKnownRankedMember(normalizedName))
            {
                continue;
            }

            String message =
                    new ChatMessageBuilder()
                            .append(
                                    Color.RED,
                                    Text.toJagexName(displayName)
                                            + " joined the Party but is not in Rogue Chests FC."
                            )
                            .build();

            client.addChatMessage(
                    ChatMessageType.GAMEMESSAGE,
                    "",
                    message,
                    ""
            );
        }
    }

    private static String normalizeName(String playerName)
    {
        if (playerName == null)
        {
            return "";
        }

        return Text.toJagexName(
                Text.removeTags(playerName)
        ).toLowerCase(java.util.Locale.ROOT);
    }

    interface Host
    {
        Host NO_OP = new Host()
        {
            @Override
            public boolean isStaffInfrastructureActive()
            {
                return false;
            }

            @Override
            public boolean isStaffFeaturesActive()
            {
                return false;
            }

            @Override
            public boolean isAuthorizedFeaturesActive()
            {
                return false;
            }

            @Override
            public boolean isInRequiredFriendsChat()
            {
                return false;
            }

            @Override
            public void refreshPanel()
            {
            }
        };

        boolean isStaffInfrastructureActive();

        boolean isStaffFeaturesActive();

        boolean isAuthorizedFeaturesActive();

        boolean isInRequiredFriendsChat();

        void refreshPanel();
    }
}
