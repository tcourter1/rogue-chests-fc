package com.roguechestsfc;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.FriendsChatMemberJoined;
import net.runelite.api.events.FriendsChatMemberLeft;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
		name = "Rogue Chests FC",
		description = "Contains utilities to help manage the Rogue Chests friends chat.",
		tags = {"rogue", "chests", "fc", "friends chat", "thieving"}
)
public class RogueChestsFcPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "roguechestsfc";
	private static final int ROGUES_CASTLE_REGION_ID = 13117;
	private static final String IGNORED_NAMES_KEY = "ignoredNames";
	private static final String BANNED_NAMES_KEY = "bannedNames";
	private static final String CAPTURED_NEARBY_NAMES_KEY =
			"capturedNearbyNames";
	private static final String OVERTIME_WHITELIST_NAMES_KEY =
			"overtimeWhitelistNames";

	private static final String IGNORE_MENU_OPTION =
			"Plugin ignore";

	private static final Duration LOOKUP_COOLDOWN =
			Duration.ofMinutes(2);

	private static final Duration JOIN_MESSAGE_COOLDOWN =
			Duration.ofMinutes(2);

	private static final Duration DEPARTED_DISPLAY_DURATION =
			Duration.ofMinutes(1);

	private static final int LOOKUPS_PER_TICK = 5;
	private static final int REQUIRED_THIEVING_LEVEL = 84;

	private static final String GREEN_LEVEL_MARKER =
			" <col=00ff00>";

	private static final String RED_LEVEL_MARKER =
			" <col=ff0000>";

	private static final String LEVEL_SUFFIX = "</col>";
	private static final String RED_TEXT_OPEN = "<col=ff0000>";
	private static final String TEXT_CLOSE = "</col>";
	private static final String BANNED_MEMBER_TEXT = " BAN";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private HiscoreClient hiscoreClient;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RogueChestsFcOverlay overlay;

	@Inject
	private RogueChestsFcOvertimeOverlay overtimeOverlay;

	@Inject
	private RogueChestsFcPanel panel;

	@Inject
	private RogueChestsFcConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	private NavigationButton navigationButton;

	private final Map<String, Integer> thievingLevels =
			new ConcurrentHashMap<>();

	private final Map<String, Instant> lastLookupTimes =
			new ConcurrentHashMap<>();

	private final Map<String, Instant> lastJoinMessageTimes =
			new ConcurrentHashMap<>();

	private final Map<String, String> displayNames =
			new ConcurrentHashMap<>();

	private final Map<String, LowLevelMember> lowLevelMembers =
			new ConcurrentHashMap<>();

	private final Set<String> currentMembers =
			ConcurrentHashMap.newKeySet();

	private final Map<String, NearbyMemberTracker> nearbyMemberTrackers =
			new ConcurrentHashMap<>();

	private final Set<String> overtimeTrackingSuppressedUntilExit =
			ConcurrentHashMap.newKeySet();

	private final Set<String> pendingLookups =
			ConcurrentHashMap.newKeySet();

	private final Set<String> pendingJoinMessages =
			ConcurrentHashMap.newKeySet();

	private final ConcurrentLinkedQueue<String> lookupQueue =
			new ConcurrentLinkedQueue<>();

	private volatile boolean suppressJoinMessages = true;

	@Provides
	RogueChestsFcConfig provideConfig(
			ConfigManager configManager)
	{
		return configManager.getConfig(
				RogueChestsFcConfig.class
		);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		overlayManager.add(overtimeOverlay);

		BufferedImage icon =
				ImageUtil.loadImageResource(
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

		panel.refresh();

		suppressJoinMessages = true;
		queueCurrentMembersWhenAvailable();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(overtimeOverlay);

		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(
					navigationButton
			);

			navigationButton = null;
		}

		lookupQueue.clear();
		pendingLookups.clear();
		pendingJoinMessages.clear();
		displayNames.clear();
		lastLookupTimes.clear();
		lastJoinMessageTimes.clear();
		thievingLevels.clear();
		lowLevelMembers.clear();
		currentMembers.clear();
		clearNearbyMemberTracking();

		suppressJoinMessages = true;

		clientThread.invoke(
				this::removeLevelsFromMemberList
		);
	}

	@Subscribe
	public void onFriendsChatChanged(
			FriendsChatChanged event)
	{
		clearNearbyMemberTracking();

		if (event.isJoined())
		{
			suppressJoinMessages = true;
			pendingJoinMessages.clear();
			queueCurrentMembersWhenAvailable();
		}
		else
		{
			suppressJoinMessages = true;

			lookupQueue.clear();
			pendingLookups.clear();
			pendingJoinMessages.clear();
			displayNames.clear();
			currentMembers.clear();
			lowLevelMembers.clear();

			clientThread.invoke(
					this::removeLevelsFromMemberList
			);
		}
	}

	@Subscribe
	public void onFriendsChatMemberJoined(
			FriendsChatMemberJoined event)
	{
		FriendsChatMember member = event.getMember();

		if (member == null)
		{
			return;
		}

		String playerName = member.getName();
		String normalizedName =
				normalizeName(playerName);

		if (normalizedName.isEmpty())
		{
			return;
		}

		currentMembers.add(normalizedName);
		removeCapturedNearbyName(playerName);

		LowLevelMember lowLevelMember =
				lowLevelMembers.get(normalizedName);

		if (lowLevelMember != null)
		{
			lowLevelMember.setDepartedAt(null);
		}

		if (isBannedPlayer(playerName))
		{
			cancelLookup(normalizedName);
			thievingLevels.remove(normalizedName);
			lowLevelMembers.remove(normalizedName);

			if (config.showBannedJoinMessage())
			{
				showJoinNotification(
						normalizedName,
						playerName,
						"(Banned player)"
				);
			}

			clientThread.invoke(
					this::applyLevelsToMemberList
			);

			return;
		}

		if (shouldQueueJoinMessage(normalizedName))
		{
			pendingJoinMessages.add(normalizedName);

			Integer cachedLevel =
					thievingLevels.get(normalizedName);

			if (cachedLevel != null)
			{
				showLowLevelJoinMessage(
						normalizedName,
						playerName,
						cachedLevel
				);
			}
		}

		queueLookup(playerName);
	}

	@Subscribe
	public void onFriendsChatMemberLeft(
			FriendsChatMemberLeft event)
	{
		FriendsChatMember member = event.getMember();

		if (member == null)
		{
			return;
		}

		String normalizedName =
				normalizeName(member.getName());

		currentMembers.remove(normalizedName);
		pendingJoinMessages.remove(normalizedName);
		removeNearbyMemberTracking(normalizedName);

		LowLevelMember lowLevelMember =
				lowLevelMembers.get(normalizedName);

		if (lowLevelMember != null)
		{
			lowLevelMember.setDepartedAt(
					Instant.now()
			);
		}
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		if (!isInFriendsChat() || !isInRoguesCastleRegion())
		{
			return;
		}

		Player player = event.getPlayer();
		Player localPlayer = client.getLocalPlayer();

		if (player == null || localPlayer == null)
		{
			return;
		}

		String playerName = player.getName();
		String localPlayerName = localPlayer.getName();

		String normalizedName =
				normalizeName(playerName);

		String normalizedLocalName =
				normalizeName(localPlayerName);

		if (normalizedName.isEmpty()
				|| normalizedName.equals(normalizedLocalName)
				|| currentMembers.contains(normalizedName))
		{
			return;
		}

		addCapturedNearbyName(playerName);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		for (int i = 0; i < LOOKUPS_PER_TICK; i++)
		{
			String normalizedName =
					lookupQueue.poll();

			if (normalizedName == null)
			{
				break;
			}

			startLookup(normalizedName);
		}

		removeExpiredDepartedMembers();
		updateNearbyMemberTracking();
	}

	@Subscribe
	public void onGameStateChanged(
			GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			clearNearbyMemberTracking();
		}
	}

	@Subscribe(priority = Float.NEGATIVE_INFINITY)
	public void onPostClientTick(
			PostClientTick event)
	{
		applyLevelsToMemberList();
	}

	@Subscribe(priority = Float.NEGATIVE_INFINITY)
	public void onScriptPostFired(
			ScriptPostFired event)
	{
		if (event.getScriptId()
				== ScriptID.FRIENDS_CHAT_CHANNEL_REBUILD)
		{
			applyLevelsToMemberList();
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		String playerName =
				findFriendsChatPlayerInMenu(
						event.getMenuEntries()
				);

		if (playerName.isEmpty())
		{
			return;
		}

		String normalizedName =
				normalizeName(playerName);

		if (normalizedName.isEmpty()
				|| !currentMembers.contains(normalizedName)
				|| getIgnoredNames().contains(
				normalizedName
		))
		{
			return;
		}

		client.createMenuEntry(1)
				.setOption(IGNORE_MENU_OPTION)
				.setTarget(
						"<col=ff9040>"
								+ playerName
								+ "</col>"
				)
				.setType(MenuAction.RUNELITE)
				.onClick(menuEntry ->
						addIgnoredNames(playerName));
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
		return getConfiguredPlayerNames(
				config.capturedNearbyNames()
		);
	}

	List<String> getOvertimeWhitelistPlayerNames()
	{
		return getConfiguredPlayerNames(
				config.overtimeWhitelistNames()
		);
	}

	void addIgnoredNames(String names)
	{
		addConfiguredNames(
				IGNORED_NAMES_KEY,
				config.ignoredNames(),
				names
		);
	}

	void addBannedNames(String names)
	{
		addConfiguredNames(
				BANNED_NAMES_KEY,
				config.bannedNames(),
				names
		);
	}

	void addOvertimeWhitelistNames(String names)
	{
		addConfiguredNames(
				OVERTIME_WHITELIST_NAMES_KEY,
				config.overtimeWhitelistNames(),
				names
		);

		parseConfiguredNames(names).forEach(normalizedName ->
		{
			removeNearbyMemberTracking(normalizedName);
			overtimeTrackingSuppressedUntilExit.remove(
					normalizedName
			);
		});
	}

	void removeIgnoredName(String playerName)
	{
		removeConfiguredName(
				IGNORED_NAMES_KEY,
				config.ignoredNames(),
				playerName
		);
	}

	void removeBannedName(String playerName)
	{
		removeConfiguredName(
				BANNED_NAMES_KEY,
				config.bannedNames(),
				playerName
		);
	}

	void removeOvertimeWhitelistName(String playerName)
	{
		String normalizedName =
				normalizeName(playerName);

		removeConfiguredName(
				OVERTIME_WHITELIST_NAMES_KEY,
				config.overtimeWhitelistNames(),
				playerName
		);

		if (!normalizedName.isEmpty())
		{
			removeNearbyMemberTracking(normalizedName);
			overtimeTrackingSuppressedUntilExit.add(
					normalizedName
			);
		}
	}

	void removeCapturedNearbyName(String playerName)
	{
		removeConfiguredName(
				CAPTURED_NEARBY_NAMES_KEY,
				config.capturedNearbyNames(),
				playerName
		);
	}

	void clearCapturedNearbyNames()
	{
		configManager.setConfiguration(
				CONFIG_GROUP,
				CAPTURED_NEARBY_NAMES_KEY,
				""
		);

		panel.refresh();
	}

	void addCapturedNearbyNamesToBanList()
	{
		List<String> capturedNames =
				getCapturedNearbyPlayerNames();

		if (capturedNames.isEmpty())
		{
			return;
		}

		addConfiguredNames(
				BANNED_NAMES_KEY,
				config.bannedNames(),
				String.join("\n", capturedNames)
		);

		clearCapturedNearbyNames();
	}

	void copyIgnoredNames()
	{
		copyNamesToClipboard(
				getIgnoredPlayerNames()
		);
	}

	void copyBannedNames()
	{
		copyNamesToClipboard(
				getBannedPlayerNames()
		);
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

	private void addCapturedNearbyName(String playerName)
	{
		String normalizedName =
				normalizeName(playerName);

		if (normalizedName.isEmpty()
				|| currentMembers.contains(normalizedName))
		{
			return;
		}

		Map<String, String> capturedNames =
				getConfiguredPlayerNameMap(
						config.capturedNearbyNames()
				);

		if (capturedNames.containsKey(normalizedName))
		{
			return;
		}

		capturedNames.put(
				normalizedName,
				Text.toJagexName(playerName)
		);

		saveConfiguredNames(
				CAPTURED_NEARBY_NAMES_KEY,
				capturedNames,
				false
		);
	}

	private void copyNamesToClipboard(
			List<String> names)
	{
		if (names == null || names.isEmpty())
		{
			return;
		}

		String clipboardText =
				String.join("\n", names);

		try
		{
			Toolkit.getDefaultToolkit()
					.getSystemClipboard()
					.setContents(
							new StringSelection(
									clipboardText
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

	private void updateNearbyMemberTracking()
	{
		if (!isInFriendsChat()
				|| !isInRoguesCastleRegion())
		{
			clearNearbyMemberTracking();
			return;
		}

		Player localPlayer = client.getLocalPlayer();

		if (localPlayer == null)
		{
			clearNearbyMemberTracking();
			return;
		}

		String localPlayerName =
				normalizeName(localPlayer.getName());

		Instant now = Instant.now();
		Duration threshold =
				Duration.ofMinutes(
						config.overtimeMinutes()
				);

		Duration renderGracePeriod =
				Duration.ofSeconds(
						config.overtimeRenderGraceSeconds()
				);

		Set<String> visibleMembers =
				new HashSet<>();

		Set<String> overtimeWhitelistNames =
				getOvertimeWhitelistNames();

		for (Player player : client.getPlayers())
		{
			if (player == null)
			{
				continue;
			}

			String playerName = player.getName();
			String normalizedName =
					normalizeName(playerName);

			if (normalizedName.isEmpty()
					|| normalizedName.equals(
					localPlayerName
			)
					|| !currentMembers.contains(
					normalizedName
			))
			{
				continue;
			}

			visibleMembers.add(normalizedName);

			if (overtimeWhitelistNames.contains(
					normalizedName
			))
			{
				removeNearbyMemberTracking(normalizedName);
				overtimeTrackingSuppressedUntilExit.remove(
						normalizedName
				);
				continue;
			}

			if (overtimeTrackingSuppressedUntilExit.contains(
					normalizedName
			))
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
						!visibleMembers.contains(
								normalizedName
						)
		);

		for (Map.Entry<String, NearbyMemberTracker> entry
				: new ArrayList<>(
				nearbyMemberTrackers.entrySet()
		))
		{
			String normalizedName = entry.getKey();
			NearbyMemberTracker tracker = entry.getValue();

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

			if (tracker.getPausedDuration(now).compareTo(
					renderGracePeriod
			) >= 0)
			{
				removeNearbyMemberTracking(normalizedName);
			}
		}
	}

	private void showOvertimeNotification(
			String playerName,
			int limitMinutes)
	{
		String message =
				new ChatMessageBuilder()
						.append(
								Color.RED,
								Text.toJagexName(
										playerName
								)
						)
						.append(
								" has remained within render distance for over "
						)
						.append(
								Color.RED,
								limitMinutes
										+ " minutes"
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

	private void removeNearbyMemberTracking(
			String normalizedName)
	{
		nearbyMemberTrackers.remove(normalizedName);
	}

	private void clearNearbyMemberTracking()
	{
		nearbyMemberTrackers.clear();
		overtimeTrackingSuppressedUntilExit.clear();
	}

	private boolean isInFriendsChat()
	{
		return client.getFriendsChatManager() != null;
	}

	private boolean isInRoguesCastleRegion()
	{
		Player localPlayer = client.getLocalPlayer();

		return localPlayer != null
				&& localPlayer.getWorldLocation().getRegionID()
				== ROGUES_CASTLE_REGION_ID;
	}

	private void addConfiguredNames(
			String configKey,
			String currentValue,
			String newNames)
	{
		if (newNames == null
				|| newNames.trim().isEmpty())
		{
			return;
		}

		Map<String, String> namesByNormalizedName =
				getConfiguredPlayerNameMap(
						currentValue
				);

		Arrays.stream(
						newNames.split("[,\\r\\n]+")
				)
				.map(String::trim)
				.map(Text::toJagexName)
				.filter(name -> !name.isEmpty())
				.forEach(name ->
						namesByNormalizedName.putIfAbsent(
								normalizeName(name),
								name
						)
				);

		boolean refreshFriendsChat =
				!OVERTIME_WHITELIST_NAMES_KEY.equals(
						configKey
				);

		saveConfiguredNames(
				configKey,
				namesByNormalizedName,
				refreshFriendsChat
		);
	}

	private void removeConfiguredName(
			String configKey,
			String currentValue,
			String playerName)
	{
		String normalizedName =
				normalizeName(playerName);

		if (normalizedName.isEmpty())
		{
			return;
		}

		Map<String, String> namesByNormalizedName =
				getConfiguredPlayerNameMap(
						currentValue
				);

		if (namesByNormalizedName.remove(
				normalizedName
		) == null)
		{
			return;
		}

		boolean refreshFriendsChat =
				!CAPTURED_NEARBY_NAMES_KEY.equals(
						configKey
				)
						&& !OVERTIME_WHITELIST_NAMES_KEY.equals(
						configKey
				);

		saveConfiguredNames(
				configKey,
				namesByNormalizedName,
				refreshFriendsChat
		);
	}

	private void saveConfiguredNames(
			String configKey,
			Map<String, String> namesByNormalizedName,
			boolean refreshFriendsChat)
	{
		String value = String.join(
				"\n",
				namesByNormalizedName.values()
		);

		configManager.setConfiguration(
				CONFIG_GROUP,
				configKey,
				value
		);

		panel.refresh();

		if (refreshFriendsChat)
		{
			refreshConfiguredPlayerLists();
		}
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

	private Map<String, String>
	getConfiguredPlayerNameMap(
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
						configuredNames.split(
								"[,\\r\\n]+"
						)
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

	private void removeCurrentMembersFromCapturedList(
			Set<String> memberNames)
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

		if (changed)
		{
			saveConfiguredNames(
					CAPTURED_NEARBY_NAMES_KEY,
					capturedNames,
					false
			);
		}
	}

	private void refreshConfiguredPlayerLists()
	{
		clientThread.invoke(() ->
		{
			Set<String> bannedNames =
					getBannedNames();

			for (String bannedName : bannedNames)
			{
				cancelLookup(bannedName);
				thievingLevels.remove(bannedName);
				lowLevelMembers.remove(bannedName);
			}

			FriendsChatManager friendsChatManager =
					client.getFriendsChatManager();

			if (friendsChatManager != null)
			{
				FriendsChatMember[] members =
						friendsChatManager.getMembers();

				if (members != null)
				{
					for (FriendsChatMember member
							: members)
					{
						if (member == null)
						{
							continue;
						}

						String playerName =
								member.getName();

						String normalizedName =
								normalizeName(playerName);

						if (normalizedName.isEmpty())
						{
							continue;
						}

						currentMembers.add(
								normalizedName
						);

						if (bannedNames.contains(
								normalizedName
						))
						{
							cancelLookup(
									normalizedName
							);

							thievingLevels.remove(
									normalizedName
							);

							lowLevelMembers.remove(
									normalizedName
							);
						}
						else
						{
							queueLookup(playerName);
						}
					}
				}
			}

			applyLevelsToMemberList();
		});
	}

	private boolean shouldQueueJoinMessage(
			String normalizedName)
	{
		if (suppressJoinMessages
				|| !config.showLowLevelJoinMessage()
				|| getIgnoredNames().contains(
				normalizedName
		)
				|| getBannedNames().contains(
				normalizedName
		))
		{
			return false;
		}

		Instant lastMessageTime =
				lastJoinMessageTimes.get(
						normalizedName
				);

		return lastMessageTime == null
				|| Duration.between(
				lastMessageTime,
				Instant.now()
		).compareTo(
				JOIN_MESSAGE_COOLDOWN
		) >= 0;
	}

	private String findFriendsChatPlayerInMenu(
			MenuEntry[] menuEntries)
	{
		if (menuEntries == null)
		{
			return "";
		}

		for (MenuEntry menuEntry : menuEntries)
		{
			if (menuEntry == null)
			{
				continue;
			}

			String playerName =
					extractPlayerName(
							menuEntry.getTarget()
					);

			String normalizedName =
					normalizeName(playerName);

			if (!normalizedName.isEmpty()
					&& currentMembers.contains(
					normalizedName
			))
			{
				return playerName;
			}
		}

		return "";
	}

	private void queueCurrentMembersWhenAvailable()
	{
		clientThread.invokeLater(() ->
		{
			FriendsChatManager friendsChatManager =
					client.getFriendsChatManager();

			if (friendsChatManager == null)
			{
				return true;
			}

			FriendsChatMember[] members =
					friendsChatManager.getMembers();

			if (members == null
					|| members.length == 0)
			{
				return false;
			}

			Set<String> loadedMembers =
					ConcurrentHashMap.newKeySet();

			for (FriendsChatMember member : members)
			{
				if (member == null)
				{
					continue;
				}

				String playerName =
						member.getName();

				String normalizedName =
						normalizeName(playerName);

				if (normalizedName.isEmpty())
				{
					continue;
				}

				loadedMembers.add(normalizedName);
				currentMembers.add(normalizedName);

				boolean bannedPlayer =
						isBannedPlayer(playerName);

				if (bannedPlayer)
				{
					cancelLookup(normalizedName);
					thievingLevels.remove(
							normalizedName
					);
					lowLevelMembers.remove(
							normalizedName
					);

					if (config
							.showBannedJoinMessage())
					{
						showJoinNotification(
								normalizedName,
								playerName,
								"(Banned player)"
						);
					}
				}

				LowLevelMember lowLevelMember =
						lowLevelMembers.get(
								normalizedName
						);

				if (lowLevelMember != null)
				{
					lowLevelMember.setDepartedAt(
							null
					);
				}

				if (!bannedPlayer)
				{
					queueLookup(playerName);
				}
			}

			removeCurrentMembersFromCapturedList(
					loadedMembers
			);

			for (String normalizedName
					: new ArrayList<>(
					currentMembers
			))
			{
				if (!loadedMembers.contains(
						normalizedName
				))
				{
					currentMembers.remove(
							normalizedName
					);

					markMemberDeparted(
							normalizedName
					);
				}
			}

			applyLevelsToMemberList();

			suppressJoinMessages = false;

			return true;
		});
	}

	private void cancelLookup(
			String normalizedName)
	{
		pendingJoinMessages.remove(
				normalizedName
		);

		pendingLookups.remove(normalizedName);
		displayNames.remove(normalizedName);

		lookupQueue.removeIf(
				normalizedName::equals
		);
	}

	private void queueLookup(String playerName)
	{
		if (playerName == null
				|| playerName.trim().isEmpty())
		{
			return;
		}

		String normalizedName =
				normalizeName(playerName);

		if (normalizedName.isEmpty()
				|| isBannedPlayer(playerName))
		{
			return;
		}

		Instant now = Instant.now();

		Instant lastLookup =
				lastLookupTimes.get(
						normalizedName
				);

		if (lastLookup != null
				&& Duration.between(
				lastLookup,
				now
		).compareTo(
				LOOKUP_COOLDOWN
		) < 0)
		{
			updateLowLevelMemberFromCache(
					normalizedName,
					playerName
			);

			applyLevelsToMemberList();

			return;
		}

		if (!pendingLookups.add(
				normalizedName
		))
		{
			return;
		}

		lastLookupTimes.put(
				normalizedName,
				now
		);

		displayNames.put(
				normalizedName,
				Text.toJagexName(playerName)
		);

		lookupQueue.add(normalizedName);
	}

	private void startLookup(
			String normalizedName)
	{
		String playerName =
				displayNames.getOrDefault(
						normalizedName,
						normalizedName
				);

		if (isBannedPlayer(playerName))
		{
			cancelLookup(normalizedName);
			return;
		}

		hiscoreClient.lookupAsync(
				playerName,
				HiscoreEndpoint.NORMAL
		).whenComplete((result, throwable) ->
		{
			pendingLookups.remove(normalizedName);
			displayNames.remove(normalizedName);

			if (throwable != null)
			{
				log.debug(
						"Unable to retrieve Hiscores for {}",
						playerName,
						throwable
				);

				return;
			}

			handleHiscoreResult(
					normalizedName,
					playerName,
					result
			);
		});
	}

	private void handleHiscoreResult(
			String normalizedName,
			String playerName,
			HiscoreResult result)
	{
		if (isBannedPlayer(playerName))
		{
			thievingLevels.remove(normalizedName);
			lowLevelMembers.remove(normalizedName);

			clientThread.invoke(
					this::applyLevelsToMemberList
			);

			return;
		}

		if (result == null)
		{
			return;
		}

		Skill thieving =
				result.getSkill(
						HiscoreSkill.THIEVING
				);

		if (thieving == null
				|| thieving.getLevel() < 1)
		{
			return;
		}

		int level = thieving.getLevel();

		thievingLevels.put(
				normalizedName,
				level
		);

		showLowLevelJoinMessage(
				normalizedName,
				playerName,
				level
		);

		if (level < REQUIRED_THIEVING_LEVEL)
		{
			Instant departedAt =
					currentMembers.contains(
							normalizedName
					)
							? null
							: Instant.now();

			lowLevelMembers.compute(
					normalizedName,
					(key, existing) ->
					{
						if (existing == null)
						{
							return new LowLevelMember(
									Text.toJagexName(
											playerName
									),
									departedAt
							);
						}

						existing.setName(
								Text.toJagexName(
										playerName
								)
						);

						existing.setDepartedAt(
								departedAt
						);

						return existing;
					});
		}
		else
		{
			lowLevelMembers.remove(
					normalizedName
			);
		}

		clientThread.invoke(
				this::applyLevelsToMemberList
		);
	}

	private void showLowLevelJoinMessage(
			String normalizedName,
			String playerName,
			int thievingLevel)
	{
		if (!pendingJoinMessages.remove(
				normalizedName
		))
		{
			return;
		}

		if (!config.showLowLevelJoinMessage()
				|| thievingLevel
				>= REQUIRED_THIEVING_LEVEL
				|| getIgnoredNames().contains(
				normalizedName
		)
				|| getBannedNames().contains(
				normalizedName
		)
				|| !currentMembers.contains(
				normalizedName
		))
		{
			return;
		}

		showJoinNotification(
				normalizedName,
				playerName,
				"("
						+ thievingLevel
						+ " Thieving)"
		);
	}

	private void showJoinNotification(
			String normalizedName,
			String playerName,
			String notificationText)
	{
		if (!currentMembers.contains(
				normalizedName
		))
		{
			return;
		}

		Instant now = Instant.now();

		Instant lastMessageTime =
				lastJoinMessageTimes.get(
						normalizedName
				);

		if (lastMessageTime != null
				&& Duration.between(
				lastMessageTime,
				now
		).compareTo(
				JOIN_MESSAGE_COOLDOWN
		) < 0)
		{
			return;
		}

		lastJoinMessageTimes.put(
				normalizedName,
				now
		);

		String message =
				new ChatMessageBuilder()
						.append(
								Text.toJagexName(
										playerName
								)
						)
						.append(
								" has joined the channel - "
						)
						.append(
								Color.RED,
								notificationText
						)
						.build();

		clientThread.invoke(() ->
				client.addChatMessage(
						ChatMessageType
								.FRIENDSCHATNOTIFICATION,
						"",
						message,
						""
				)
		);
	}

	private boolean isBannedPlayer(
			String playerName)
	{
		return getBannedNames().contains(
				normalizeName(playerName)
		);
	}

	private void updateLowLevelMemberFromCache(
			String normalizedName,
			String playerName)
	{
		if (isBannedPlayer(playerName))
		{
			return;
		}

		Integer level =
				thievingLevels.get(
						normalizedName
				);

		if (level == null
				|| level
				>= REQUIRED_THIEVING_LEVEL)
		{
			return;
		}

		lowLevelMembers.compute(
				normalizedName,
				(key, existing) ->
				{
					if (existing == null)
					{
						return new LowLevelMember(
								Text.toJagexName(
										playerName
								),
								currentMembers.contains(
										normalizedName
								)
										? null
										: Instant.now()
						);
					}

					existing.setName(
							Text.toJagexName(
									playerName
							)
					);

					if (currentMembers.contains(
							normalizedName
					))
					{
						existing.setDepartedAt(
								null
						);
					}

					return existing;
				});
	}

	private String extractPlayerName(
			String target)
	{
		if (target == null
				|| target.trim().isEmpty())
		{
			return "";
		}

		String withoutFormatting =
				removePluginFormatting(target);

		return Text.toJagexName(
				Text.removeTags(
						withoutFormatting
				)
		);
	}

	private void markMemberDeparted(
			String normalizedName)
	{
		LowLevelMember member =
				lowLevelMembers.get(
						normalizedName
				);

		if (member != null
				&& !member.isDeparted())
		{
			member.setDepartedAt(
					Instant.now()
			);
		}
	}

	private void removeExpiredDepartedMembers()
	{
		Instant now = Instant.now();

		lowLevelMembers.entrySet()
				.removeIf(entry ->
				{
					LowLevelMember member =
							entry.getValue();

					Instant departedAt =
							member.getDepartedAt();

					return departedAt != null
							&& Duration.between(
							departedAt,
							now
					).compareTo(
							DEPARTED_DISPLAY_DURATION
					) >= 0;
				});
	}

	List<OvertimeMember> getOvertimeMembers()
	{
		if (!isInFriendsChat()
				|| !isInRoguesCastleRegion())
		{
			return new ArrayList<>();
		}

		Instant now = Instant.now();
		Duration threshold =
				Duration.ofMinutes(
						config.overtimeMinutes()
				);

		List<OvertimeMember> members =
				new ArrayList<>();

		Set<String> overtimeWhitelistNames =
				getOvertimeWhitelistNames();

		for (Map.Entry<String, NearbyMemberTracker> entry
				: nearbyMemberTrackers.entrySet())
		{
			String normalizedName = entry.getKey();
			NearbyMemberTracker tracker = entry.getValue();

			if (!currentMembers.contains(normalizedName)
					|| overtimeWhitelistNames.contains(
					normalizedName
			))
			{
				continue;
			}

			Duration elapsed = tracker.getElapsed(now);

			if (elapsed.compareTo(threshold) < 0)
			{
				continue;
			}

			members.add(
					new OvertimeMember(
							tracker.getDisplayName(),
							elapsed,
							tracker.isPaused()
					)
			);
		}

		members.sort(
				Comparator.comparing(
								OvertimeMember::getElapsed
						).reversed()
						.thenComparing(
								OvertimeMember::getName,
								String.CASE_INSENSITIVE_ORDER
						)
		);

		return members;
	}

	List<LowLevelMember> getLowLevelMembers()
	{
		Set<String> ignoredNames =
				getIgnoredNames();

		Set<String> bannedNames =
				getBannedNames();

		List<LowLevelMember> members =
				new ArrayList<>();

		for (Map.Entry<String, LowLevelMember> entry
				: lowLevelMembers.entrySet())
		{
			if (!ignoredNames.contains(
					entry.getKey()
			)
					&& !bannedNames.contains(
					entry.getKey()
			))
			{
				members.add(entry.getValue());
			}
		}

		members.sort(
				Comparator.comparing(
						LowLevelMember::isDeparted
				).thenComparing(
						LowLevelMember::getName,
						String.CASE_INSENSITIVE_ORDER
				)
		);

		return members;
	}

	private Set<String> getIgnoredNames()
	{
		return parseConfiguredNames(
				config.ignoredNames()
		);
	}

	private Set<String> getBannedNames()
	{
		return parseConfiguredNames(
				config.bannedNames()
		);
	}

	private Set<String> getOvertimeWhitelistNames()
	{
		return parseConfiguredNames(
				config.overtimeWhitelistNames()
		);
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
						configuredNames.split(
								"[,\\r\\n]+"
						)
				)
				.map(String::trim)
				.map(this::normalizeName)
				.filter(name -> !name.isEmpty())
				.forEach(names::add);

		return names;
	}

	private void applyLevelsToMemberList()
	{
		Widget chatList =
				client.getWidget(
						InterfaceID
								.ChatchannelCurrent
								.LIST
				);

		if (chatList == null
				|| chatList.getChildren() == null)
		{
			return;
		}

		Set<String> ignoredNames =
				getIgnoredNames();

		Set<String> bannedNames =
				getBannedNames();

		Widget[] children =
				chatList.getChildren();

		List<FriendsChatRow> rows =
				new ArrayList<>();

		for (int i = 0;
		     i < children.length;
		     i += 3)
		{
			Widget nameWidget = children[i];

			if (nameWidget == null)
			{
				continue;
			}

			String originalText =
					removePluginFormatting(
							nameWidget.getText()
					);

			String playerName =
					Text.toJagexName(
							Text.removeTags(
									originalText
							)
					);

			String normalizedName =
					normalizeName(playerName);

			int priority =
					getFriendsChatSortPriority(
							normalizedName,
							ignoredNames,
							bannedNames
					);

			rows.add(
					new FriendsChatRow(
							getRowWidgets(
									children,
									i
							),
							nameWidget.getOriginalY(),
							priority
					)
			);

			if (bannedNames.contains(
					normalizedName
			))
			{
				cancelLookup(normalizedName);

				thievingLevels.remove(
						normalizedName
				);

				lowLevelMembers.remove(
						normalizedName
				);

				nameWidget.setText(
						RED_TEXT_OPEN
								+ originalText
								+ BANNED_MEMBER_TEXT
								+ TEXT_CLOSE
				);

				continue;
			}

			Integer thievingLevel =
					thievingLevels.get(
							normalizedName
					);

			if (thievingLevel == null)
			{
				nameWidget.setText(
						originalText
				);

				continue;
			}

			boolean showGreen =
					thievingLevel
							>= REQUIRED_THIEVING_LEVEL
							|| ignoredNames.contains(
							normalizedName
					);

			String levelMarker =
					showGreen
							? GREEN_LEVEL_MARKER
							: RED_LEVEL_MARKER;

			nameWidget.setText(
					originalText
							+ levelMarker
							+ thievingLevel
							+ LEVEL_SUFFIX
			);
		}

		sortFriendsChatRows(rows);
	}

	private int getFriendsChatSortPriority(
			String normalizedName,
			Set<String> ignoredNames,
			Set<String> bannedNames)
	{
		if (bannedNames.contains(normalizedName))
		{
			return 0;
		}

		Integer thievingLevel =
				thievingLevels.get(normalizedName);

		if (thievingLevel != null
				&& thievingLevel < REQUIRED_THIEVING_LEVEL
				&& !ignoredNames.contains(normalizedName))
		{
			return 1;
		}

		return 2;
	}

	private List<Widget> getRowWidgets(
			Widget[] children,
			int startIndex)
	{
		List<Widget> rowWidgets =
				new ArrayList<>(3);

		for (int i = startIndex;
		     i < children.length
					 && i < startIndex + 3;
		     i++)
		{
			Widget widget = children[i];

			if (widget != null)
			{
				rowWidgets.add(widget);
			}
		}

		return rowWidgets;
	}

	private void sortFriendsChatRows(
			List<FriendsChatRow> rows)
	{
		if (rows.size() < 2)
		{
			return;
		}

		List<Integer> rowPositions =
				new ArrayList<>(rows.size());

		for (FriendsChatRow row : rows)
		{
			rowPositions.add(row.getBaseY());
		}

		rowPositions.sort(Integer::compareTo);

		rows.sort(
				Comparator.comparingInt(
						FriendsChatRow::getPriority
				)
		);

		for (int i = 0; i < rows.size(); i++)
		{
			rows.get(i).moveTo(
					rowPositions.get(i)
			);
		}
	}

	private void removeLevelsFromMemberList()
	{
		Widget chatList =
				client.getWidget(
						InterfaceID
								.ChatchannelCurrent
								.LIST
				);

		if (chatList == null
				|| chatList.getChildren() == null)
		{
			return;
		}

		Widget[] children =
				chatList.getChildren();

		for (int i = 0;
		     i < children.length;
		     i += 3)
		{
			Widget nameWidget = children[i];

			if (nameWidget != null)
			{
				nameWidget.setText(
						removePluginFormatting(
								nameWidget.getText()
						)
				);
			}
		}
	}

	private String removePluginFormatting(
			String text)
	{
		if (text == null)
		{
			return "";
		}

		String withoutLevel =
				removeLevelFromText(text);

		String plainText =
				Text.removeTags(withoutLevel);

		if (plainText.endsWith(
				BANNED_MEMBER_TEXT
		))
		{
			plainText = plainText.substring(
					0,
					plainText.length()
							- BANNED_MEMBER_TEXT.length()
			);
		}

		return plainText;
	}

	private String removeLevelFromText(
			String text)
	{
		if (text == null)
		{
			return "";
		}

		int greenIndex =
				text.indexOf(
						GREEN_LEVEL_MARKER
				);

		int redIndex =
				text.indexOf(
						RED_LEVEL_MARKER
				);

		int markerIndex;

		if (greenIndex == -1)
		{
			markerIndex = redIndex;
		}
		else if (redIndex == -1)
		{
			markerIndex = greenIndex;
		}
		else
		{
			markerIndex = Math.min(
					greenIndex,
					redIndex
			);
		}

		return markerIndex == -1
				? text
				: text.substring(
				0,
				markerIndex
		);
	}

	private String normalizeName(
			String playerName)
	{
		if (playerName == null)
		{
			return "";
		}

		return Text.toJagexName(
				Text.removeTags(playerName)
		).toLowerCase(Locale.ROOT);
	}


	static class FriendsChatRow
	{
		private final List<Widget> widgets;
		private final int baseY;
		private final int priority;

		FriendsChatRow(
				List<Widget> widgets,
				int baseY,
				int priority)
		{
			this.widgets = widgets;
			this.baseY = baseY;
			this.priority = priority;
		}

		int getBaseY()
		{
			return baseY;
		}

		int getPriority()
		{
			return priority;
		}

		void moveTo(int targetY)
		{
			for (Widget widget : widgets)
			{
				int offset =
						widget.getOriginalY()
								- baseY;

				widget.setOriginalY(
						targetY + offset
				);

				widget.revalidate();
			}
		}
	}

	private static class NearbyMemberTracker
	{
		private String displayName;
		private Instant activeSince;
		private Duration accumulatedActiveTime = Duration.ZERO;
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

			accumulatedActiveTime =
					accumulatedActiveTime.plus(
							Duration.between(
									activeSince,
									now
							)
					);
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
					Duration.between(activeSince, now)
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

	static class OvertimeMember
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

	static class LowLevelMember
	{
		private String name;
		private volatile Instant departedAt;

		LowLevelMember(
				String name,
				Instant departedAt)
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

		void setDepartedAt(
				Instant departedAt)
		{
			this.departedAt = departedAt;
		}

		boolean isDeparted()
		{
			return departedAt != null;
		}
	}
}