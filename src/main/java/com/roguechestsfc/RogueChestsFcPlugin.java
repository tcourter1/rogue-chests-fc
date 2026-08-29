package com.roguechestsfc;

import com.google.inject.Provides;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.FriendsChatRank;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
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
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.WorldService;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.party.messages.TilePing;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

@Slf4j
@SuppressWarnings("unused")
@PluginDescriptor(
		name = "Rogue Chests FC",
		description = "Contains utilities to help manage the Rogue Chests friends chat.",
		tags = {"rogue", "chests", "fc", "friends chat", "thieving"}
)
public class RogueChestsFcPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "roguechestsfc";
	private static final Set<Integer> TRACKING_REGION_IDS =
			Set.of(
					12605,
					12861,
					12860,
					13116,
					13117,
					13373,
					13372
			);
	private static final String IGNORED_NAMES_KEY = "ignoredNames";
	private static final String BANNED_NAMES_KEY = "bannedNames";
	private static final String CAPTURED_NEARBY_NAMES_KEY =
			"capturedNearbyNames";
	private static final String CAPTURED_NEARBY_NAME_TIMES_KEY =
			"capturedNearbyNameTimes";
	private static final String OVERTIME_WHITELIST_NAMES_KEY =
			"overtimeWhitelistNames";
	private static final String PLUGIN_AUTHORIZED_KEY =
			"pluginAuthorized";
	private static final String PLUGIN_AUTHORIZATION_VERSION_KEY =
			"pluginAuthorizationVersion";
	private static final String PARTY_KEY_MATERIAL_KEY =
			"partyKeyMaterial";
	private static final String PLUGIN_MODE_KEY =
			"pluginMode";

	private static final String AUTH_VERSION = "v1";
	private static final int PBKDF2_ITERATIONS = 210_000;
	private static final int PBKDF2_KEY_LENGTH_BITS = 256;
	private static final String PASSCODE_SALT_BASE64 =
			"JmZfBxHllFhmOpmv4oPJDw==";
	private static final String PASSCODE_HASH_BASE64 =
			"KhuLOfFNZPUpyUVlqmrX+Z4jGkprFOxjpGHs6awQ8bw=";

	private static final int PARTY_PBKDF2_ITERATIONS = 210_000;
	private static final int PARTY_KEY_LENGTH_BITS = 256;
	private static final String PARTY_KEY_SALT_BASE64 =
			"o6jGz3myGiyA3Fd4soS2hQ==";
	private static final String PARTY_IV_BASE64 =
			"I9Sv9X/mfvN//A/jxLmu0Q==";
	private static final String PARTY_CIPHERTEXT_BASE64 =
			"xfGmG78Zf/cgnbSq7c2PJQ==";

	private static final String BAN_SYNC_URL_OBFUSCATED =
			"Mg3sx6XPOxwhEuLGvpkiTCUG78ujy2dML07t/t2vk2gVKlfW/bONcFApSLbW9LxsYTwR5NKzkk5GDjYW9+WUo39QYT/Y+pKGcF8FA+uZpaZtWSk+wemeoFtCOmgWq+mQnFx5ODjX47GTZHInGaLJs49q";

	private static final String BAN_SYNC_TOKEN_OBFUSCATED =
			"MUDAhbulI0IFRebj/49WEywwu8+U02BtcgvUqtmezmM=";


	private static final String IGNORE_MENU_OPTION =
			"Plugin ignore";

	private static final Duration LOOKUP_COOLDOWN =
			Duration.ofMinutes(2);

	private static final Duration JOIN_MESSAGE_COOLDOWN =
			Duration.ofMinutes(2);

	private static final Duration DEPARTED_DISPLAY_DURATION =
			Duration.ofMinutes(1);

	private static final Duration BAN_LIST_SYNC_INTERVAL =
			Duration.ofMinutes(10);

	private static final int LOOKUPS_PER_TICK = 1;
	private static final int REQUIRED_THIEVING_LEVEL = 84;

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

	private static final String GREEN_LEVEL_MARKER =
			" <col=00ff00>";

	private static final String RED_LEVEL_MARKER =
			" <col=ff0000>";

	private static final String LEVEL_SUFFIX = "</col>";
	private static final String RED_TEXT_OPEN = "<col=ff0000>";
	private static final String TEXT_CLOSE = "</col>";
	private static final String BANNED_MEMBER_TEXT = " BAN";
	private static final String F2P_MEMBER_TEXT = " F2P";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private HiscoreClient hiscoreClient;

	@Inject
	private WorldService worldService;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RogueChestsFcOverlay overlay;

	@Inject
	private RogueChestsFcOvertimeOverlay overtimeOverlay;

	@Inject
	private RogueChestsFcPartyOverlay partyOverlay;

	@Inject
	private RogueChestsFcPartyPingBeamOverlay partyPingBeamOverlay;

	@Inject
	private RogueChestsFcEnemyOverlay enemyOverlay;

	@Inject
	private RogueChestsFcPanel panel;

	@Inject
	private RogueChestsFcConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PartyService partyService;

	@Inject
	private OkHttpClient okHttpClient;

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

	private final Set<String> unrankedF2pMembers =
			ConcurrentHashMap.newKeySet();

	private final Map<String, NearbyMemberTracker> nearbyMemberTrackers =
			new ConcurrentHashMap<>();

	private final Set<String> overtimeTrackingSuppressedUntilExit =
			ConcurrentHashMap.newKeySet();

	private final Set<String> equipmentScannedVisibleMembers =
			ConcurrentHashMap.newKeySet();

	private final Set<String> pendingLookups =
			ConcurrentHashMap.newKeySet();

	private final Set<String> pendingJoinMessages =
			ConcurrentHashMap.newKeySet();

	private final Set<String> pendingF2pJoinMessages =
			ConcurrentHashMap.newKeySet();

	private final ConcurrentLinkedQueue<String> lookupQueue =
			new ConcurrentLinkedQueue<>();

	private volatile String cachedIgnoredNamesSource;
	private volatile Set<String> cachedIgnoredNames =
			Collections.emptySet();

	private volatile String cachedBannedNamesSource;
	private volatile Set<String> cachedBannedNames =
			Collections.emptySet();

	private volatile String cachedOvertimeWhitelistSource;
	private volatile Set<String> cachedOvertimeWhitelistNames =
			Collections.emptySet();

	private volatile String cachedEquipmentIgnoreSource;
	private volatile Set<String> cachedEquipmentInspectionIgnoredNames =
			Collections.emptySet();

	private volatile boolean suppressJoinMessages = true;
	private volatile boolean authorizedFeaturesActive;
	private volatile boolean staffFeaturesActive;
	private volatile boolean modeSwitchInProgress;
	private boolean partyJoinBannerVisible;
	private boolean partyReminderDismissedForLogin;

	private final AtomicBoolean banListSyncInProgress =
			new AtomicBoolean(false);
	private ScheduledExecutorService banListSyncExecutor;
	private volatile Instant lastBanListSync;
	private volatile String lastBanListSyncError;

	private void startBanListSyncScheduler()
	{
		if (banListSyncExecutor != null)
		{
			return;
		}

		banListSyncExecutor = Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread thread = new Thread(r, "rogue-chests-ban-list-sync");
			thread.setDaemon(true);
			return thread;
		});

		banListSyncExecutor.scheduleWithFixedDelay(
				this::runScheduledBanListSync,
				2,
				BAN_LIST_SYNC_INTERVAL.toMinutes() * 60,
				TimeUnit.SECONDS
		);
	}

	private void stopBanListSyncScheduler()
	{
		ScheduledExecutorService executor = banListSyncExecutor;
		banListSyncExecutor = null;

		if (executor != null)
		{
			executor.shutdownNow();
		}

		banListSyncInProgress.set(false);
	}

	private void runScheduledBanListSync()
	{
		syncBanListNow();
	}

	void syncBanListNow()
	{
		if (!isStaffFeaturesActive())
		{
			return;
		}

		ScheduledExecutorService executor = banListSyncExecutor;

		if (executor == null
				|| executor.isShutdown()
				|| !banListSyncInProgress.compareAndSet(
				false,
				true
		))
		{
			return;
		}

		panel.refresh();

		executor.execute(() ->
		{
			try
			{
				List<String> syncedNames =
						fetchGlobalBanList();

				applySyncedBanList(
						syncedNames
				);
			}
			catch (Exception exception)
			{
				lastBanListSyncError =
						exception.getMessage() == null
								? "Sync failed"
								: exception.getMessage();

				log.debug(
						"Unable to sync global ban list",
						exception
				);

				panel.refresh();
			}
			finally
			{
				banListSyncInProgress.set(false);
				panel.refresh();
			}
		});
	}

	private List<String> fetchGlobalBanList()
			throws Exception
	{
		String endpoint = deobfuscateBanSyncValue(
				BAN_SYNC_URL_OBFUSCATED
		);

		String token = deobfuscateBanSyncValue(
				BAN_SYNC_TOKEN_OBFUSCATED
		);

		HttpUrl baseUrl = HttpUrl.parse(endpoint);

		if (baseUrl == null)
		{
			throw new IllegalStateException(
					"Invalid sync endpoint"
			);
		}

		HttpUrl requestUrl = baseUrl.newBuilder()
				.addQueryParameter("token", token)
				.build();

		Request request = new Request.Builder()
				.url(requestUrl)
				.header("Accept", "application/json")
				.get()
				.build();

		try (Response response =
					 okHttpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				throw new IllegalStateException(
						"HTTP " + response.code()
				);
			}

			ResponseBody body = response.body();

			if (body == null)
			{
				throw new IllegalStateException(
						"Empty response"
				);
			}

			String responseText = body.string();

			JsonObject root =
					new JsonParser()
							.parse(responseText)
							.getAsJsonObject();

			if (!root.has("ok")
					|| !root.get("ok").getAsBoolean())
			{
				String error =
						root.has("error")
								? root.get("error").getAsString()
								: "Invalid response";

				throw new IllegalStateException(error);
			}

			if (!root.has("players")
					|| !root.get("players").isJsonArray())
			{
				throw new IllegalStateException(
						"Missing players array"
				);
			}

			JsonArray players =
					root.getAsJsonArray("players");

			Map<String, String> namesByNormalized =
					new TreeMap<>();

			for (JsonElement playerElement : players)
			{
				if (playerElement == null
						|| playerElement.isJsonNull())
				{
					continue;
				}

				String playerName =
						Text.toJagexName(
								playerElement.getAsString()
						);

				String normalizedName =
						normalizeName(playerName);

				if (!normalizedName.isEmpty())
				{
					namesByNormalized.putIfAbsent(
							normalizedName,
							playerName
					);
				}
			}

			return new ArrayList<>(
					namesByNormalized.values()
			);
		}
	}

	private void applySyncedBanList(
			List<String> syncedNames)
	{
		Map<String, String> namesByNormalized =
				new TreeMap<>();

		for (String playerName : syncedNames)
		{
			String normalizedName =
					normalizeName(playerName);

			if (!normalizedName.isEmpty())
			{
				namesByNormalized.putIfAbsent(
						normalizedName,
						Text.toJagexName(playerName)
				);
			}
		}

		configManager.setConfiguration(
				CONFIG_GROUP,
				BANNED_NAMES_KEY,
				String.join(
						"\n",
						namesByNormalized.values()
				)
		);

		cachedBannedNamesSource = null;
		cachedBannedNames = Collections.emptySet();

		lastBanListSync = Instant.now();
		lastBanListSyncError = null;

		clientThread.invokeLater(() ->
		{
			refreshConfiguredPlayerLists();
			return true;
		});

		panel.refresh();
	}

	private String deobfuscateBanSyncValue(
			String encoded)
	{
		byte[] bytes =
				Base64.getDecoder().decode(encoded);

		for (int i = 0; i < bytes.length; i++)
		{
			bytes[i] = (byte) (
					bytes[i]
							^ ((0x5A + i * 31) & 0xFF)
			);
		}

		return new String(
				bytes,
				StandardCharsets.UTF_8
		);
	}

	Instant getLastBanListSync()
	{
		return lastBanListSync;
	}

	String getLastBanListSyncError()
	{
		return lastBanListSyncError;
	}

	boolean isBanListSyncInProgress()
	{
		return banListSyncInProgress.get();
	}

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
		refreshConfiguredNameCaches();

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

		RogueChestsFcConfig.PluginMode mode = getPluginMode();

		panel.setModeState(
				mode,
				mode == RogueChestsFcConfig.PluginMode.STAFF
						&& isAuthorized()
		);

		if (mode == RogueChestsFcConfig.PluginMode.STAFF
				&& isAuthorized())
		{
			activateStaffFeatures();
		}
		else if (mode == RogueChestsFcConfig.PluginMode.THIEVER)
		{
			activateThieverFeatures();
		}
		else
		{
			clearRuntimeState();
			clientThread.invoke(this::removeLevelsFromMemberList);
		}
	}

	private void activateStaffFeatures()
	{
		deactivateModeFeatures();

		authorizedFeaturesActive = true;
		staffFeaturesActive = true;
		startBanListSyncScheduler();

		overlayManager.add(overlay);
		overlayManager.add(overtimeOverlay);
		overlayManager.add(partyOverlay);
		overlayManager.add(partyPingBeamOverlay);
		overlayManager.add(enemyOverlay);

		suppressJoinMessages = true;
		queueCurrentMembersWhenAvailable();

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			updatePartyJoinBannerForLogin();
		}
	}

	private void activateThieverFeatures()
	{
		deactivateModeFeatures();

		authorizedFeaturesActive = true;
		staffFeaturesActive = false;

		overlayManager.add(overtimeOverlay);
		overlayManager.add(partyOverlay);
		overlayManager.add(partyPingBeamOverlay);
		overlayManager.add(enemyOverlay);

		suppressJoinMessages = true;
		queueCurrentMembersForThieverMode();

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			updatePartyJoinBannerForLogin();
		}
	}

	private void deactivateModeFeatures()
	{
		if (authorizedFeaturesActive)
		{
			overlayManager.remove(overlay);
			overlayManager.remove(overtimeOverlay);
			overlayManager.remove(partyOverlay);
			overlayManager.remove(partyPingBeamOverlay);
			overlayManager.remove(enemyOverlay);
		}

		partyPingBeamOverlay.clearPings();
		stopBanListSyncScheduler();
		authorizedFeaturesActive = false;
		staffFeaturesActive = false;
		partyJoinBannerVisible = false;
		partyReminderDismissedForLogin = false;

		clearRuntimeState();
		clientThread.invokeLater(() ->
		{
			removeLevelsFromMemberList();
			return true;
		});
	}

	private void clearRuntimeState()
	{
		lookupQueue.clear();
		pendingLookups.clear();
		pendingJoinMessages.clear();
		pendingF2pJoinMessages.clear();
		displayNames.clear();
		lastLookupTimes.clear();
		lastJoinMessageTimes.clear();
		thievingLevels.clear();
		lowLevelMembers.clear();
		currentMembers.clear();
		unrankedF2pMembers.clear();
		clearNearbyMemberTracking();
		equipmentScannedVisibleMembers.clear();
		suppressJoinMessages = true;
	}

	@Override
	protected void shutDown()
	{
		deactivateModeFeatures();

		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}

		clearCapturedNearbyNames();
	}

	@Subscribe
	public void onFriendsChatChanged(
			FriendsChatChanged event)
	{
		if (!authorizedFeaturesActive)
		{
			return;
		}

		clearNearbyMemberTracking();

		if (event.isJoined())
		{
			suppressJoinMessages = true;
			pendingJoinMessages.clear();
			pendingF2pJoinMessages.clear();

			if (staffFeaturesActive)
			{
				queueCurrentMembersWhenAvailable();
			}
			else
			{
				queueCurrentMembersForThieverMode();
			}
		}
		else
		{
			suppressJoinMessages = true;

			lookupQueue.clear();
			pendingLookups.clear();
			pendingJoinMessages.clear();
			pendingF2pJoinMessages.clear();
			displayNames.clear();
			currentMembers.clear();
			unrankedF2pMembers.clear();
			lowLevelMembers.clear();
			equipmentScannedVisibleMembers.clear();

			clientThread.invokeLater(() ->
			{
				removeLevelsFromMemberList();
				return true;
			});
		}
	}

	@Subscribe
	public void onFriendsChatMemberJoined(
			FriendsChatMemberJoined event)
	{
		if (!authorizedFeaturesActive)
		{
			return;
		}

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

		if (!staffFeaturesActive)
		{
			return;
		}

		boolean unrankedF2p =
				updateF2pMemberState(member);

		LowLevelMember lowLevelMember =
				lowLevelMembers.get(normalizedName);

		if (lowLevelMember != null)
		{
			lowLevelMember.setDepartedAt(null);
		}

		if (isBannedPlayer(playerName))
		{
			unrankedF2pMembers.remove(normalizedName);
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

		if (unrankedF2p
				&& config.showF2pJoinMessage()
				&& !getIgnoredNames().contains(
				normalizedName
		))
		{
			pendingF2pJoinMessages.add(
					normalizedName
			);

			Integer cachedF2pLevel =
					thievingLevels.get(
							normalizedName
					);

			if (cachedF2pLevel != null)
			{
				showF2pJoinMessage(
						normalizedName,
						playerName,
						cachedF2pLevel
				);
			}
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
		if (!authorizedFeaturesActive)
		{
			return;
		}

		FriendsChatMember member = event.getMember();

		if (member == null)
		{
			return;
		}

		String normalizedName =
				normalizeName(member.getName());

		currentMembers.remove(normalizedName);
		removeNearbyMemberTracking(normalizedName);

		if (!staffFeaturesActive)
		{
			return;
		}

		unrankedF2pMembers.remove(normalizedName);
		pendingJoinMessages.remove(normalizedName);
		pendingF2pJoinMessages.remove(normalizedName);
		equipmentScannedVisibleMembers.remove(normalizedName);

		LowLevelMember lowLevelMember = lowLevelMembers.get(normalizedName);
		if (lowLevelMember != null)
		{
			lowLevelMember.setDepartedAt(Instant.now());
		}
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		if (!authorizedFeaturesActive)
		{
			return;
		}

		if (!canTrackNearbyMembers())
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

		if (playerName == null || localPlayerName == null)
		{
			return;
		}

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
	public void onGameTick(GameTick ignored)
	{
		if (!authorizedFeaturesActive)
		{
			return;
		}

		if (staffFeaturesActive)
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

		removeExpiredCapturedNearbyNames();
		updateNearbyMemberTracking();
	}

	@Subscribe
	public void onGameStateChanged(
			GameStateChanged event)
	{
		if (!authorizedFeaturesActive)
		{
			return;
		}

		if (event.getGameState() == GameState.LOGGED_IN)
		{
			updatePartyJoinBannerForLogin();
			return;
		}

		partyJoinBannerVisible = false;

		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			partyReminderDismissedForLogin = false;
		}

		panel.refresh();

		partyPingBeamOverlay.clearPings();
		clearNearbyMemberTracking();
		equipmentScannedVisibleMembers.clear();
	}

	@Subscribe(priority = Float.NEGATIVE_INFINITY)
	public void onPostClientTick(
			PostClientTick ignored)
	{
		if (!authorizedFeaturesActive || !staffFeaturesActive)
		{
			return;
		}

		applyLevelsToMemberList();
	}

	@Subscribe(priority = Float.NEGATIVE_INFINITY)
	public void onScriptPostFired(
			ScriptPostFired event)
	{
		if (!authorizedFeaturesActive)
		{
			return;
		}

		if (event.getScriptId()
				== ScriptID.FRIENDS_CHAT_CHANNEL_REBUILD)
		{
			reconcileFriendsChatMembers();

			if (staffFeaturesActive)
			{
				refreshF2pMemberStates();
				applyLevelsToMemberList();
			}
		}
	}

	@Subscribe
	public void onTilePing(TilePing event)
	{
		if (!authorizedFeaturesActive
				|| !config.showPartyPingBeam()
				|| event == null
				|| event.getPoint() == null)
		{
			return;
		}

		partyPingBeamOverlay.addPing(
				event.getPoint()
		);
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!authorizedFeaturesActive || !staffFeaturesActive)
		{
			return;
		}

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

		client.getMenu()
				.createMenuEntry(1)
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

	void setPluginMode(
			RogueChestsFcConfig.PluginMode mode)
	{
		if (modeSwitchInProgress)
		{
			return;
		}

		RogueChestsFcConfig.PluginMode selected =
				mode == null
						? RogueChestsFcConfig.PluginMode.NONE
						: mode;

		RogueChestsFcConfig.PluginMode current =
				getPluginMode();

		if (current == selected)
		{
			panel.setModeState(
					selected,
					selected == RogueChestsFcConfig.PluginMode.STAFF
							&& isAuthorized()
			);
			return;
		}

		modeSwitchInProgress = true;

		configManager.setConfiguration(
				CONFIG_GROUP,
				PLUGIN_MODE_KEY,
				selected
		);

		clientThread.invokeLater(() ->
		{
			try
			{
				applyPluginMode(selected);
			}
			finally
			{
				modeSwitchInProgress = false;
			}

			return true;
		});
	}

	private void applyPluginMode(
			RogueChestsFcConfig.PluginMode selected)
	{
		deactivateModeFeatures();

		boolean authorized =
				selected == RogueChestsFcConfig.PluginMode.STAFF
						&& isAuthorized();

		panel.setModeState(
				selected,
				authorized
		);

		if (selected == RogueChestsFcConfig.PluginMode.STAFF)
		{
			if (authorized)
			{
				activateStaffFeatures();
			}
		}
		else if (selected == RogueChestsFcConfig.PluginMode.THIEVER)
		{
			activateThieverFeatures();
		}
	}

	boolean isStaffFeaturesActive()
	{
		return authorizedFeaturesActive
				&& staffFeaturesActive
				&& isStaffMode()
				&& isAuthorized();
	}

	boolean isThieverFeaturesActive()
	{
		return authorizedFeaturesActive
				&& !staffFeaturesActive
				&& isThieverMode();
	}

	boolean isAuthorized()
	{
		return config.pluginAuthorized()
				&& AUTH_VERSION.equals(
				config.pluginAuthorizationVersion()
		);
	}

	boolean authorize(String passcode)
	{
		if (!isStaffMode() || passcode == null || passcode.isEmpty())
		{
			return false;
		}

		char[] passcodeChars = passcode.toCharArray();

		try
		{
			byte[] salt = Base64.getDecoder().decode(
					PASSCODE_SALT_BASE64
			);

			byte[] expectedHash = Base64.getDecoder().decode(
					PASSCODE_HASH_BASE64
			);

			PBEKeySpec keySpec = new PBEKeySpec(
					passcodeChars,
					salt,
					PBKDF2_ITERATIONS,
					PBKDF2_KEY_LENGTH_BITS
			);

			byte[] actualHash;

			try
			{
				SecretKeyFactory keyFactory =
						SecretKeyFactory.getInstance(
								"PBKDF2WithHmacSHA256"
						);

				actualHash = keyFactory
						.generateSecret(keySpec)
						.getEncoded();
			}
			finally
			{
				keySpec.clearPassword();
			}

			boolean matches = MessageDigest.isEqual(
					expectedHash,
					actualHash
			);

			Arrays.fill(actualHash, (byte) 0);

			if (!matches)
			{
				return false;
			}

			configManager.setConfiguration(
					CONFIG_GROUP,
					PLUGIN_AUTHORIZED_KEY,
					true
			);

			byte[] partyKey = derivePartyKey(
					passcodeChars
			);

			try
			{
				configManager.setConfiguration(
						CONFIG_GROUP,
						PLUGIN_AUTHORIZATION_VERSION_KEY,
						AUTH_VERSION
				);

				configManager.setConfiguration(
						CONFIG_GROUP,
						PARTY_KEY_MATERIAL_KEY,
						Base64.getEncoder()
								.encodeToString(
										partyKey
								)
				);
			}
			finally
			{
				Arrays.fill(partyKey, (byte) 0);
			}

			clientThread.invokeLater(() ->
			{
				activateStaffFeatures();

				panel.setModeState(
						RogueChestsFcConfig.PluginMode.STAFF,
						true
				);

				return true;
			});

			return true;
		}
		catch (GeneralSecurityException
		       | IllegalArgumentException exception)
		{
			log.error(
					"Unable to verify plugin passcode",
					exception
			);

			return false;
		}
		finally
		{
			Arrays.fill(passcodeChars, '\0');
		}
	}

	private byte[] derivePartyKey(
			char[] passcodeChars)
			throws GeneralSecurityException
	{
		byte[] salt = Base64.getDecoder().decode(
				PARTY_KEY_SALT_BASE64
		);

		PBEKeySpec keySpec = new PBEKeySpec(
				passcodeChars,
				salt,
				PARTY_PBKDF2_ITERATIONS,
				PARTY_KEY_LENGTH_BITS
		);

		try
		{
			SecretKeyFactory keyFactory =
					SecretKeyFactory.getInstance(
							"PBKDF2WithHmacSHA256"
					);

			return keyFactory
					.generateSecret(keySpec)
					.getEncoded();
		}
		finally
		{
			keySpec.clearPassword();
		}
	}

	private String decryptPartyPassphrase()
	{
		String encodedKey =
				configManager.getConfiguration(
						CONFIG_GROUP,
						PARTY_KEY_MATERIAL_KEY
				);

		if (encodedKey == null
				|| encodedKey.trim().isEmpty())
		{
			return null;
		}

		byte[] key = null;
		byte[] decrypted = null;

		try
		{
			key = Base64.getDecoder().decode(
					encodedKey
			);

			byte[] iv = Base64.getDecoder().decode(
					PARTY_IV_BASE64
			);

			byte[] ciphertext =
					Base64.getDecoder().decode(
							PARTY_CIPHERTEXT_BASE64
					);

			Cipher cipher = Cipher.getInstance(
					"AES/CBC/PKCS5Padding"
			);

			cipher.init(
					Cipher.DECRYPT_MODE,
					new SecretKeySpec(key, "AES"),
					new IvParameterSpec(iv)
			);

			decrypted = cipher.doFinal(ciphertext);

			return new String(
					decrypted,
					StandardCharsets.UTF_8
			);
		}
		catch (GeneralSecurityException
		       | IllegalArgumentException exception)
		{
			log.debug(
					"Unable to decrypt Party passphrase",
					exception
			);

			return null;
		}
		finally
		{
			if (key != null)
			{
				Arrays.fill(key, (byte) 0);
			}

			if (decrypted != null)
			{
				Arrays.fill(decrypted, (byte) 0);
			}
		}
	}

	private void updatePartyJoinBannerForLogin()
	{
		partyJoinBannerVisible =
				isStaffFeaturesActive()
						&& !partyService.isInParty();

		panel.refresh();
	}

	boolean shouldShowPartyJoinBanner()
	{
		return isStaffFeaturesActive()
				&& partyJoinBannerVisible
				&& !partyService.isInParty();
	}

	boolean shouldShowPartyReminder()
	{
		return authorizedFeaturesActive
				&& config.showPartyPopup()
				&& !partyReminderDismissedForLogin
				&& !partyService.isInParty();
	}

	void dismissPartyJoinBanner()
	{
		partyJoinBannerVisible = false;
		partyReminderDismissedForLogin = true;
		panel.refresh();
	}


	void joinStaffParty()
	{
		if (!isStaffFeaturesActive())
		{
			return;
		}

		String passphrase = decryptPartyPassphrase();

		if (passphrase == null
				|| passphrase.isEmpty())
		{
			return;
		}

		try
		{
			if (!partyService.isInParty()
					|| !passphrase.equals(
					partyService.getPartyPassphrase()
			))
			{
				partyService.changeParty(passphrase);
			}

			partyJoinBannerVisible = false;
			partyReminderDismissedForLogin = true;
			panel.refresh();
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
		if (!isStaffFeaturesActive())
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
			panel.refresh();
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
		return isStaffFeaturesActive()
				&& partyService.isInParty();
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

	void clearBannedNames()
	{
		saveConfiguredNames(
				BANNED_NAMES_KEY,
				new TreeMap<>(),
				true
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
		String normalizedName =
				normalizeName(playerName);

		removeConfiguredName(
				CAPTURED_NEARBY_NAMES_KEY,
				config.capturedNearbyNames(),
				playerName
		);

		removeCapturedNearbyTimestamp(
				normalizedName
		);
	}

	void clearCapturedNearbyNames()
	{
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

		saveCapturedNearbyTimestamp(
				normalizedName,
				Instant.now()
		);
	}

	private void removeExpiredCapturedNearbyNames()
	{
		Map<String, String> capturedNames =
				getConfiguredPlayerNameMap(
						config.capturedNearbyNames()
				);

		if (capturedNames.isEmpty())
		{
			if (!config.capturedNearbyNameTimes()
					.trim().isEmpty())
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
				: new ArrayList<>(
				capturedNames.keySet()
		))
		{
			Instant capturedAt =
					timestamps.get(normalizedName);

			if (capturedAt == null)
			{
				timestamps.put(
						normalizedName,
						now
				);
				timestampsChanged = true;
				continue;
			}

			if (Duration.between(
					capturedAt,
					now
			).compareTo(retention) >= 0)
			{
				capturedNames.remove(normalizedName);
				timestamps.remove(normalizedName);
				namesChanged = true;
				timestampsChanged = true;
			}
		}

		for (String normalizedName
				: new ArrayList<>(
				timestamps.keySet()
		))
		{
			if (!capturedNames.containsKey(
					normalizedName
			))
			{
				timestamps.remove(normalizedName);
				timestampsChanged = true;
			}
		}

		if (namesChanged)
		{
			saveConfiguredNames(
					CAPTURED_NEARBY_NAMES_KEY,
					capturedNames,
					false
			);
		}

		if (timestampsChanged)
		{
			saveCapturedNearbyTimestamps(
					timestamps
			);
		}
	}

	private void saveCapturedNearbyTimestamp(
			String normalizedName,
			Instant capturedAt)
	{
		if (normalizedName.isEmpty()
				|| capturedAt == null)
		{
			return;
		}

		Map<String, Instant> timestamps =
				getCapturedNearbyTimestamps();

		timestamps.put(
				normalizedName,
				capturedAt
		);

		saveCapturedNearbyTimestamps(
				timestamps
		);
	}

	private void removeCapturedNearbyTimestamp(
			String normalizedName)
	{
		if (normalizedName.isEmpty())
		{
			return;
		}

		Map<String, Instant> timestamps =
				getCapturedNearbyTimestamps();

		if (timestamps.remove(
				normalizedName
		) != null)
		{
			saveCapturedNearbyTimestamps(
					timestamps
			);
		}
	}

	private Map<String, Instant>
	getCapturedNearbyTimestamps()
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
						configuredTimes.split(
								"[\r\n]+"
						)
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
								Instant.ofEpochMilli(
										epochMilli
								)
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
				new ArrayList<>();

		for (Map.Entry<String, Instant> entry
				: timestamps.entrySet())
		{
			lines.add(
					entry.getKey()
							+ "|"
							+ entry.getValue()
							.toEpochMilli()
			);
		}

		configManager.setConfiguration(
				CONFIG_GROUP,
				CAPTURED_NEARBY_NAME_TIMES_KEY,
				String.join(
						"\n",
						lines
				)
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
		if (!canTrackNearbyMembers())
		{
			clearNearbyMemberTracking();
			equipmentScannedVisibleMembers.clear();
			return;
		}

		Player localPlayer = client.getLocalPlayer();

		if (localPlayer == null)
		{
			clearNearbyMemberTracking();
			equipmentScannedVisibleMembers.clear();
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

			if (playerName == null)
			{
				continue;
			}

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

			scanEquipmentIfNeeded(
					player,
					normalizedName,
					playerName
			);

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

		equipmentScannedVisibleMembers.removeIf(
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

	private void scanEquipmentIfNeeded(
			Player player,
			String normalizedName,
			String playerName)
	{
		if (!isStaffFeaturesActive()
				|| !config.showMissingEquipmentWarning()
				|| getEquipmentInspectionIgnoredNames().contains(
				normalizedName
		)
				|| equipmentScannedVisibleMembers.contains(
				normalizedName
		))
		{
			return;
		}

		PlayerComposition composition =
				player.getPlayerComposition();

		if (composition == null)
		{
			return;
		}

		equipmentScannedVisibleMembers.add(
				normalizedName
		);

		int missingSlots =
				countMissingVisibleEquipment(
						composition
				);

		if (missingSlots
				>= config.missingEquipmentThreshold())
		{
			showMissingEquipmentNotification(
					playerName,
					missingSlots
			);
		}
	}

	private int countMissingVisibleEquipment(
			PlayerComposition composition)
	{
		int[] equipmentIds =
				composition.getEquipmentIds();

		if (equipmentIds == null
				|| equipmentIds.length == 0)
		{
			return VISIBLE_EQUIPMENT_SLOTS.length;
		}

		int weaponId =
				getEquippedItemId(
						equipmentIds,
						KitType.WEAPON
				);

		boolean twoHandedWeapon =
				isTwoHandedWeapon(weaponId);

		int missingSlots = 0;

		for (KitType slot : VISIBLE_EQUIPMENT_SLOTS)
		{
			if (slot == KitType.SHIELD
					&& twoHandedWeapon)
			{
				continue;
			}

			if (getEquippedItemId(
					equipmentIds,
					slot
			) < 0)
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

		if (slotIndex < 0
				|| slotIndex >= equipmentIds.length)
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

		if (itemComposition == null
				|| itemComposition.getName() == null)
		{
			return false;
		}

		String weaponName =
				itemComposition.getName()
						.toLowerCase(Locale.ROOT);

		for (String marker
				: TWO_HANDED_WEAPON_NAME_MARKERS)
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
								Text.toJagexName(
										playerName
								)
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

	private boolean canTrackNearbyMembers()
	{
		if (client.getFriendsChatManager() == null)
		{
			return false;
		}

		Player localPlayer = client.getLocalPlayer();

		return localPlayer != null
				&& TRACKING_REGION_IDS.contains(
				localPlayer.getWorldLocation().getRegionID()
		);
	}

	int getNearbyEnemyCount()
	{
		if (!authorizedFeaturesActive
				|| !canTrackNearbyMembers())
		{
			return -1;
		}

		Player localPlayer = client.getLocalPlayer();

		if (localPlayer == null)
		{
			return -1;
		}

		String localPlayerName =
				normalizeName(localPlayer.getName());

		int enemyCount = 0;

		for (Player player : client.getPlayers())
		{
			if (player == null)
			{
				continue;
			}

			String normalizedName =
					normalizeName(player.getName());

			if (normalizedName.isEmpty()
					|| normalizedName.equals(localPlayerName)
					|| currentMembers.contains(normalizedName))
			{
				continue;
			}

			enemyCount++;
		}

		return enemyCount;
	}

	int getNearbyFcCount()
	{
		if (!authorizedFeaturesActive
				|| !canTrackNearbyMembers())
		{
			return -1;
		}

		Player localPlayer = client.getLocalPlayer();

		if (localPlayer == null)
		{
			return -1;
		}

		String localPlayerName =
				normalizeName(localPlayer.getName());

		int fcCount = 0;

		for (Player player : client.getPlayers())
		{
			if (player == null)
			{
				continue;
			}

			String normalizedName =
					normalizeName(player.getName());

			if (normalizedName.isEmpty())
			{
				continue;
			}

			if (normalizedName.equals(localPlayerName)
					|| currentMembers.contains(normalizedName))
			{
				fcCount++;
			}
		}

		return fcCount;
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

			Map<String, Instant> timestamps =
					getCapturedNearbyTimestamps();

			for (String normalizedName : memberNames)
			{
				timestamps.remove(normalizedName);
			}

			saveCapturedNearbyTimestamps(
					timestamps
			);
		}
	}

	private void refreshConfiguredPlayerLists()
	{
		if (!isStaffFeaturesActive())
		{
			return;
		}

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
		)
				|| unrankedF2pMembers.contains(
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

	private void queueCurrentMembersForThieverMode()
	{
		clientThread.invokeLater(() ->
		{
			if (!isThieverFeaturesActive())
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

			Set<String> loadedMembers = new HashSet<>();
			for (FriendsChatMember member : members)
			{
				if (member == null)
				{
					continue;
				}

				String normalizedName = normalizeName(member.getName());
				if (!normalizedName.isEmpty())
				{
					loadedMembers.add(normalizedName);
					currentMembers.add(normalizedName);
				}
			}

			removeCurrentMembersFromCapturedList(loadedMembers);
			currentMembers.retainAll(loadedMembers);
			suppressJoinMessages = false;
			return true;
		});
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

				boolean unrankedF2p =
						updateF2pMemberState(member);

				boolean bannedPlayer =
						isBannedPlayer(playerName);

				if (bannedPlayer)
				{
					unrankedF2pMembers.remove(
							normalizedName
					);
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

		pendingF2pJoinMessages.remove(
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
			Integer cachedLevel =
					thievingLevels.get(
							normalizedName
					);

			if (cachedLevel != null)
			{
				showF2pJoinMessage(
						normalizedName,
						playerName,
						cachedLevel
				);
			}

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

		lastLookupTimes.put(
				normalizedName,
				Instant.now()
		);

		thievingLevels.put(
				normalizedName,
				level
		);

		showF2pJoinMessage(
				normalizedName,
				playerName,
				level
		);

		showLowLevelJoinMessage(
				normalizedName,
				playerName,
				level
		);

		if (level < REQUIRED_THIEVING_LEVEL
				|| unrankedF2pMembers.contains(
				normalizedName
		))
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

	private void showF2pJoinMessage(
			String normalizedName,
			String playerName,
			int thievingLevel)
	{
		if (!pendingF2pJoinMessages.remove(
				normalizedName
		))
		{
			return;
		}

		if (!config.showF2pJoinMessage()
				|| getIgnoredNames().contains(
				normalizedName
		)
				|| getBannedNames().contains(
				normalizedName
		)
				|| !unrankedF2pMembers.contains(
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
				"(F2P - "
						+ thievingLevel
						+ " Thieving)"
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
				|| unrankedF2pMembers.contains(
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

	private void reconcileFriendsChatMembers()
	{
		FriendsChatManager friendsChatManager =
				client.getFriendsChatManager();

		if (friendsChatManager == null)
		{
			return;
		}

		FriendsChatMember[] members =
				friendsChatManager.getMembers();

		if (members == null)
		{
			return;
		}

		Set<String> actualMembers =
				new HashSet<>();

		for (FriendsChatMember member : members)
		{
			if (member == null)
			{
				continue;
			}

			String normalizedName =
					normalizeName(member.getName());

			if (!normalizedName.isEmpty())
			{
				actualMembers.add(normalizedName);
			}
		}

		for (String normalizedName
				: new HashSet<>(currentMembers))
		{
			if (actualMembers.contains(normalizedName))
			{
				continue;
			}

			currentMembers.remove(normalizedName);
			unrankedF2pMembers.remove(normalizedName);
			pendingJoinMessages.remove(normalizedName);
			pendingF2pJoinMessages.remove(normalizedName);
			equipmentScannedVisibleMembers.remove(normalizedName);
			removeNearbyMemberTracking(normalizedName);
			markMemberDeparted(normalizedName);
		}

		currentMembers.addAll(actualMembers);
	}

	private void refreshF2pMemberStates()
	{
		FriendsChatManager friendsChatManager =
				client.getFriendsChatManager();

		if (friendsChatManager == null)
		{
			unrankedF2pMembers.clear();
			return;
		}

		FriendsChatMember[] members =
				friendsChatManager.getMembers();

		if (members == null)
		{
			return;
		}

		Set<String> refreshedNames =
				new HashSet<>();

		for (FriendsChatMember member : members)
		{
			if (member == null)
			{
				continue;
			}

			String playerName = member.getName();
			String normalizedName =
					normalizeName(playerName);

			if (normalizedName.isEmpty()
					|| isBannedPlayer(playerName))
			{
				continue;
			}

			if (updateF2pMemberState(member))
			{
				refreshedNames.add(normalizedName);
			}
		}

		for (String normalizedName
				: new HashSet<>(
				unrankedF2pMembers
		))
		{
			if (refreshedNames.contains(
					normalizedName
			))
			{
				continue;
			}

			unrankedF2pMembers.remove(
					normalizedName
			);

			Integer level =
					thievingLevels.get(
							normalizedName
					);

			if (level == null
					|| level
					>= REQUIRED_THIEVING_LEVEL)
			{
				lowLevelMembers.remove(
						normalizedName
				);
			}
		}
	}

	private boolean updateF2pMemberState(
			FriendsChatMember member)
	{
		if (member == null)
		{
			return false;
		}

		String playerName = member.getName();
		String normalizedName =
				normalizeName(playerName);

		if (normalizedName.isEmpty())
		{
			return false;
		}

		boolean unrankedF2p =
				isUnrankedF2p(member);

		if (unrankedF2p)
		{
			unrankedF2pMembers.add(
					normalizedName
			);

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
		else
		{
			unrankedF2pMembers.remove(
					normalizedName
			);

			Integer level =
					thievingLevels.get(
							normalizedName
					);

			if (level == null
					|| level
					>= REQUIRED_THIEVING_LEVEL)
			{
				lowLevelMembers.remove(
						normalizedName
				);
			}
		}

		return unrankedF2p;
	}

	private boolean isUnrankedF2p(
			FriendsChatMember member)
	{
		if (member == null
				|| member.getRank()
				!= FriendsChatRank.UNRANKED)
		{
			return false;
		}

		WorldResult worlds =
				worldService.getWorlds();

		if (worlds == null)
		{
			return false;
		}

		World world =
				worlds.findWorld(
						member.getWorld()
				);

		return world != null
				&& !world.getTypes().contains(
				WorldType.MEMBERS
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

		boolean unrankedF2p =
				unrankedF2pMembers.contains(
						normalizedName
				);

		if (level == null && !unrankedF2p)
		{
			return;
		}

		if (!unrankedF2p
				&& level >= REQUIRED_THIEVING_LEVEL)
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
		if (!canTrackNearbyMembers())
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
		String source = config.ignoredNames();

		if (!sameConfiguredValue(
				source,
				cachedIgnoredNamesSource
		))
		{
			cachedIgnoredNames =
					parseConfiguredNames(source);
			cachedIgnoredNamesSource = source;
		}

		return cachedIgnoredNames;
	}

	private Set<String> getBannedNames()
	{
		String source = config.bannedNames();

		if (!sameConfiguredValue(
				source,
				cachedBannedNamesSource
		))
		{
			cachedBannedNames =
					parseConfiguredNames(source);
			cachedBannedNamesSource = source;
		}

		return cachedBannedNames;
	}

	private Set<String> getOvertimeWhitelistNames()
	{
		String source = config.overtimeWhitelistNames();

		if (!sameConfiguredValue(
				source,
				cachedOvertimeWhitelistSource
		))
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

		if (!sameConfiguredValue(
				source,
				cachedEquipmentIgnoreSource
		))
		{
			cachedEquipmentInspectionIgnoredNames =
					parseConfiguredNames(source);
			cachedEquipmentIgnoreSource = source;
		}

		return cachedEquipmentInspectionIgnoredNames;
	}

	private void refreshConfiguredNameCaches()
	{
		cachedIgnoredNamesSource = config.ignoredNames();
		cachedIgnoredNames =
				parseConfiguredNames(
						cachedIgnoredNamesSource
				);

		cachedBannedNamesSource = config.bannedNames();
		cachedBannedNames =
				parseConfiguredNames(
						cachedBannedNamesSource
				);

		cachedOvertimeWhitelistSource =
				config.overtimeWhitelistNames();
		cachedOvertimeWhitelistNames =
				parseConfiguredNames(
						cachedOvertimeWhitelistSource
				);

		cachedEquipmentIgnoreSource =
				config.equipmentInspectionIgnoredNames();
		cachedEquipmentInspectionIgnoredNames =
				parseConfiguredNames(
						cachedEquipmentIgnoreSource
				);
	}

	private boolean sameConfiguredValue(
			String first,
			String second)
	{
		return first == null
				? second == null
				: first.equals(second);
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

			if (unrankedF2pMembers.contains(
					normalizedName
			))
			{
				String f2pColorOpen =
						ignoredNames.contains(
								normalizedName
						)
								? "<col=00ff00>"
								: RED_TEXT_OPEN;

				nameWidget.setText(
						f2pColorOpen
								+ originalText
								+ F2P_MEMBER_TEXT
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

		if (unrankedF2pMembers.contains(
				normalizedName
		)
				&& !ignoredNames.contains(
				normalizedName
		))
		{
			return 1;
		}

		Integer thievingLevel =
				thievingLevels.get(normalizedName);

		if (thievingLevel != null
				&& thievingLevel < REQUIRED_THIEVING_LEVEL
				&& !ignoredNames.contains(normalizedName))
		{
			return 2;
		}

		return 3;
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
		else if (plainText.endsWith(
				F2P_MEMBER_TEXT
		))
		{
			plainText = plainText.substring(
					0,
					plainText.length()
							- F2P_MEMBER_TEXT.length()
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