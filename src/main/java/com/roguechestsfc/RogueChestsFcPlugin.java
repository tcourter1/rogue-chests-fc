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
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.FriendsChatRank;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.WorldView;
import net.runelite.api.ScriptID;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.FriendsChatMemberJoined;
import net.runelite.api.events.FriendsChatMemberLeft;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
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
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
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
	private static final String REQUIRED_FRIENDS_CHAT = "Rogue Chests";
	private static final int ROGUES_CASTLE_CHEST_ID = 26757;
	private static final String SEARCH_FOR_TRAPS_OPTION = "Search for traps";
	private static final String WALK_HERE_OPTION = "Walk here";
	private static final String ROGUE_NPC_NAME = "Rogue";
	private static final String EDWARD_NPC_NAME = "Edward";
	private static final Duration PARTY_FC_CHECK_GRACE = Duration.ofSeconds(3);
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
	private static final String PARTY_SYNC_VERSION_KEY =
			"partySyncVersion";
	private static final String PARTY_SYNC_NONCE_KEY =
			"partySyncNonce";
	private static final String PARTY_SYNC_CIPHERTEXT_KEY =
			"partySyncCiphertext";
	private static final String PARTY_SYNC_MAC_KEY =
			"partySyncMac";
	private static final String PLUGIN_MODE_KEY =
			"pluginMode";

	private static final String PARTY_SYNC_ALGORITHM =
			"HMAC-SHA256-STREAM-v1";

	private static final String PARTY_SYNC_ROOT_KEY_BASE64 =
			"jtlLqKn/LpgI7uXh7/Y2ASkxVjs9seqWWHFv33Lp934=";

	private static final String BAN_SYNC_URL =
			"https://script.google.com/macros/s/AKfycbx89x9PgKuyctvvMdOViiXHXei9HNOFubnUle4iMgpaYGLZCYcz7h6UKfEAoNBWbauBhw/exec";

	private static final String BAN_SYNC_TOKEN =
			"k9X2mP7qW4vL1bZ8fY3hR6dN0jT5gC2x";


	private static final Duration LOOKUP_COOLDOWN =
			Duration.ofMinutes(2);

	private static final Duration JOIN_MESSAGE_COOLDOWN =
			Duration.ofMinutes(2);

	private static final Duration DEPARTED_DISPLAY_DURATION =
			Duration.ofMinutes(1);

	private static final Duration BAN_LIST_SYNC_INTERVAL =
			Duration.ofMinutes(10);

	private static final Duration ACCESS_SANITY_CHECK_INTERVAL =
			Duration.ofSeconds(10);

	private static final Duration STAFF_RANK_CACHE_DURATION =
			Duration.ofMinutes(30);

	private static final Duration STAFF_RANK_REVOCATION_GRACE =
			Duration.ofSeconds(10);

	private static final Duration HIGH_LEVEL_CACHE_TTL =
			Duration.ofDays(60);

	private static final long CAPTURED_NEARBY_CLEANUP_INTERVAL_NANOS =
			TimeUnit.SECONDS.toNanos(5);

	private static final long CAPTURED_NEARBY_FLUSH_INTERVAL_NANOS =
			TimeUnit.SECONDS.toNanos(1);

	private static final long HIGH_LEVEL_CACHE_WRITE_INTERVAL_NANOS =
			TimeUnit.SECONDS.toNanos(30);

	private static final String HIGH_LEVEL_CACHE_DIRECTORY =
			"rogue-chests-fc";

	private static final String HIGH_LEVEL_CACHE_FILENAME =
			"high-level-cache.txt";

	private static final long MEMBER_LIST_REFRESH_INTERVAL_NANOS =
			TimeUnit.SECONDS.toNanos(1);

	private static final int LOOKUPS_PER_TICK = 1;
	private static final int REQUIRED_THIEVING_LEVEL = 84;
	private static final int HIGH_VALUE_VISIBLE_ITEM_GP = 2_000_000;

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
	private RogueChestsFcThieverMode thieverMode;

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

	private final Map<Long, Instant> pendingPartyMembershipChecks =
			new ConcurrentHashMap<>();

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

	private final Map<String, Instant> knownHighLevelPlayers =
			new ConcurrentHashMap<>();

	private final Object highLevelCacheFileLock =
			new Object();

	private File highLevelCacheFile;
	private volatile boolean highLevelCacheDirty;

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
	private boolean clearCapturedNearbyOnNextLogin;

	private Instant lastAccessSanityCheck;
	private volatile boolean cachedStaffRankAuthorized;
	private volatile Instant cachedStaffRankCheckedAt;
	private volatile Instant unauthorizedStaffRankObservedAt;
	private boolean pendingPostHopOutsiderCapture;
	private final Map<String, String> pendingCapturedNearbyNames =
			new LinkedHashMap<>();
	private long lastCapturedNearbyFlushNanos;
	private long lastCapturedNearbyCleanupNanos;
	private long lastHighLevelCacheWriteNanos;
	private long lastMemberListRefreshNanos;
	private int cachedNearbyEnemyCount = -1;
	private int cachedNearbyFcCount = -1;

	private ModeTransitionPhase modeTransitionPhase =
			ModeTransitionPhase.NONE;
	private RogueChestsFcConfig.PluginMode pendingTransitionMode =
			RogueChestsFcConfig.PluginMode.NONE;

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
				this::syncBanListNow,
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


	void syncBanListNow()
	{
		if (!isStaffInfrastructureActive())
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

		refreshPanel();

		executor.execute(() ->
		{
			try
			{
				SyncedPlayerLists syncedLists =
						fetchGlobalLists();

				applySyncedLists(
						syncedLists
				);
			}
			catch (Exception exception)
			{
				lastBanListSyncError =
						exception.getMessage() == null
								? "Sync failed"
								: exception.getMessage();

				log.debug(
						"Unable to sync global lists",
						exception
				);

				refreshPanel();
			}
			finally
			{
				banListSyncInProgress.set(false);
				refreshPanel();
			}
		});
	}

	private SyncedPlayerLists fetchGlobalLists()
			throws Exception
	{
		HttpUrl baseUrl = HttpUrl.parse(
				BAN_SYNC_URL
		);

		if (baseUrl == null)
		{
			throw new IllegalStateException(
					"Invalid sync endpoint"
			);
		}

		HttpUrl requestUrl = baseUrl.newBuilder()
				.addQueryParameter(
						"token",
						BAN_SYNC_TOKEN
				)
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

			JsonObject root = parseSyncResponse(body.string());

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

			if (!root.has("under84Players")
					|| !root.get("under84Players").isJsonArray())
			{
				throw new IllegalStateException(
						"Missing under84Players array"
				);
			}

			if (!root.has("party")
					|| !root.get("party").isJsonObject())
			{
				throw new IllegalStateException(
						"Missing party object"
				);
			}

			return new SyncedPlayerLists(
					parseSyncedPlayerArray(
							root.getAsJsonArray("players")
					),
					parseSyncedPlayerArray(
							root.getAsJsonArray("under84Players")
					),
					parseSyncedPartyCredential(
							root.getAsJsonObject("party")
					)
			);
		}
	}

	private JsonObject parseSyncResponse(String responseText)
	{
		return new JsonParser()
				.parse(responseText)
				.getAsJsonObject();
	}

	private SyncedPartyCredential parseSyncedPartyCredential(
			JsonObject party)
	{
		if (!party.has("version"))
		{
			throw new IllegalStateException(
					"Missing Party version"
			);
		}

		int version = party.get("version").getAsInt();

		boolean available =
				!party.has("available")
						|| party.get("available").getAsBoolean();

		String algorithm =
				party.has("algorithm")
						? party.get("algorithm").getAsString()
						: PARTY_SYNC_ALGORITHM;

		if (!PARTY_SYNC_ALGORITHM.equals(algorithm))
		{
			throw new IllegalStateException(
					"Unsupported Party sync algorithm"
			);
		}

		if (!available)
		{
			return new SyncedPartyCredential(
					version,
					false,
					null,
					null,
					null
			);
		}

		if (!party.has("nonce")
				|| !party.has("ciphertext")
				|| !party.has("mac"))
		{
			throw new IllegalStateException(
					"Incomplete Party credential"
			);
		}

		return new SyncedPartyCredential(
				version,
				true,
				party.get("nonce").getAsString(),
				party.get("ciphertext").getAsString(),
				party.get("mac").getAsString()
		);
	}

	private List<String> parseSyncedPlayerArray(
			JsonArray players)
	{
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

	private void applySyncedLists(
			SyncedPlayerLists syncedLists)
			throws GeneralSecurityException
	{
		applySyncedPartyCredential(
				syncedLists.partyCredential
		);

		configManager.setConfiguration(
				CONFIG_GROUP,
				BANNED_NAMES_KEY,
				String.join(
						"\n",
						syncedLists.bannedNames
				)
		);

		configManager.setConfiguration(
				CONFIG_GROUP,
				IGNORED_NAMES_KEY,
				String.join(
						"\n",
						syncedLists.ignoredNames
				)
		);

		cachedBannedNamesSource = null;
		cachedBannedNames = Collections.emptySet();
		cachedIgnoredNamesSource = null;
		cachedIgnoredNames = Collections.emptySet();

		lastBanListSync = Instant.now();
		lastBanListSyncError = null;

		clientThread.invokeLater(() ->
		{
			refreshConfiguredPlayerLists();
			return true;
		});

		refreshPanel();
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
		clearCapturedNearbyOnNextLogin =
				client.getGameState() != GameState.LOGGED_IN;

		refreshConfiguredNameCaches();
		loadHighLevelCache();

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
				false
		);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			reconcileFriendsChatAccess();
		}
		else
		{
			clearRuntimeState();
			clientThread.invoke(this::removeLevelsFromMemberList);
		}
	}

	private void activateStaffFeatures()
	{
		beginModeTransition(
				RogueChestsFcConfig.PluginMode.STAFF
		);
	}

	private void activateThieverFeatures()
	{
		beginModeTransition(
				RogueChestsFcConfig.PluginMode.THIEVER
		);
	}

	private void deactivateModeFeatures()
	{
		beginModeTransition(
				RogueChestsFcConfig.PluginMode.NONE
		);
	}

	private void beginModeTransition(
			RogueChestsFcConfig.PluginMode targetMode)
	{
		RogueChestsFcConfig.PluginMode normalizedTarget =
				targetMode == null
						? RogueChestsFcConfig.PluginMode.NONE
						: targetMode;

		if (normalizedTarget == pendingTransitionMode
				&& modeTransitionPhase != ModeTransitionPhase.NONE)
		{
			return;
		}

		pendingTransitionMode = normalizedTarget;

		// Stop new work immediately. The remaining cleanup/activation work
		// is intentionally spread across later client ticks.
		if (normalizedTarget == RogueChestsFcConfig.PluginMode.NONE)
		{
			authorizedFeaturesActive = false;
			staffFeaturesActive = false;
			partyJoinBannerVisible = false;
			partyReminderDismissedForLogin = false;

			modeTransitionPhase =
					ModeTransitionPhase.DEACTIVATE_OVERLAYS;
		}
		else
		{
			modeTransitionPhase =
					ModeTransitionPhase.PREPARE_ACTIVATION;
		}
	}

	private void processModeTransition()
	{
		if (modeTransitionPhase == ModeTransitionPhase.NONE)
		{
			return;
		}

		switch (modeTransitionPhase)
		{
			case DEACTIVATE_OVERLAYS:
				overlayManager.remove(overlay);
				overlayManager.remove(overtimeOverlay);
				overlayManager.remove(partyOverlay);
				overlayManager.remove(partyPingBeamOverlay);
				overlayManager.remove(enemyOverlay);
				overlayManager.remove(thieverMode);

				partyPingBeamOverlay.clearPings();
				stopBanListSyncScheduler();

				cachedNearbyEnemyCount = -1;
				cachedNearbyFcCount = -1;

				modeTransitionPhase =
						ModeTransitionPhase.DEACTIVATE_RUNTIME;
				return;

			case DEACTIVATE_RUNTIME:
				clearRuntimeState();

				modeTransitionPhase =
						ModeTransitionPhase.DEACTIVATE_WIDGETS;
				return;

			case DEACTIVATE_WIDGETS:
				removeLevelsFromMemberList();
				refreshPanel();

				modeTransitionPhase =
						pendingTransitionMode
								== RogueChestsFcConfig.PluginMode.NONE
								? ModeTransitionPhase.NONE
								: ModeTransitionPhase.PREPARE_ACTIVATION;
				return;

			case PREPARE_ACTIVATION:
				if (client.getGameState() != GameState.LOGGED_IN)
				{
					return;
				}

				if (pendingTransitionMode
						== RogueChestsFcConfig.PluginMode.STAFF)
				{
					if (!isInRequiredFriendsChat()
							|| !isAuthorized())
					{
						return;
					}
				}
				else if (pendingTransitionMode
						== RogueChestsFcConfig.PluginMode.THIEVER)
				{
					if (!isInRequiredFriendsChat())
					{
						return;
					}
				}
				else
				{
					modeTransitionPhase =
							ModeTransitionPhase.NONE;
					return;
				}

				// If another mode is currently active, tear it down first.
				if (authorizedFeaturesActive)
				{
					authorizedFeaturesActive = false;
					staffFeaturesActive = false;

					modeTransitionPhase =
							ModeTransitionPhase.DEACTIVATE_OVERLAYS;
					return;
				}

				modeTransitionPhase =
						ModeTransitionPhase.ACTIVATE_OVERLAYS;
				return;

			case ACTIVATE_OVERLAYS:
				authorizedFeaturesActive = true;
				staffFeaturesActive =
						pendingTransitionMode
								== RogueChestsFcConfig.PluginMode.STAFF;

				if (staffFeaturesActive)
				{
					overlayManager.add(overlay);
				}
				else
				{
					overlayManager.add(thieverMode);
				}

				overlayManager.add(overtimeOverlay);
				overlayManager.add(partyOverlay);
				overlayManager.add(partyPingBeamOverlay);
				overlayManager.add(enemyOverlay);

				suppressJoinMessages = true;

				modeTransitionPhase =
						ModeTransitionPhase.ACTIVATE_BACKGROUND;
				return;

			case ACTIVATE_BACKGROUND:
				if (!authorizedFeaturesActive)
				{
					modeTransitionPhase =
							ModeTransitionPhase.NONE;
					return;
				}

				if (staffFeaturesActive)
				{
					startBanListSyncScheduler();
				}

				modeTransitionPhase =
						ModeTransitionPhase.ACTIVATE_DATA;
				return;

			case ACTIVATE_DATA:
				if (!authorizedFeaturesActive)
				{
					modeTransitionPhase =
							ModeTransitionPhase.NONE;
					return;
				}

				if (staffFeaturesActive)
				{
					queueCurrentMembersWhenAvailable();
				}
				else
				{
					queueCurrentMembersForThieverMode();
				}

				modeTransitionPhase =
						ModeTransitionPhase.ACTIVATE_UI;
				return;

			case ACTIVATE_UI:
				if (!authorizedFeaturesActive)
				{
					modeTransitionPhase =
							ModeTransitionPhase.NONE;
					return;
				}

				if (client.getGameState() == GameState.LOGGED_IN)
				{
					updatePartyJoinBannerForLogin();
				}

				panel.setModeState(
						pendingTransitionMode,
						staffFeaturesActive
				);

				refreshPanel();

				modeTransitionPhase =
						ModeTransitionPhase.NONE;
				return;

			default:
				modeTransitionPhase =
						ModeTransitionPhase.NONE;
		}
	}

	private void clearRuntimeState()
	{
		lookupQueue.clear();
		pendingLookups.clear();
		pendingJoinMessages.clear();
		pendingF2pJoinMessages.clear();
		pendingPartyMembershipChecks.clear();
		displayNames.clear();
		lastLookupTimes.clear();
		lastJoinMessageTimes.clear();
		thievingLevels.clear();
		lowLevelMembers.clear();
		currentMembers.clear();
		unrankedF2pMembers.clear();
		clearNearbyMemberTracking();
		equipmentScannedVisibleMembers.clear();
		pendingCapturedNearbyNames.clear();
		lastCapturedNearbyFlushNanos = 0L;
		thieverMode.reset();
		suppressJoinMessages = true;
	}


	@Override
	protected void shutDown()
	{
		modeTransitionPhase = ModeTransitionPhase.NONE;
		pendingTransitionMode = RogueChestsFcConfig.PluginMode.NONE;

		overlayManager.remove(overlay);
		overlayManager.remove(overtimeOverlay);
		overlayManager.remove(partyOverlay);
		overlayManager.remove(partyPingBeamOverlay);
		overlayManager.remove(enemyOverlay);
		overlayManager.remove(thieverMode);

		partyPingBeamOverlay.clearPings();
		stopBanListSyncScheduler();

		authorizedFeaturesActive = false;
		staffFeaturesActive = false;
		clearRuntimeState();
		removeLevelsFromMemberList();
		persistHighLevelCacheIfDirty();

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
		clientThread.invokeLater(() ->
		{
			reconcileFriendsChatAccess();
			return true;
		});

		if (client.getGameState() != GameState.LOGGED_IN
				|| !authorizedFeaturesActive)
		{
			return;
		}

		if (!event.isJoined()
				|| !isInRequiredFriendsChat())
		{
			suspendFriendsChatWork();
			return;
		}

		clearNearbyMemberTracking();
		cachedNearbyEnemyCount = -1;
		cachedNearbyFcCount = -1;

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

		FriendsChatManager manager =
				client.getFriendsChatManager();

		if (manager != null
				&& manager.getMembers() != null)
		{
			Set<String> restoredMembers =
					new HashSet<>();

			for (FriendsChatMember member :
					manager.getMembers())
			{
				if (member == null)
				{
					continue;
				}

				String normalizedName =
						normalizeName(
								member.getName()
						);

				if (!normalizedName.isEmpty())
				{
					restoredMembers.add(
							normalizedName
					);
				}
			}

			removeCurrentMembersFromCapturedList(
					restoredMembers
			);
		}
	}

	private void suspendFriendsChatWork()
	{
		lookupQueue.clear();
		pendingLookups.clear();
		pendingJoinMessages.clear();
		pendingF2pJoinMessages.clear();
		displayNames.clear();
		currentMembers.clear();
		unrankedF2pMembers.clear();
		lowLevelMembers.clear();
		equipmentScannedVisibleMembers.clear();
		suppressJoinMessages = true;
		lastMemberListRefreshNanos = 0L;
		refreshPanel();
	}

	@Subscribe
	public void onFriendsChatMemberJoined(
			FriendsChatMemberJoined event)
	{
		if (!authorizedFeaturesActive
				|| !isInRequiredFriendsChat())
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
		if (!authorizedFeaturesActive
				|| !isInRequiredFriendsChat())
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
	public void onItemContainerChanged(
			ItemContainerChanged event)
	{
		thieverMode.onItemContainerChanged(
				event,
				isThieverInfrastructureActive()
		);
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort ignored)
	{
		if (isThieverFeaturesInactive()
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

		for (int i = 0; i < entries.length; i++)
		{
			MenuEntry entry = entries[i];
			if (entry == null)
			{
				continue;
			}

			if (WALK_HERE_OPTION.equalsIgnoreCase(entry.getOption()))
			{
				walkIndex = i;
			}

			if (entry.getIdentifier() == ROGUES_CASTLE_CHEST_ID)
			{
				chestPresent = true;
				if (SEARCH_FOR_TRAPS_OPTION.equalsIgnoreCase(entry.getOption()))
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

		// The last menu entry is the active left-click option. Running this
		// after menu sorting lets our Rogue Chests safety swaps take priority
		// over Menu Entry Swapper and other earlier menu reordering.
		if (chestPresent)
		{
			int desiredIndex = thieverMode.isBankLockoutActive()
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


	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		if (!authorizedFeaturesActive
				|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (cannotTrackNearbyMembers())
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

		queueCapturedNearbyName(playerName);
	}

	@Subscribe
	public void onGameTick(GameTick ignored)
	{
		autoEnableThieverModeIfNeeded();
		processModeTransition();

		thieverMode.onGameTick(
				isThieverInfrastructureActive()
		);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			runAccessSanityCheck();
			capturePostHopVisiblePlayers();
		}

		if (client.getGameState() != GameState.LOGGED_IN
				|| !authorizedFeaturesActive)
		{
			return;
		}

		processPendingPartyMembershipChecks();

		if (staffFeaturesActive
				&& isInRequiredFriendsChat())
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

		flushCapturedNearbyNamesIfDue();
		removeExpiredCapturedNearbyNamesIfDue();
		persistHighLevelCacheIfDue();
		updateNearbyMemberTrackingAndCounts();
	}

	@Subscribe
	public void onGameStateChanged(
			GameStateChanged event)
	{
		GameState gameState = event.getGameState();

		// Never tear down/rebuild the plugin during transient game states.
		// FC access is only evaluated once the client is stably LOGGED_IN.
		if (gameState != GameState.LOGGED_IN)
		{
			partyJoinBannerVisible = false;
			cachedNearbyEnemyCount = -1;
			cachedNearbyFcCount = -1;
		}

		// A normal logout should not immediately clear Nearby Outsiders.
		// The next real login will clear it.
		if (gameState == GameState.LOGIN_SCREEN)
		{
			lastAccessSanityCheck = null;
			pendingPostHopOutsiderCapture = false;
			pendingCapturedNearbyNames.clear();
			pendingPartyMembershipChecks.clear();
			lastCapturedNearbyFlushNanos = 0L;
			clearCapturedNearbyOnNextLogin = true;
			partyReminderDismissedForLogin = false;

			partyPingBeamOverlay.clearPings();
			clearNearbyMemberTracking();
			equipmentScannedVisibleMembers.clear();
			refreshPanel();
			return;
		}

		// World hops clear Nearby Outsiders immediately, but do not
		// deactivate/rebuild mode features.
		if (gameState == GameState.HOPPING)
		{
			lastAccessSanityCheck = null;
			pendingPostHopOutsiderCapture = true;

			clearCapturedNearbyNames();
			clearCapturedNearbyOnNextLogin = false;

			partyPingBeamOverlay.clearPings();
			clearNearbyMemberTracking();
			equipmentScannedVisibleMembers.clear();
			refreshPanel();
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

		reconcileFriendsChatAccess();

		if (authorizedFeaturesActive
				&& isInRequiredFriendsChat())
		{
			updatePartyJoinBannerForLogin();
		}
	}

	@Subscribe(priority = Float.NEGATIVE_INFINITY)
	public void onPostClientTick(
			PostClientTick ignored)
	{
		if (!authorizedFeaturesActive
				|| !staffFeaturesActive
				|| !isInRequiredFriendsChat()
				|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		long now = System.nanoTime();

		if (now - lastMemberListRefreshNanos
				< MEMBER_LIST_REFRESH_INTERVAL_NANOS)
		{
			return;
		}

		lastMemberListRefreshNanos = now;
		applyLevelsToMemberList();
	}

	@Subscribe(priority = Float.NEGATIVE_INFINITY)
	public void onScriptPostFired(
			ScriptPostFired event)
	{
		if (!authorizedFeaturesActive
				|| !isInRequiredFriendsChat()
				|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (event.getScriptId()
				== ScriptID.FRIENDS_CHAT_CHANNEL_REBUILD)
		{
			reconcileFriendsChatAccess();
			reconcileFriendsChatMembers();
			removeCurrentMembersFromCapturedList(
					new HashSet<>(
							currentMembers
					)
			);

			if (staffFeaturesActive)
			{
				refreshF2pMemberStates();
				applyLevelsToMemberList();
			}
		}
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		if (!isStaffInfrastructureActive())
		{
			return;
		}

		pendingPartyMembershipChecks.putIfAbsent(
				event.getMemberId(),
				Instant.now()
		);
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		pendingPartyMembershipChecks.remove(
				event.getMemberId()
		);
	}

	private void processPendingPartyMembershipChecks()
	{
		if (pendingPartyMembershipChecks.isEmpty()
				|| !isStaffInfrastructureActive()
				|| !partyService.isInParty()
				|| !isInRequiredFriendsChat())
		{
			return;
		}

		FriendsChatManager friendsChatManager =
				client.getFriendsChatManager();

		if (friendsChatManager == null)
		{
			return;
		}

		PartyMember localMember = partyService.getLocalMember();
		Instant now = Instant.now();

		for (Map.Entry<Long, Instant> entry :
				new ArrayList<>(pendingPartyMembershipChecks.entrySet()))
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
				pendingPartyMembershipChecks.remove(memberId);
				continue;
			}

			if (localMember != null
					&& Objects.equals(
					localMember.getMemberId(),
					memberId
			))
			{
				pendingPartyMembershipChecks.remove(memberId);
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
				pendingPartyMembershipChecks.remove(memberId);
				continue;
			}

			boolean inFriendsChat = false;

			for (FriendsChatMember friendsChatMember :
					friendsChatManager.getMembers())
			{
				if (friendsChatMember != null
						&& normalizedName.equals(
						normalizeName(
								friendsChatMember.getName()
						)
				))
				{
					inFriendsChat = true;
					break;
				}
			}

			pendingPartyMembershipChecks.remove(memberId);

			if (inFriendsChat)
			{
				continue;
			}

			String message = new ChatMessageBuilder()
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
	public void onOverheadTextChanged(OverheadTextChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();

		if (localPlayer == null
				|| !TRACKING_REGION_IDS.contains(
				localPlayer.getWorldLocation().getRegionID()
		))
		{
			return;
		}

		if (!(event.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) event.getActor();

		if (npc.getName() == null
				|| !ROGUE_NPC_NAME.equalsIgnoreCase(npc.getName()))
		{
			return;
		}

		npc.setOverheadText(null);
	}

	private void reconcileFriendsChatAccess()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		lastAccessSanityCheck = Instant.now();

		RogueChestsFcConfig.PluginMode mode =
				getPluginMode();

		boolean inRequiredChat =
				isInRequiredFriendsChat();

		if (mode == RogueChestsFcConfig.PluginMode.STAFF)
		{
			if (!inRequiredChat)
			{
				if (modeTransitionPhase == ModeTransitionPhase.NONE)
				{
					panel.setModeState(
							mode,
							authorizedFeaturesActive
									&& staffFeaturesActive
					);
				}

				return;
			}

			boolean authorized = isAuthorized();

			if (authorized)
			{
				if ((!authorizedFeaturesActive
						|| !staffFeaturesActive)
						&& isNotTransitioningTo(
						RogueChestsFcConfig.PluginMode.STAFF
				))
				{
					activateStaffFeatures();
				}
			}
			else if (authorizedFeaturesActive
					&& staffFeaturesActive
					&& isNotTransitioningTo(
					RogueChestsFcConfig.PluginMode.NONE
			))
			{
				deactivateModeFeatures();
			}

			if (modeTransitionPhase == ModeTransitionPhase.NONE)
			{
				panel.setModeState(mode, authorized);
			}

			return;
		}

		if (mode == RogueChestsFcConfig.PluginMode.THIEVER)
		{
			if (inRequiredChat
					&& (!authorizedFeaturesActive
					|| staffFeaturesActive)
					&& isNotTransitioningTo(
					RogueChestsFcConfig.PluginMode.THIEVER
			))
			{
				activateThieverFeatures();
			}

			if (modeTransitionPhase == ModeTransitionPhase.NONE)
			{
				panel.setModeState(mode, false);
			}

			return;
		}

		if (authorizedFeaturesActive
				&& isNotTransitioningTo(
				RogueChestsFcConfig.PluginMode.NONE
		))
		{
			deactivateModeFeatures();
		}

		if (modeTransitionPhase == ModeTransitionPhase.NONE)
		{
			panel.setModeState(mode, false);
		}
	}

	private void runAccessSanityCheck()
	{
		Instant now = Instant.now();

		if (lastAccessSanityCheck != null
				&& Duration.between(
				lastAccessSanityCheck,
				now
		).compareTo(
				ACCESS_SANITY_CHECK_INTERVAL
		) < 0)
		{
			return;
		}

		reconcileFriendsChatAccess();
	}

	private void capturePostHopVisiblePlayers()
	{
		if (!pendingPostHopOutsiderCapture
				|| !authorizedFeaturesActive
				|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Player localPlayer =
				client.getLocalPlayer();

		if (localPlayer == null)
		{
			return;
		}

		// Only perform the recovery scan inside the Rogue Castle
		// tracking regions.
		if (!TRACKING_REGION_IDS.contains(
				localPlayer.getWorldLocation().getRegionID()
		))
		{
			pendingPostHopOutsiderCapture = false;
			return;
		}

		String localPlayerName =
				normalizeName(
						localPlayer.getName()
				);

		// FC membership commonly repopulates several seconds after the
		// destination world has already rendered nearby players. Capture
		// everyone except ourselves in one batch, then prune confirmed FC
		// members as soon as the Friends Chat state comes back.
		List<String> visiblePlayerNames = new ArrayList<>();

		for (Player player : getVisiblePlayers())
		{
			if (player == null
					|| player.getName() == null)
			{
				continue;
			}

			String normalizedName =
					normalizeName(
							player.getName()
					);

			if (normalizedName.isEmpty()
					|| normalizedName.equals(
					localPlayerName
			))
			{
				continue;
			}

			visiblePlayerNames.add(
					player.getName()
			);
		}

		addCapturedNearbyNames(visiblePlayerNames);

		pendingPostHopOutsiderCapture = false;
	}


	private void autoEnableThieverModeIfNeeded()
	{
		if (client.getGameState() != GameState.LOGGED_IN
				|| isStaffMode()
				|| isThieverMode()
				|| !isInRequiredFriendsChat()
				|| !thieverMode.shouldAutoEnable())
		{
			return;
		}

		setPluginMode(
				RogueChestsFcConfig.PluginMode.THIEVER
		);
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
				beginModeTransition(
						RogueChestsFcConfig.PluginMode.STAFF
				);
			}
			else
			{
				beginModeTransition(
						RogueChestsFcConfig.PluginMode.NONE
				);
			}
		}
		else if (selected == RogueChestsFcConfig.PluginMode.THIEVER)
		{
			if (isInRequiredFriendsChat())
			{
				beginModeTransition(
						RogueChestsFcConfig.PluginMode.THIEVER
				);
			}
			else
			{
				beginModeTransition(
						RogueChestsFcConfig.PluginMode.NONE
				);
			}
		}
		else
		{
			beginModeTransition(
					RogueChestsFcConfig.PluginMode.NONE
			);
		}
	}

	private boolean isNotTransitioningTo(
			RogueChestsFcConfig.PluginMode targetMode)
	{
		return modeTransitionPhase == ModeTransitionPhase.NONE
				|| pendingTransitionMode != targetMode;
	}

	private boolean isThieverInfrastructureActive()
	{
		return authorizedFeaturesActive
				&& !staffFeaturesActive
				&& isThieverMode();
	}

	private boolean isStaffInfrastructureActive()
	{
		return authorizedFeaturesActive
				&& staffFeaturesActive
				&& isStaffMode()
				&& isAuthorized();
	}

	boolean isStaffFeaturesActive()
	{
		return isStaffInfrastructureActive()
				&& isInRequiredFriendsChat();
	}

	private boolean isThieverFeaturesInactive()
	{
		return !authorizedFeaturesActive
				|| !isInRequiredFriendsChat()
				|| staffFeaturesActive
				|| !isThieverMode();
	}

	boolean isAuthorized()
	{
		return isStaffRankAuthorized();
	}

	private boolean isInRequiredFriendsChat()
	{
		FriendsChatManager manager =
				client.getFriendsChatManager();

		if (manager == null
				|| manager.getName() == null)
		{
			return false;
		}

		return normalizeName(
				REQUIRED_FRIENDS_CHAT
		).equals(
				normalizeName(
						manager.getName()
				)
		);
	}

	private boolean isStaffRankAuthorized()
	{
		boolean inRequiredChat =
				isInRequiredFriendsChat();

		if (!inRequiredChat)
		{
			unauthorizedStaffRankObservedAt = null;

			return cachedStaffRankCheckedAt != null
					&& cachedStaffRankAuthorized;
		}

		Instant now = Instant.now();

		if (cachedStaffRankAuthorized
				&& cachedStaffRankCheckedAt != null
				&& Duration.between(
				cachedStaffRankCheckedAt,
				now
		).compareTo(
				STAFF_RANK_CACHE_DURATION
		) < 0)
		{
			return true;
		}

		FriendsChatManager manager =
				client.getFriendsChatManager();

		FriendsChatRank rank =
				manager == null
						? null
						: manager.getMyRank();

		if (rank == null)
		{
			return cachedStaffRankCheckedAt != null
					&& cachedStaffRankAuthorized;
		}

		boolean authorized;

		switch (rank)
		{
			case LIEUTENANT:
			case CAPTAIN:
			case GENERAL:
			case OWNER:
				authorized = true;
				break;
			default:
				authorized = false;
		}

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

			if (Duration.between(
					unauthorizedStaffRankObservedAt,
					now
			).compareTo(
					STAFF_RANK_REVOCATION_GRACE
			) < 0)
			{
				return true;
			}
		}

		cachedStaffRankAuthorized = false;
		cachedStaffRankCheckedAt = now;
		unauthorizedStaffRankObservedAt = null;

		return false;
	}

	private void applySyncedPartyCredential(
			SyncedPartyCredential credential)
			throws GeneralSecurityException
	{
		if (credential == null)
		{
			throw new GeneralSecurityException(
					"Missing Party credential"
			);
		}

		if (!credential.available)
		{
			configManager.setConfiguration(
					CONFIG_GROUP,
					PARTY_SYNC_VERSION_KEY,
					Integer.toString(
							credential.version
					)
			);

			configManager.unsetConfiguration(
					CONFIG_GROUP,
					PARTY_SYNC_NONCE_KEY
			);

			configManager.unsetConfiguration(
					CONFIG_GROUP,
					PARTY_SYNC_CIPHERTEXT_KEY
			);

			configManager.unsetConfiguration(
					CONFIG_GROUP,
					PARTY_SYNC_MAC_KEY
			);

			return;
		}

		String decrypted =
				decryptPartyCredential(
						credential
				);

		if (decrypted.isEmpty())
		{
			throw new GeneralSecurityException(
					"Party credential decrypted to an empty value"
			);
		}

		configManager.setConfiguration(
				CONFIG_GROUP,
				PARTY_SYNC_VERSION_KEY,
				Integer.toString(
						credential.version
				)
		);

		configManager.setConfiguration(
				CONFIG_GROUP,
				PARTY_SYNC_NONCE_KEY,
				credential.nonce
		);

		configManager.setConfiguration(
				CONFIG_GROUP,
				PARTY_SYNC_CIPHERTEXT_KEY,
				credential.ciphertext
		);

		configManager.setConfiguration(
				CONFIG_GROUP,
				PARTY_SYNC_MAC_KEY,
				credential.mac
		);
	}

	private String decryptPartyPassphrase()
	{
		String versionText =
				configManager.getConfiguration(
						CONFIG_GROUP,
						PARTY_SYNC_VERSION_KEY
				);

		String nonce =
				configManager.getConfiguration(
						CONFIG_GROUP,
						PARTY_SYNC_NONCE_KEY
				);

		String ciphertext =
				configManager.getConfiguration(
						CONFIG_GROUP,
						PARTY_SYNC_CIPHERTEXT_KEY
				);

		String mac =
				configManager.getConfiguration(
						CONFIG_GROUP,
						PARTY_SYNC_MAC_KEY
				);

		if (versionText == null
				|| nonce == null
				|| ciphertext == null
				|| mac == null)
		{
			return null;
		}

		try
		{
			int version =
					Integer.parseInt(
							versionText
					);

			return decryptPartyCredential(
					new SyncedPartyCredential(
							version,
							true,
							nonce,
							ciphertext,
							mac
					)
			);
		}
		catch (GeneralSecurityException
		       | IllegalArgumentException exception)
		{
			log.debug(
					"Unable to decrypt synced Party passphrase",
					exception
			);

			return null;
		}
	}

	private String decryptPartyCredential(
			SyncedPartyCredential credential)
			throws GeneralSecurityException
	{
		byte[] rootKey = null;
		byte[] encryptionKey = null;
		byte[] authenticationKey = null;
		byte[] nonce = null;
		byte[] ciphertext = null;
		byte[] plaintext = null;

		try
		{
			rootKey = Base64.getDecoder().decode(
					PARTY_SYNC_ROOT_KEY_BASE64
			);

			nonce = Base64.getDecoder().decode(
					credential.nonce
			);

			ciphertext = Base64.getDecoder().decode(
					credential.ciphertext
			);

			byte[] suppliedMac =
					Base64.getDecoder().decode(
							credential.mac
					);

			encryptionKey = hmacSha256(
					rootKey,
					"party-sync-encryption"
							.getBytes(
									StandardCharsets.UTF_8
							)
			);

			authenticationKey = hmacSha256(
					rootKey,
					"party-sync-authentication"
							.getBytes(
									StandardCharsets.UTF_8
							)
			);

			String macMessage =
					credential.version
							+ "|"
							+ credential.nonce
							+ "|"
							+ credential.ciphertext;

			byte[] expectedMac =
					hmacSha256(
							authenticationKey,
							macMessage.getBytes(
									StandardCharsets.UTF_8
							)
					);

			if (!MessageDigest.isEqual(
					expectedMac,
					suppliedMac
			))
			{
				throw new GeneralSecurityException(
						"Party credential authentication failed"
				);
			}

			plaintext = cryptPartyBytes(
					ciphertext,
					encryptionKey,
					nonce
			);

			return new String(
					plaintext,
					StandardCharsets.UTF_8
			);
		}
		catch (IllegalArgumentException exception)
		{
			throw new GeneralSecurityException(
					"Invalid Party credential encoding",
					exception
			);
		}
		finally
		{
			wipe(rootKey);
			wipe(encryptionKey);
			wipe(authenticationKey);
			wipe(nonce);
			wipe(ciphertext);
			wipe(plaintext);
		}
	}

	private byte[] cryptPartyBytes(
			byte[] input,
			byte[] encryptionKey,
			byte[] nonce)
			throws GeneralSecurityException
	{
		byte[] output =
				new byte[input.length];

		int offset = 0;
		int counter = 1;

		while (offset < input.length)
		{
			byte[] blockInput =
					new byte[
							nonce.length + 4
							];

			System.arraycopy(
					nonce,
					0,
					blockInput,
					0,
					nonce.length
			);

			int counterOffset =
					nonce.length;

			blockInput[counterOffset] =
					(byte) (
							counter >>> 24
					);

			blockInput[counterOffset + 1] =
					(byte) (
							counter >>> 16
					);

			blockInput[counterOffset + 2] =
					(byte) (
							counter >>> 8
					);

			blockInput[counterOffset + 3] =
					(byte) counter;

			byte[] keystream =
					hmacSha256(
							encryptionKey,
							blockInput
					);

			try
			{
				for (int index = 0;
				     index < keystream.length
							 && offset < input.length;
				     index++, offset++)
				{
					output[offset] =
							(byte) (
									input[offset]
											^ keystream[index]
							);
				}
			}
			finally
			{
				wipe(keystream);
				wipe(blockInput);
			}

			counter++;
		}

		return output;
	}

	private byte[] hmacSha256(
			byte[] key,
			byte[] data)
			throws GeneralSecurityException
	{
		Mac mac = Mac.getInstance(
				"HmacSHA256"
		);

		mac.init(
				new SecretKeySpec(
						key,
						"HmacSHA256"
				)
		);

		return mac.doFinal(data);
	}

	private void wipe(byte[] value)
	{
		if (value != null)
		{
			Arrays.fill(
					value,
					(byte) 0
			);
		}
	}

	private void updatePartyJoinBannerForLogin()
	{
		partyJoinBannerVisible =
				isStaffFeaturesActive()
						&& !partyService.isInParty();

		refreshPanel();
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
		refreshPanel();
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
			refreshPanel();
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
			refreshPanel();
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

	void addOvertimeWhitelistNames(String names)
	{
		addOvertimeWhitelistConfiguredNames(
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

		refreshPanel();
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

	private void queueCapturedNearbyName(String playerName)
	{
		String normalizedName = normalizeName(playerName);

		if (normalizedName.isEmpty()
				|| currentMembers.contains(normalizedName))
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
				new ArrayList<>(pendingCapturedNearbyNames.values());
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
				capturedNames,
				false
		);

		saveCapturedNearbyTimestamps(timestamps);
	}

	private void removeExpiredCapturedNearbyNamesIfDue()
	{
		long now = System.nanoTime();

		if (now - lastCapturedNearbyCleanupNanos
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

	private void refreshPanel()
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			panel.refresh();
		}
		else
		{
			SwingUtilities.invokeLater(
					panel::refresh
			);
		}
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

		boolean staffFeaturesActiveNow = isStaffFeaturesActive();
		Set<String> equipmentIgnoredNames =
				staffFeaturesActiveNow
						? getEquipmentInspectionIgnoredNames()
						: Collections.emptySet();

		int enemyCount = 0;
		int fcCount = 0;

		for (Player player : getVisiblePlayers())
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

			if (normalizedName.isEmpty())
			{
				continue;
			}

			boolean isLocalPlayer =
					normalizedName.equals(localPlayerName);
			boolean isCurrentMember =
					currentMembers.contains(normalizedName);

			if (isLocalPlayer || isCurrentMember)
			{
				fcCount++;
			}
			else
			{
				enemyCount++;
			}

			if (isLocalPlayer || !isCurrentMember)
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

		cachedNearbyEnemyCount = enemyCount;
		cachedNearbyFcCount = fcCount;
	}

	private void scanEquipmentIfNeeded(
			Player player,
			String normalizedName,
			String playerName)
	{
		if (equipmentScannedVisibleMembers.contains(
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

		showHighValueVisibleEquipmentNotification(
				playerName,
				composition
		);

		if (!config.showMissingEquipmentWarning())
		{
			return;
		}

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

	private void showHighValueVisibleEquipmentNotification(
			String playerName,
			PlayerComposition composition)
	{
		int[] equipmentIds = composition.getEquipmentIds();

		if (equipmentIds == null
				|| equipmentIds.length == 0)
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

			if (price > HIGH_VALUE_VISIBLE_ITEM_GP
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
				itemManager.getItemComposition(highestItemId).getName();

		String message =
				new ChatMessageBuilder()
						.append(
								Color.RED,
								Text.toJagexName(playerName)
						)
						.append(" is wearing ")
						.append(
								Color.RED,
								itemName
						)
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

		if (itemComposition.getName() == null)
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

	private Iterable<? extends Player> getVisiblePlayers()
	{
		WorldView worldView = client.getTopLevelWorldView();
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
				|| !TRACKING_REGION_IDS.contains(
				localPlayer.getWorldLocation().getRegionID()
		);
	}

	int getNearbyEnemyCount()
	{
		return cachedNearbyEnemyCount;
	}

	int getNearbyFcCount()
	{
		return cachedNearbyFcCount;
	}

	private void addOvertimeWhitelistConfiguredNames(
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
								normalizeName(
										name
								),
								name
						)
				);

		saveConfiguredNames(
				OVERTIME_WHITELIST_NAMES_KEY,
				namesByNormalizedName,
				false
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

		refreshPanel();

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

	private void queueCurrentMembersForThieverMode()
	{
		clientThread.invokeLater(() ->
		{
			if (isThieverFeaturesInactive())
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
			if (!isStaffFeaturesActive())
			{
				return true;
			}

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

	private void loadHighLevelCache()
	{
		synchronized (highLevelCacheFileLock)
		{
			knownHighLevelPlayers.clear();
			highLevelCacheDirty = false;
			lastHighLevelCacheWriteNanos = System.nanoTime();

			File cacheDirectory =
					new File(
							RuneLite.RUNELITE_DIR,
							HIGH_LEVEL_CACHE_DIRECTORY
					);

			highLevelCacheFile =
					new File(
							cacheDirectory,
							HIGH_LEVEL_CACHE_FILENAME
					);

			if (!highLevelCacheFile.exists())
			{
				return;
			}

			Instant now = Instant.now();

			try
			{
				for (String line :
						Files.readAllLines(
								highLevelCacheFile.toPath(),
								StandardCharsets.UTF_8
						))
				{
					if (line.trim().isEmpty())
					{
						continue;
					}

					int separatorIndex =
							line.lastIndexOf('|');

					if (separatorIndex <= 0
							|| separatorIndex
							>= line.length() - 1)
					{
						highLevelCacheDirty = true;
						continue;
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
						highLevelCacheDirty = true;
						continue;
					}

					try
					{
						long verifiedEpochMilli =
								Long.parseLong(
										line.substring(
												separatorIndex + 1
										)
								);

						Instant verifiedAt =
								Instant.ofEpochMilli(
										verifiedEpochMilli
								);

						if (Duration.between(
								verifiedAt,
								now
						).compareTo(
								HIGH_LEVEL_CACHE_TTL
						) < 0)
						{
							knownHighLevelPlayers.put(
									normalizedName,
									verifiedAt
							);
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
				log.debug(
						"Unable to load 84+ Thieving cache",
						exception
				);

				knownHighLevelPlayers.clear();
				return;
			}

			// Clean malformed/expired entries once at startup.
			persistHighLevelCacheIfDirtyLocked();
		}
	}

	private boolean isKnownHighLevelPlayer(
			String normalizedName)
	{
		if (normalizedName == null
				|| normalizedName.isEmpty())
		{
			return false;
		}

		Instant verifiedAt =
				knownHighLevelPlayers.get(
						normalizedName
				);

		if (verifiedAt == null)
		{
			return false;
		}

		if (Duration.between(
				verifiedAt,
				Instant.now()
		).compareTo(
				HIGH_LEVEL_CACHE_TTL
		) < 0)
		{
			return true;
		}

		if (knownHighLevelPlayers.remove(
				normalizedName,
				verifiedAt
		))
		{
			highLevelCacheDirty = true;
		}

		return false;
	}

	private void rememberHighLevelPlayer(
			String normalizedName,
			Instant verifiedAt)
	{
		if (normalizedName == null
				|| normalizedName.isEmpty()
				|| verifiedAt == null)
		{
			return;
		}

		knownHighLevelPlayers.put(
				normalizedName,
				verifiedAt
		);

		highLevelCacheDirty = true;
	}

	private void forgetHighLevelPlayer(
			String normalizedName)
	{
		if (normalizedName == null
				|| normalizedName.isEmpty())
		{
			return;
		}

		if (knownHighLevelPlayers.remove(
				normalizedName
		) != null)
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

		if (lastHighLevelCacheWriteNanos != 0
				&& now - lastHighLevelCacheWriteNanos
				< HIGH_LEVEL_CACHE_WRITE_INTERVAL_NANOS)
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
		if (!highLevelCacheDirty
				|| highLevelCacheFile == null)
		{
			return;
		}

		File cacheDirectory =
				highLevelCacheFile.getParentFile();

		try
		{
			if (cacheDirectory != null)
			{
				Files.createDirectories(
						cacheDirectory.toPath()
				);
			}

			List<String> lines =
					new ArrayList<>();

			Map<String, Instant> sortedEntries =
					new TreeMap<>(
							knownHighLevelPlayers
					);

			for (Map.Entry<String, Instant> entry :
					sortedEntries.entrySet())
			{
				lines.add(
						entry.getKey()
								+ "|"
								+ entry.getValue()
								.toEpochMilli()
				);
			}

			Files.write(
					highLevelCacheFile.toPath(),
					lines,
					StandardCharsets.UTF_8
			);

			highLevelCacheDirty = false;
		}
		catch (IOException exception)
		{
			log.debug(
					"Unable to save 84+ Thieving cache",
					exception
			);
		}
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

		// F2P members still go through Hiscores so their existing F2P
		// join/level behavior remains unchanged. For normal members,
		// a recent 84+ verification lets us skip the network lookup.
		if (!unrankedF2pMembers.contains(normalizedName)
				&& isKnownHighLevelPlayer(normalizedName))
		{
			pendingJoinMessages.remove(normalizedName);

			boolean hadLiveLevel =
					thievingLevels.remove(normalizedName) != null;

			boolean hadLowLevelEntry =
					lowLevelMembers.remove(normalizedName) != null;

			if (hadLiveLevel || hadLowLevelEntry)
			{
				clientThread.invoke(
						this::applyLevelsToMemberList
				);
			}

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

		if (result == null
				|| !isStaffFeaturesActive())
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
		Instant now = Instant.now();

		lastLookupTimes.put(
				normalizedName,
				now
		);

		// Preserve the existing F2P notification behavior before deciding
		// whether the Thieving level itself needs to stay in live memory.
		showF2pJoinMessage(
				normalizedName,
				playerName,
				level
		);

		// This also consumes any pending low-level join notification.
		showLowLevelJoinMessage(
				normalizedName,
				playerName,
				level
		);

		boolean unrankedF2p =
				unrankedF2pMembers.contains(
						normalizedName
				);

		if (level >= REQUIRED_THIEVING_LEVEL)
		{
			rememberHighLevelPlayer(
					normalizedName,
					now
			);

			// 84+ players do not need a painted Thieving level or a live
			// level entry. F2P members retain their special F2P panel state.
			thievingLevels.remove(normalizedName);

			if (unrankedF2p)
			{
				upsertLowLevelMember(
						normalizedName,
						playerName
				);
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

			return;
		}

		// A successful under-84 lookup supersedes any stale/reused-name
		// high-level cache entry.
		forgetHighLevelPlayer(
				normalizedName
		);

		thievingLevels.put(
				normalizedName,
				level
		);

		upsertLowLevelMember(
				normalizedName,
				playerName
		);

		clientThread.invoke(
				this::applyLevelsToMemberList
		);
	}

	private void upsertLowLevelMember(
			String normalizedName,
			String playerName)
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
		if (!isInRequiredFriendsChat())
		{
			return;
		}

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
		if (!isStaffFeaturesActive())
		{
			return;
		}

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
		if (cannotTrackNearbyMembers())
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

		if (!Objects.equals(
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

		if (!Objects.equals(
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

		if (!Objects.equals(
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

		if (!Objects.equals(
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
		if (!isStaffFeaturesActive())
		{
			return;
		}

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
			rowPositions.add(row.baseY);
		}

		rowPositions.sort(Integer::compareTo);

		rows.sort(
				Comparator.comparingInt(
						row -> row.priority
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


	private enum ModeTransitionPhase
	{
		NONE,
		DEACTIVATE_OVERLAYS,
		DEACTIVATE_RUNTIME,
		DEACTIVATE_WIDGETS,
		PREPARE_ACTIVATION,
		ACTIVATE_OVERLAYS,
		ACTIVATE_BACKGROUND,
		ACTIVATE_DATA,
		ACTIVATE_UI
	}

	private static class SyncedPlayerLists
	{
		private final List<String> bannedNames;
		private final List<String> ignoredNames;
		private final SyncedPartyCredential partyCredential;

		SyncedPlayerLists(
				List<String> bannedNames,
				List<String> ignoredNames,
				SyncedPartyCredential partyCredential)
		{
			this.bannedNames = bannedNames;
			this.ignoredNames = ignoredNames;
			this.partyCredential = partyCredential;
		}

	}

	private static class SyncedPartyCredential
	{
		private final int version;
		private final boolean available;
		private final String nonce;
		private final String ciphertext;
		private final String mac;

		SyncedPartyCredential(
				int version,
				boolean available,
				String nonce,
				String ciphertext,
				String mac)
		{
			this.version = version;
			this.available = available;
			this.nonce = nonce;
			this.ciphertext = ciphertext;
			this.mac = mac;
		}

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