package com.roguechestsfc;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.OverlayManager;

@Singleton
public class RogueChestsFcModeController
{
    private static final String CONFIG_GROUP = "roguechestsfc";
    private static final String PLUGIN_MODE_KEY = "pluginMode";

    private final Client client;
    private final ClientThread clientThread;
    private final ConfigManager configManager;
    private final RogueChestsFcConfig config;
    private final OverlayManager overlayManager;
    private final RogueChestsFcOverlay staffOverlay;
    private final RogueChestsFcOvertimeOverlay overtimeOverlay;
    private final RogueChestsFcPartyOverlay partyOverlay;
    private final RogueChestsFcPartyPingBeamOverlay partyPingBeamOverlay;
    private final RogueChestsFcEnemyOverlay enemyOverlay;
    private final RogueChestsFcThieverMode thieverMode;
    private final RogueChestsFcPanel panel;
    private final RogueChestsFcSyncService syncService;
    private final RogueChestsFcListRenderer listRenderer;

    private Host host = Host.NO_OP;

    private TransitionPhase transitionPhase = TransitionPhase.NONE;
    private RogueChestsFcConfig.PluginMode pendingMode =
            RogueChestsFcConfig.PluginMode.NONE;

    private boolean authorizedFeaturesActive;
    private boolean staffFeaturesActive;
    private boolean modeSwitchInProgress;

