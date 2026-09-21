package com.roguechestsfc;

import com.google.inject.Provides;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.FriendsChatMemberJoined;
import net.runelite.api.events.FriendsChatMemberLeft;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.party.messages.TilePing;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@SuppressWarnings("unused")
@PluginDescriptor(
		name = "Rogue Chests FC",
		description = "Contains utilities to help manage the Rogue Chests friends chat.",
		tags = {"rogue", "chests", "fc", "friends chat", "thieving"}
)
public class RogueChestsFcPlugin extends Plugin
		implements RogueChestsFcModeController.Host,
		RogueChestsFcLookupService.Context,
		RogueChestsFcFriendsChatService.Host,
		RogueChestsFcNearbyPlayerService.Host,
		RogueChestsFcPartyService.Host
{
	private static final String CONFIG_GROUP = "roguechestsfc";

	private static final int ROGUES_CASTLE_CHEST_ID = 26757;
	private static final String SEARCH_FOR_TRAPS_OPTION = "Search for traps";
	private static final String WALK_HERE_OPTION = "Walk here";
	private static final String ROGUE_NPC_NAME = "Rogue";
	private static final String EDWARD_NPC_NAME = "Edward";

	private static final Duration ACCESS_SANITY_CHECK_INTERVAL =
			Duration.ofSeconds(10);

	private static final long MEMBER_LIST_REFRESH_INTERVAL_NANOS =
			Duration.ofSeconds(1).toNanos();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private RogueChestsFcConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RogueChestsFcPanel panel;

	@Inject
	private RogueChestsFcThieverMode thieverMode;

	@Inject
	private RogueChestsFcModeController modeController;

	@Inject
	private RogueChestsFcSyncService syncService;

	@Inject
	private RogueChestsFcListRenderer listRenderer;

	@Inject
	private RogueChestsFcLookupService lookupService;

	@Inject
	private RogueChestsFcFriendsChatService friendsChatService;

	@Inject
	private RogueChestsFcNearbyPlayerService nearbyPlayerService;

	@Inject
	private RogueChestsFcPartyService roguePartyService;

	private NavigationButton navigationButton;
	private Instant lastAccessSanityCheck;
	private long lastMemberListRefreshNanos;

	private volatile String cachedIgnoredNamesSource;
	private volatile Set<String> cachedIgnoredNames =
			Collections.emptySet();

	private volatile String cachedBannedNamesSource;
	private volatile Set<String> cachedBannedNames =
			Collections.emptySet();

	@Provides
	RogueChestsFcConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RogueChestsFcConfig.class);
	}

	@Override
	protected void startUp()
	{
		wireServices();

		lookupService.startUp();
		friendsChatService.startUp();
		nearbyPlayerService.startUp();

		BufferedImage icon = ImageUtil.loadImageResource(
				getClass(),
				"Chest.png"
		);

		navigationButton = NavigationButton.builder()
				.tooltip("Rogue Chests FC")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navigationButton);
		overlayManager.add(thieverMode);
		modeController.initializePanelState();

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			reconcileFriendsChatAccess();
		}
		else
		{
			listRenderer.removeLevelsFromMemberList();
		}
	}

	@Override
	protected void shutDown()
	{
		modeController.shutDown();
		overlayManager.remove(thieverMode);
		syncService.stop();

		roguePartyService.clearRuntimeState();
		nearbyPlayerService.shutDown();
		friendsChatService.shutDown();
		lookupService.shutDown();

		thieverMode.reset();
		listRenderer.removeLevelsFromMemberList();

		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}

		nearbyPlayerService.clearCapturedNearbyNames();
	}

	private void wireServices()
	{
		modeController.setHost(this);
		lookupService.setContext(this);
		friendsChatService.setHost(this);
		nearbyPlayerService.setHost(this);
		roguePartyService.setHost(this);

		syncService.setCallbacks(
				this::onSyncedListsChanged,
				this::refreshPanel
		);
	}

	private void onSyncedListsChanged()
	{
		invalidateConfiguredNameCaches();
		refreshConfiguredPlayerLists();
		refreshPanel();
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (!"rogue".equalsIgnoreCase(event.getCommand()))
		{
			return;
		}

		String[] arguments = event.getArguments();
		if (arguments.length != 1
				|| !"antipker".equalsIgnoreCase(arguments[0]))
		{
			return;
		}

		boolean enabled = thieverMode.toggleAntiPkerMode();

		client.addChatMessage(
				ChatMessageType.GAMEMESSAGE,
				"",
				"Rogue Chests FC: Anti-PKer mode "
						+ (enabled ? "enabled." : "disabled."),
				""
		);
	}

	@Subscribe
	public void onFriendsChatChanged(FriendsChatChanged event)
	{
		clientThread.invokeLater(() ->
		{
			reconcileFriendsChatAccess();
			return true;
		});

		if (client.getGameState() != GameState.LOGGED_IN
				|| !modeController.isAuthorizedFeaturesActive())
		{
			return;
		}

		if (!event.isJoined()
				|| !friendsChatService.isInRequiredFriendsChat())
		{
			suspendFriendsChatWork();
			return;
		}

		nearbyPlayerService.clearNearbyMemberTracking();
		nearbyPlayerService.clearEquipmentScanState();
		lookupService.setJoinMessagesSuppressed(true);

		if (modeController.isStaffFeaturesActive())
		{
			friendsChatService.queueCurrentMembersForStaff();
		}
		else
		{
			friendsChatService.queueCurrentMembersForThiever();
		}
	}

	@Subscribe
	public void onFriendsChatMemberJoined(
			FriendsChatMemberJoined event)
	{
		if (!modeController.isAuthorizedFeaturesActive()
				|| !friendsChatService.isInRequiredFriendsChat())
		{
			return;
		}

		friendsChatService.onMemberJoined(event.getMember());
	}

	@Subscribe
	public void onFriendsChatMemberLeft(
			FriendsChatMemberLeft event)
	{
		if (!modeController.isAuthorizedFeaturesActive()
				|| !friendsChatService.isInRequiredFriendsChat())
		{
			return;
		}

		friendsChatService.onMemberLeft(event.getMember());
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			thieverMode.resetThievingSession();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		thieverMode.onItemContainerChanged(
				event,
				modeController.isThieverFeaturesActive()
		);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		thieverMode.onMenuOptionClicked(event);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		thieverMode.onWidgetLoaded(event.getGroupId());
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		thieverMode.onWidgetClosed(event.getGroupId());
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort ignored)
	{
		if (!modeController.isThieverFeaturesActive()
				|| !friendsChatService.isInRequiredFriendsChat()
				|| client.isMenuOpen())
		{
			return;
		}

		MenuEntry[] entries = client.getMenu().getMenuEntries();
		if (entries.length < 2)
		{
			return;
		}

		int walkIndex = -1;
		int searchIndex = -1;
		boolean chestPresent = false;
		boolean blockedNpcPresent = false;
		boolean playerPresent = false;

		for (int i = 0; i < entries.length; i++)
		{
			MenuEntry entry = entries[i];
			if (entry == null)
			{
				continue;
			}

			if (entry.getPlayer() != null)
			{
				playerPresent = true;
			}

			if (WALK_HERE_OPTION.equalsIgnoreCase(entry.getOption()))
			{
				walkIndex = i;
			}

			if (entry.getIdentifier() == ROGUES_CASTLE_CHEST_ID)
			{
				chestPresent = true;

				if (SEARCH_FOR_TRAPS_OPTION.equalsIgnoreCase(
						entry.getOption()))
				{
					searchIndex = i;
				}
			}

			NPC npc = entry.getNpc();
			if (npc != null)
			{
				String npcName = npc.getName();

				if (ROGUE_NPC_NAME.equalsIgnoreCase(npcName)
						|| EDWARD_NPC_NAME.equalsIgnoreCase(npcName))
				{
					blockedNpcPresent = true;
				}
			}
		}

		if (playerPresent)
		{
			return;
		}

		if (chestPresent)
		{
			int desiredIndex =
					thieverMode.isBankLockoutActive()
							? walkIndex
							: searchIndex;

			moveMenuEntryToLeftClick(entries, desiredIndex);
			return;
		}

		if (blockedNpcPresent)
		{
			moveMenuEntryToLeftClick(entries, walkIndex);
		}
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		nearbyPlayerService.onPlayerSpawned(event.getPlayer());
	}

	@Subscribe
	public void onGameTick(GameTick ignored)
	{
		modeController.autoEnableThieverModeIfNeeded();
		modeController.processTransition();

		thieverMode.onGameTick(
				modeController.isThieverFeaturesActive(),
				friendsChatService.isInRequiredFriendsChat()
		);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			runAccessSanityCheck();
		}

		if (!modeController.isAuthorizedFeaturesActive())
		{
			return;
		}

		roguePartyService.processTick();
		lookupService.processTick();
		friendsChatService.processTick();
		nearbyPlayerService.processTick();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState gameState = event.getGameState();

		thieverMode.onGameStateChanged(gameState);
		roguePartyService.onGameStateChanged(gameState);
		nearbyPlayerService.onGameStateChanged(gameState);

		if (gameState == GameState.LOGIN_SCREEN
				|| gameState == GameState.HOPPING)
		{
			lastAccessSanityCheck = null;
		}

		if (gameState != GameState.LOGGED_IN)
		{
			return;
		}

		reconcileFriendsChatAccess();

		if (modeController.isAuthorizedFeaturesActive()
				&& friendsChatService.isInRequiredFriendsChat())
		{
			roguePartyService.updateJoinBannerForLogin();
		}
	}

	@Subscribe(priority = Float.NEGATIVE_INFINITY)
	public void onPostClientTick(PostClientTick ignored)
	{
		if (!isStaffFeaturesActive()
				|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		long now = System.nanoTime();

		if (lastMemberListRefreshNanos != 0L
				&& now - lastMemberListRefreshNanos
				< MEMBER_LIST_REFRESH_INTERVAL_NANOS)
		{
			return;
		}

		lastMemberListRefreshNanos = now;
		renderFriendsChatMemberList();
	}

	@Subscribe(priority = Float.NEGATIVE_INFINITY)
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (!modeController.isAuthorizedFeaturesActive()
				|| !friendsChatService.isInRequiredFriendsChat()
				|| client.getGameState() != GameState.LOGGED_IN
				|| event.getScriptId()
				!= ScriptID.FRIENDS_CHAT_CHANNEL_REBUILD)
		{
			return;
		}

		reconcileFriendsChatAccess();
		friendsChatService.reconcileMembers();

		nearbyPlayerService.removeCurrentMembersFromCapturedList(
				new HashSet<>(
						friendsChatService.getCurrentMembers()
				)
		);

		if (isStaffFeaturesActive())
		{
			friendsChatService.refreshF2pMemberStates();
			renderFriendsChatMemberList();
		}
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		roguePartyService.onUserJoin(event.getMemberId());
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		roguePartyService.onUserPart(event.getMemberId());
	}

	@Subscribe
	public void onTilePing(TilePing event)
	{
		roguePartyService.onTilePing(event);
	}

	@Subscribe
	public void onOverheadTextChanged(
			OverheadTextChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();

		if (localPlayer == null
				|| !RogueChestsFcRegions.isTrackingRegion(
				localPlayer.getWorldLocation().getRegionID()))
		{
			return;
		}

		if (!(event.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) event.getActor();

		if (npc.getName() != null
				&& ROGUE_NPC_NAME.equalsIgnoreCase(npc.getName()))
		{
			npc.setOverheadText(null);
		}
	}

	private void reconcileFriendsChatAccess()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		lastAccessSanityCheck = Instant.now();

		RogueChestsFcConfig.PluginMode mode =
				modeController.getPluginMode();

		boolean inRequiredChat =
				friendsChatService.isInRequiredFriendsChat();

		if (mode == RogueChestsFcConfig.PluginMode.STAFF)
		{
			if (!inRequiredChat)
			{
				panel.setModeState(
						mode,
						modeController.isStaffFeaturesActive()
				);
				return;
			}

			boolean authorized =
					friendsChatService.isStaffAuthorized();

			if (authorized)
			{
				if (!modeController.isStaffFeaturesActive()
						&& modeController.isNotTransitioningTo(
						RogueChestsFcConfig.PluginMode.STAFF))
				{
					modeController.activateStaff();
				}
			}
			else if (modeController.isStaffFeaturesActive()
					&& modeController.isNotTransitioningTo(
					RogueChestsFcConfig.PluginMode.NONE))
			{
				modeController.deactivate();
			}

			panel.setModeState(mode, authorized);
			return;
		}

		if (mode == RogueChestsFcConfig.PluginMode.THIEVER)
		{
			if (inRequiredChat
					&& !modeController.isThieverFeaturesActive()
					&& modeController.isNotTransitioningTo(
					RogueChestsFcConfig.PluginMode.THIEVER))
			{
				modeController.activateThiever();
			}

			panel.setModeState(mode, false);
			return;
		}

		if (modeController.isAuthorizedFeaturesActive()
				&& modeController.isNotTransitioningTo(
				RogueChestsFcConfig.PluginMode.NONE))
		{
			modeController.deactivate();
		}

		panel.setModeState(mode, false);
	}

	private void runAccessSanityCheck()
	{
		Instant now = Instant.now();

		if (lastAccessSanityCheck != null
				&& Duration.between(lastAccessSanityCheck, now)
				.compareTo(ACCESS_SANITY_CHECK_INTERVAL) < 0)
		{
			return;
		}

		reconcileFriendsChatAccess();
	}

	private void suspendFriendsChatWork()
	{
		lookupService.clearPendingWork();
		lookupService.setJoinMessagesSuppressed(true);
		friendsChatService.clearRosterState();
		nearbyPlayerService.clearEquipmentScanState();
		roguePartyService.clearPendingMembershipChecks();
		lastMemberListRefreshNanos = 0L;
		refreshPanel();
	}

	private void moveMenuEntryToLeftClick(
			MenuEntry[] entries,
			int desiredIndex)
	{
		if (desiredIndex < 0
				|| desiredIndex >= entries.length)
		{
			return;
		}

		int leftClickIndex = entries.length - 1;

		if (desiredIndex == leftClickIndex)
		{
			return;
		}

		MenuEntry temporary = entries[leftClickIndex];
		entries[leftClickIndex] = entries[desiredIndex];
		entries[desiredIndex] = temporary;

		client.getMenu().setMenuEntries(entries);
	}

	private void refreshConfiguredPlayerLists()
	{
		invalidateConfiguredNameCaches();

		if (!isStaffFeaturesActive())
		{
			refreshPanel();
			return;
		}

		clientThread.invoke(() ->
		{
			Set<String> bannedNames = getBannedNames();

			for (String bannedName : bannedNames)
			{
				lookupService.handleBannedMember(bannedName);
			}

			friendsChatService.reconcileMembers();
			friendsChatService.refreshF2pMemberStates();

			FriendsChatManager manager =
					client.getFriendsChatManager();

			if (manager != null)
			{
				FriendsChatMember[] members =
						manager.getMembers();

				if (members != null)
				{
					for (FriendsChatMember member : members)
					{
						if (member == null)
						{
							continue;
						}

						String playerName = member.getName();
						String normalizedName =
								normalizeName(playerName);

						if (normalizedName.isEmpty())
						{
							continue;
						}

						if (bannedNames.contains(normalizedName))
						{
							lookupService.handleBannedMember(
									normalizedName
							);
						}
						else
						{
							lookupService.queueLookup(playerName);
						}
					}
				}
			}

			renderFriendsChatMemberList();
			refreshPanel();
		});
	}

	private void renderFriendsChatMemberList()
	{
		listRenderer.applyLevelsToMemberList(
				isStaffFeaturesActive(),
				getIgnoredNames(),
				getBannedNames(),
				friendsChatService.getUnrankedF2pMembers(),
				lookupService.getThievingLevels(),
				lookupService::handleBannedMember
		);
	}

	private void invalidateConfiguredNameCaches()
	{
		cachedIgnoredNamesSource = null;
		cachedIgnoredNames = Collections.emptySet();
		cachedBannedNamesSource = null;
		cachedBannedNames = Collections.emptySet();
	}

	@Override
	public boolean isInRequiredFriendsChat()
	{
		return friendsChatService.isInRequiredFriendsChat();
	}

	@Override
	public boolean isStaffAuthorized()
	{
		return friendsChatService.isStaffAuthorized();
	}

	@Override
	public boolean isAuthorizedFeaturesActive()
	{
		return modeController.isAuthorizedFeaturesActive();
	}

	@Override
	public boolean isStaffFeaturesActive()
	{
		return modeController.isStaffFeaturesActive()
				&& friendsChatService.isInRequiredFriendsChat()
				&& friendsChatService.isStaffAuthorized();
	}

	@Override
	public boolean isThieverFeaturesActive()
	{
		return modeController.isThieverFeaturesActive();
	}

	@Override
	public boolean isStaffInfrastructureActive()
	{
		return modeController.isAuthorizedFeaturesActive()
				&& modeController.isStaffFeaturesActive()
				&& modeController.isStaffMode()
				&& friendsChatService.isStaffAuthorized();
	}

	@Override
	public Set<String> getCurrentMembers()
	{
		return friendsChatService.getCurrentMembers();
	}

	@Override
	public Set<String> getUnrankedF2pMembers()
	{
		return friendsChatService.getUnrankedF2pMembers();
	}

	@Override
	public Set<String> getIgnoredNames()
	{
		String source = config.ignoredNames();

		if (!Objects.equals(source, cachedIgnoredNamesSource))
		{
			cachedIgnoredNames = parseConfiguredNames(source);
			cachedIgnoredNamesSource = source;
		}

		return cachedIgnoredNames;
	}

	@Override
	public Set<String> getBannedNames()
	{
		String source = config.bannedNames();

		if (!Objects.equals(source, cachedBannedNamesSource))
		{
			cachedBannedNames = parseConfiguredNames(source);
			cachedBannedNamesSource = source;
		}

		return cachedBannedNames;
	}

	@Override
	public void clearRuntimeState()
	{
		lookupService.clearRuntimeState();
		friendsChatService.clearRosterState();
		nearbyPlayerService.clearRuntimeState();
		roguePartyService.clearRuntimeState();
		thieverMode.reset();
		lastMemberListRefreshNanos = 0L;
	}

	@Override
	public void clearStaffOnlyRuntimeForThieverSwitch()
	{
		lookupService.clearPendingWork();
		roguePartyService.clearStaffOnlyRuntimeForThieverSwitch();
		nearbyPlayerService.clearEquipmentScanState();
		lastMemberListRefreshNanos = 0L;
	}

	@Override
	public void queueCurrentMembersForStaff()
	{
		friendsChatService.queueCurrentMembersForStaff();
	}

	@Override
	public void queueCurrentMembersForThiever()
	{
		friendsChatService.queueCurrentMembersForThiever();
	}

	@Override
	public void setJoinMessagesSuppressed(boolean suppressed)
	{
		lookupService.setJoinMessagesSuppressed(suppressed);
	}

	@Override
	public void onModeDeactivationStarted()
	{
		roguePartyService.onModeDeactivationStarted();
	}

	@Override
	public void onSharedOverlaysDeactivated()
	{
		// Overlay-specific cleanup is already handled by ModeController.
	}

	@Override
	public void updatePartyJoinBannerForLogin()
	{
		roguePartyService.updateJoinBannerForLogin();
	}

	@Override
	public void removeCapturedNearbyName(String playerName)
	{
		nearbyPlayerService.removeCapturedNearbyName(playerName);
	}

	@Override
	public void removeCurrentMembersFromCapturedList(
			Set<String> normalizedNames)
	{
		nearbyPlayerService.removeCurrentMembersFromCapturedList(
				normalizedNames
		);
	}

	@Override
	public void removeNearbyMemberTracking(
			String normalizedName)
	{
		nearbyPlayerService.removeNearbyMemberTracking(
				normalizedName
		);
	}

	@Override
	public void removeEquipmentScannedVisibleMember(
			String normalizedName)
	{
		nearbyPlayerService.removeEquipmentScannedVisibleMember(
				normalizedName
		);
	}

	@Override
	public void refreshPanel()
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			panel.refresh();
		}
		else
		{
			SwingUtilities.invokeLater(panel::refresh);
		}
	}

	void setPluginMode(RogueChestsFcConfig.PluginMode mode)
	{
		modeController.setPluginMode(mode);
	}

	boolean isThieverMode()
	{
		return modeController.isThieverMode();
	}

	boolean shouldShowPartyJoinBanner()
	{
		return roguePartyService.shouldShowPartyJoinBanner();
	}

	boolean shouldShowPartyReminder()
	{
		return roguePartyService.shouldShowPartyReminder();
	}

	void dismissPartyJoinBanner()
	{
		roguePartyService.dismissPartyJoinBanner();
	}

	void joinStaffParty()
	{
		roguePartyService.joinStaffParty();
	}

	void leaveStaffParty()
	{
		roguePartyService.leaveStaffParty();
	}

	boolean isInParty()
	{
		return roguePartyService.isInParty();
	}

	void syncBanListNow()
	{
		syncService.syncNow();
	}

	boolean isBanListSyncInProgress()
	{
		return syncService.isSyncInProgress();
	}

	Instant getLastBanListSync()
	{
		return syncService.getLastSync();
	}

	String getLastBanListSyncError()
	{
		return syncService.getLastSyncError();
	}

	int getNearbyEnemyCount()
	{
		return nearbyPlayerService.getNearbyEnemyCount();
	}

	int getNearbyFcCount()
	{
		return nearbyPlayerService.getNearbyFcCount();
	}

	List<LowLevelMember> getLowLevelMembers()
	{
		if (!isStaffFeaturesActive())
		{
			return Collections.emptyList();
		}

		List<RogueChestsFcLookupService.LowLevelMember> source =
				lookupService.getLowLevelMembers();

		List<LowLevelMember> members =
				new ArrayList<>(source.size());

		for (RogueChestsFcLookupService.LowLevelMember member : source)
		{
			members.add(
					new LowLevelMember(
							member.getName(),
							member.isDeparted()
					)
			);
		}

		return members;
	}

	List<OvertimeMember> getOvertimeMembers()
	{
		if (!isAuthorizedFeaturesActive())
		{
			return Collections.emptyList();
		}

		List<RogueChestsFcNearbyPlayerService.OvertimeMember> source =
				nearbyPlayerService.getOvertimeMembers();

		List<OvertimeMember> members =
				new ArrayList<>(source.size());

		for (RogueChestsFcNearbyPlayerService.OvertimeMember member : source)
		{
			members.add(
					new OvertimeMember(
							member.getName(),
							member.getElapsed(),
							member.isPaused()
					)
			);
		}

		return members;
	}

	List<String> getIgnoredPlayerNames()
	{
		return getConfiguredPlayerNames(
				config.ignoredNames()
		);
	}

	List<String> getBannedPlayerNames()
	{
		return getConfiguredPlayerNames(
				config.bannedNames()
		);
	}

	List<String> getCapturedNearbyPlayerNames()
	{
		return nearbyPlayerService
				.getCapturedNearbyPlayerNames();
	}

	List<String> getOvertimeWhitelistPlayerNames()
	{
		return nearbyPlayerService
				.getOvertimeWhitelistPlayerNames();
	}

	void addOvertimeWhitelistNames(String names)
	{
		nearbyPlayerService.addOvertimeWhitelistNames(names);
	}

	void removeOvertimeWhitelistName(String playerName)
	{
		nearbyPlayerService.removeOvertimeWhitelistName(playerName);
	}

	void clearCapturedNearbyNames()
	{
		nearbyPlayerService.clearCapturedNearbyNames();
	}

	void copyIgnoredNames()
	{
		copyNamesToClipboard(getIgnoredPlayerNames());
	}

	void copyBannedNames()
	{
		copyNamesToClipboard(getBannedPlayerNames());
	}

	void copyCapturedNearbyNames()
	{
		copyNamesToClipboard(
				getCapturedNearbyPlayerNames()
		);
	}

	void copyOvertimeWhitelistNames()
	{
		copyNamesToClipboard(
				getOvertimeWhitelistPlayerNames()
		);
	}

	private List<String> getConfiguredPlayerNames(
			String configuredNames)
	{
		return new ArrayList<>(
				getConfiguredPlayerNameMap(
						configuredNames
				).values()
		);
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
				.map(RogueChestsFcPlugin::normalizeName)
				.filter(name -> !name.isEmpty())
				.forEach(names::add);

		return names;
	}

	private void copyNamesToClipboard(List<String> names)
	{
		if (names == null || names.isEmpty())
		{
			return;
		}

		try
		{
			Toolkit.getDefaultToolkit()
					.getSystemClipboard()
					.setContents(
							new StringSelection(
									String.join("\n", names)
							),
							null
					);
		}
		catch (IllegalStateException exception)
		{
			log.debug(
					"Unable to access the system clipboard",
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

		return Text.toJagexName(
				Text.removeTags(playerName)
		).toLowerCase(Locale.ROOT);
	}

	static final class LowLevelMember
	{
		private final String name;
		private final boolean departed;

		LowLevelMember(String name, boolean departed)
		{
			this.name = name;
			this.departed = departed;
		}

		String getName()
		{
			return name;
		}

		boolean isDeparted()
		{
			return departed;
		}
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
}