    @Inject
    public RogueChestsFcModeController(
            Client client,
            ClientThread clientThread,
            ConfigManager configManager,
            RogueChestsFcConfig config,
            OverlayManager overlayManager,
            RogueChestsFcOverlay staffOverlay,
            RogueChestsFcOvertimeOverlay overtimeOverlay,
            RogueChestsFcPartyOverlay partyOverlay,
            RogueChestsFcPartyPingBeamOverlay partyPingBeamOverlay,
            RogueChestsFcEnemyOverlay enemyOverlay,
            RogueChestsFcThieverMode thieverMode,
            RogueChestsFcPanel panel,
            RogueChestsFcSyncService syncService,
            RogueChestsFcListRenderer listRenderer)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.configManager = configManager;
        this.config = config;
        this.overlayManager = overlayManager;
        this.staffOverlay = staffOverlay;
        this.overtimeOverlay = overtimeOverlay;
        this.partyOverlay = partyOverlay;
        this.partyPingBeamOverlay = partyPingBeamOverlay;
        this.enemyOverlay = enemyOverlay;
        this.thieverMode = thieverMode;
        this.panel = panel;
        this.syncService = syncService;
        this.listRenderer = listRenderer;
    }

    void setHost(Host host)
    {
        this.host = host == null ? Host.NO_OP : host;
    }

    RogueChestsFcConfig.PluginMode getPluginMode()
    {
        RogueChestsFcConfig.PluginMode mode = config.pluginMode();
        return mode == null
                ? RogueChestsFcConfig.PluginMode.NONE
                : mode;
    }

    boolean isStaffMode()
    {
        return getPluginMode() == RogueChestsFcConfig.PluginMode.STAFF;
    }

    boolean isThieverMode()
    {
        return getPluginMode() == RogueChestsFcConfig.PluginMode.THIEVER;
    }

    boolean isAuthorizedFeaturesActive()
    {
        return authorizedFeaturesActive;
    }

    boolean isStaffFeaturesActive()
    {
        return authorizedFeaturesActive && staffFeaturesActive;
    }

    boolean isThieverFeaturesActive()
    {
        return authorizedFeaturesActive
                && !staffFeaturesActive
                && isThieverMode();
    }

    boolean isNotTransitioningTo(RogueChestsFcConfig.PluginMode targetMode)
    {
        return transitionPhase == TransitionPhase.NONE
                || pendingMode != targetMode;
    }

    void initializePanelState()
    {
        RogueChestsFcConfig.PluginMode mode = getPluginMode();
        panel.setModeState(mode, false);
    }

    void activateStaff()
    {
        beginTransition(RogueChestsFcConfig.PluginMode.STAFF);
        processTransitionUntilBlocked();
    }

    void activateThiever()
    {
        beginTransition(RogueChestsFcConfig.PluginMode.THIEVER);
        processTransitionUntilBlocked();
    }

    void deactivate()
    {
        beginTransition(RogueChestsFcConfig.PluginMode.NONE);
        processTransitionUntilBlocked();
    }

    void setPluginMode(RogueChestsFcConfig.PluginMode mode)
    {
        if (modeSwitchInProgress)
        {
            return;
        }

        RogueChestsFcConfig.PluginMode selected = mode == null
                ? RogueChestsFcConfig.PluginMode.NONE
                : mode;

        if (getPluginMode() == selected)
        {
            panel.setModeState(
                    selected,
                    selected == RogueChestsFcConfig.PluginMode.STAFF
                            && host.isStaffAuthorized()
            );
            return;
        }

        modeSwitchInProgress = true;
        configManager.setConfiguration(CONFIG_GROUP, PLUGIN_MODE_KEY, selected);

        clientThread.invokeLater(() ->
        {
            try
            {
                applyPluginMode(selected);
                processTransitionUntilBlocked();
            }
            finally
            {
                modeSwitchInProgress = false;
            }

            return true;
        });
    }

    void autoEnableThieverModeIfNeeded()
    {
        boolean enteredRoguesCastle = thieverMode.consumeAutoEnableTrigger();

        if (client.getGameState() != GameState.LOGGED_IN
                || getPluginMode() != RogueChestsFcConfig.PluginMode.NONE
                || !host.isInRequiredFriendsChat()
                || !enteredRoguesCastle)
        {
            return;
        }

        setPluginMode(RogueChestsFcConfig.PluginMode.THIEVER);
    }

    private void processTransitionUntilBlocked()
    {
        int remainingSteps = TransitionPhase.values().length + 2;

        while (transitionPhase != TransitionPhase.NONE
                && remainingSteps-- > 0)
        {
            TransitionPhase before = transitionPhase;
            processTransition();

            if (transitionPhase == before)
            {
                break;
            }
        }
    }

    void processTransition()
    {
        if (transitionPhase == TransitionPhase.NONE)
        {
            return;
        }

        switch (transitionPhase)
        {
            case SWITCH_ACTIVE_MODE:
                processActiveModeSwitch();
                return;

            case DEACTIVATE_OVERLAYS:
                removeAllOverlays();
                partyPingBeamOverlay.clearPings();
                syncService.stop();
                host.onSharedOverlaysDeactivated();
                transitionPhase = TransitionPhase.DEACTIVATE_RUNTIME;
                return;

            case DEACTIVATE_RUNTIME:
                host.clearRuntimeState();
                transitionPhase = TransitionPhase.DEACTIVATE_WIDGETS;
                return;

            case DEACTIVATE_WIDGETS:
                listRenderer.removeLevelsFromMemberList();
                host.refreshPanel();
                transitionPhase = pendingMode == RogueChestsFcConfig.PluginMode.NONE
                        ? TransitionPhase.NONE
                        : TransitionPhase.PREPARE_ACTIVATION;
                return;

            case PREPARE_ACTIVATION:
                if (!canActivatePendingMode())
                {
                    return;
                }

                if (authorizedFeaturesActive)
                {
                    authorizedFeaturesActive = false;
                    staffFeaturesActive = false;
                    transitionPhase = TransitionPhase.DEACTIVATE_OVERLAYS;
                    return;
                }

                transitionPhase = TransitionPhase.ACTIVATE_OVERLAYS;
                return;

            case ACTIVATE_OVERLAYS:
                authorizedFeaturesActive = true;
                staffFeaturesActive = pendingMode == RogueChestsFcConfig.PluginMode.STAFF;

                if (staffFeaturesActive)
                {
                    overlayManager.add(staffOverlay);
                }
                overlayManager.add(overtimeOverlay);
                overlayManager.add(partyOverlay);
                overlayManager.add(partyPingBeamOverlay);
                overlayManager.add(enemyOverlay);

                host.setJoinMessagesSuppressed(true);
                transitionPhase = TransitionPhase.ACTIVATE_BACKGROUND;
                return;

            case ACTIVATE_BACKGROUND:
                if (!authorizedFeaturesActive)
                {
                    transitionPhase = TransitionPhase.NONE;
                    return;
                }

                if (staffFeaturesActive)
                {
                    syncService.start();
                }

                transitionPhase = TransitionPhase.ACTIVATE_DATA;
                return;

            case ACTIVATE_DATA:
                if (!authorizedFeaturesActive)
                {
                    transitionPhase = TransitionPhase.NONE;
                    return;
                }

                if (staffFeaturesActive)
                {
                    host.queueCurrentMembersForStaff();
                }
                else
                {
                    host.queueCurrentMembersForThiever();
                }

                transitionPhase = TransitionPhase.ACTIVATE_UI;
                return;

            case ACTIVATE_UI:
                if (!authorizedFeaturesActive)
                {
                    transitionPhase = TransitionPhase.NONE;
                    return;
                }

                if (client.getGameState() == GameState.LOGGED_IN)
                {
                    host.updatePartyJoinBannerForLogin();
                }

                panel.setModeState(pendingMode, staffFeaturesActive);
                host.refreshPanel();
                transitionPhase = TransitionPhase.NONE;
                return;

            default:
                transitionPhase = TransitionPhase.NONE;
        }
    }

    void shutDown()
    {
        transitionPhase = TransitionPhase.NONE;
        pendingMode = RogueChestsFcConfig.PluginMode.NONE;
        authorizedFeaturesActive = false;
        staffFeaturesActive = false;
        modeSwitchInProgress = false;
        syncService.stop();
        removeAllOverlays();
        partyPingBeamOverlay.clearPings();
    }

    private void applyPluginMode(RogueChestsFcConfig.PluginMode selected)
    {
        boolean authorized = selected == RogueChestsFcConfig.PluginMode.STAFF
                && host.isStaffAuthorized();

        panel.setModeState(selected, authorized);

        if (selected == RogueChestsFcConfig.PluginMode.STAFF)
        {
            beginTransition(authorized
                    ? RogueChestsFcConfig.PluginMode.STAFF
                    : RogueChestsFcConfig.PluginMode.NONE);
        }
        else if (selected == RogueChestsFcConfig.PluginMode.THIEVER)
        {
            beginTransition(host.isInRequiredFriendsChat()
                    ? RogueChestsFcConfig.PluginMode.THIEVER
                    : RogueChestsFcConfig.PluginMode.NONE);
        }
        else
        {
            beginTransition(RogueChestsFcConfig.PluginMode.NONE);
        }
    }

    private void beginTransition(RogueChestsFcConfig.PluginMode targetMode)
    {
        RogueChestsFcConfig.PluginMode normalizedTarget = targetMode == null
                ? RogueChestsFcConfig.PluginMode.NONE
                : targetMode;

        if (normalizedTarget == pendingMode
                && transitionPhase != TransitionPhase.NONE)
        {
            return;
        }

        pendingMode = normalizedTarget;

        if (normalizedTarget != RogueChestsFcConfig.PluginMode.NONE
                && authorizedFeaturesActive
                && isDirectModeSwitch(normalizedTarget))
        {
            transitionPhase = TransitionPhase.SWITCH_ACTIVE_MODE;
            return;
        }

        if (normalizedTarget == RogueChestsFcConfig.PluginMode.NONE)
        {
            authorizedFeaturesActive = false;
            staffFeaturesActive = false;
            host.onModeDeactivationStarted();
            transitionPhase = TransitionPhase.DEACTIVATE_OVERLAYS;
        }
        else
        {
            transitionPhase = TransitionPhase.PREPARE_ACTIVATION;
        }
    }

    private boolean isDirectModeSwitch(RogueChestsFcConfig.PluginMode targetMode)
    {
        return (targetMode == RogueChestsFcConfig.PluginMode.STAFF && !staffFeaturesActive)
                || (targetMode == RogueChestsFcConfig.PluginMode.THIEVER && staffFeaturesActive);
    }

    private void processActiveModeSwitch()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        if (pendingMode == RogueChestsFcConfig.PluginMode.STAFF)
        {
            if (!host.isInRequiredFriendsChat() || !host.isStaffAuthorized())
            {
                return;
            }

            thieverMode.reset();
            staffFeaturesActive = true;
            authorizedFeaturesActive = true;
            overlayManager.add(staffOverlay);
            syncService.start();

            host.setJoinMessagesSuppressed(true);
            host.queueCurrentMembersForStaff();
        }
        else if (pendingMode == RogueChestsFcConfig.PluginMode.THIEVER)
        {
            if (!host.isInRequiredFriendsChat())
            {
                return;
            }

            syncService.stop();
            host.clearStaffOnlyRuntimeForThieverSwitch();
            overlayManager.remove(staffOverlay);
            staffFeaturesActive = false;
            authorizedFeaturesActive = true;
            thieverMode.reset();
            listRenderer.removeLevelsFromMemberList();
            host.setJoinMessagesSuppressed(false);
        }
        else
        {
            transitionPhase = TransitionPhase.NONE;
            return;
        }

        panel.setModeState(pendingMode, staffFeaturesActive);
        host.refreshPanel();
        transitionPhase = TransitionPhase.NONE;
    }

    private boolean canActivatePendingMode()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return false;
        }

        if (pendingMode == RogueChestsFcConfig.PluginMode.STAFF)
        {
            return host.isInRequiredFriendsChat() && host.isStaffAuthorized();
        }

        if (pendingMode == RogueChestsFcConfig.PluginMode.THIEVER)
        {
            return host.isInRequiredFriendsChat();
        }

        transitionPhase = TransitionPhase.NONE;
        return false;
    }

    private void removeAllOverlays()
    {
        overlayManager.remove(staffOverlay);
        overlayManager.remove(overtimeOverlay);
        overlayManager.remove(partyOverlay);
        overlayManager.remove(partyPingBeamOverlay);
        overlayManager.remove(enemyOverlay);
    }

    private enum TransitionPhase
    {
        NONE,
        SWITCH_ACTIVE_MODE,
        DEACTIVATE_OVERLAYS,
        DEACTIVATE_RUNTIME,
        DEACTIVATE_WIDGETS,
        PREPARE_ACTIVATION,
        ACTIVATE_OVERLAYS,
        ACTIVATE_BACKGROUND,
        ACTIVATE_DATA,
        ACTIVATE_UI
    }

    interface Host
    {
        Host NO_OP = new Host()
        {
            @Override
            public boolean isInRequiredFriendsChat()
            {
                return false;
            }

            @Override
            public boolean isStaffAuthorized()
            {
                return false;
            }

            @Override
            public void clearRuntimeState()
            {
            }

            @Override
            public void clearStaffOnlyRuntimeForThieverSwitch()
            {
            }

            @Override
            public void queueCurrentMembersForStaff()
            {
            }

            @Override
            public void queueCurrentMembersForThiever()
            {
            }

            @Override
            public void setJoinMessagesSuppressed(boolean suppressed)
            {
            }

            @Override
            public void onModeDeactivationStarted()
            {
            }

            @Override
            public void onSharedOverlaysDeactivated()
            {
            }

            @Override
            public void updatePartyJoinBannerForLogin()
            {
            }

            @Override
            public void refreshPanel()
            {
            }
        };

        boolean isInRequiredFriendsChat();

        boolean isStaffAuthorized();

        void clearRuntimeState();

        void clearStaffOnlyRuntimeForThieverSwitch();

        void queueCurrentMembersForStaff();

        void queueCurrentMembersForThiever();

        void setJoinMessagesSuppressed(boolean suppressed);

        void onModeDeactivationStarted();

        void onSharedOverlaysDeactivated();

        void updatePartyJoinBannerForLogin();

        void refreshPanel();
    }
}