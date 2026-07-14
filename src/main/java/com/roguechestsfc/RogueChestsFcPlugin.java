package com.roguechestsfc;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.ScriptID;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.FriendsChatMemberJoined;
import net.runelite.api.events.FriendsChatMemberLeft;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
		name = "Rogue Chests FC",
		description = "Displays the Thieving levels of players in the Friends Chat member list",
		tags = {"rogue", "chests", "fc", "friends chat", "thieving"}
)
public class RogueChestsFcPlugin extends Plugin
{
	private static final Duration LOOKUP_COOLDOWN = Duration.ofMinutes(2);
	private static final Duration DEPARTED_DISPLAY_DURATION = Duration.ofMinutes(1);
	private static final int LOOKUPS_PER_TICK = 5;
	private static final int REQUIRED_THIEVING_LEVEL = 84;

	private static final String GREEN_LEVEL_MARKER = " <col=00ff00>";
	private static final String RED_LEVEL_MARKER = " <col=ff0000>";
	private static final String LEVEL_SUFFIX = "</col>";

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
	private RogueChestsFcConfig config;

	private final Map<String, Integer> thievingLevels = new ConcurrentHashMap<>();
	private final Map<String, Instant> lastLookupTimes = new ConcurrentHashMap<>();
	private final Map<String, String> displayNames = new ConcurrentHashMap<>();
	private final Map<String, LowLevelMember> lowLevelMembers = new ConcurrentHashMap<>();
	private final Set<String> currentMembers = ConcurrentHashMap.newKeySet();
	private final Set<String> pendingLookups = ConcurrentHashMap.newKeySet();
	private final ConcurrentLinkedQueue<String> lookupQueue = new ConcurrentLinkedQueue<>();

	@Provides
	RogueChestsFcConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RogueChestsFcConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		queueCurrentMembersWhenAvailable();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);

		lookupQueue.clear();
		pendingLookups.clear();
		displayNames.clear();
		lastLookupTimes.clear();
		thievingLevels.clear();
		lowLevelMembers.clear();
		currentMembers.clear();

		clientThread.invoke(this::removeLevelsFromMemberList);
	}

	@Subscribe
	public void onFriendsChatChanged(FriendsChatChanged event)
	{
		if (event.isJoined())
		{
			queueCurrentMembersWhenAvailable();
		}
		else
		{
			currentMembers.clear();
			markAllMembersDeparted();
		}
	}

	@Subscribe
	public void onFriendsChatMemberJoined(FriendsChatMemberJoined event)
	{
		FriendsChatMember member = event.getMember();

		if (member == null)
		{
			return;
		}

		String normalizedName = normalizeName(member.getName());

		if (normalizedName.isEmpty())
		{
			return;
		}

		currentMembers.add(normalizedName);

		LowLevelMember lowLevelMember = lowLevelMembers.get(normalizedName);

		if (lowLevelMember != null)
		{
			lowLevelMember.setDepartedAt(null);
		}

		queueLookup(member.getName());
	}

	@Subscribe
	public void onFriendsChatMemberLeft(FriendsChatMemberLeft event)
	{
		FriendsChatMember member = event.getMember();

		if (member == null)
		{
			return;
		}

		String normalizedName = normalizeName(member.getName());
		currentMembers.remove(normalizedName);

		LowLevelMember lowLevelMember = lowLevelMembers.get(normalizedName);

		if (lowLevelMember != null)
		{
			lowLevelMember.setDepartedAt(Instant.now());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
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

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.FRIENDS_CHAT_CHANNEL_REBUILD)
		{
			applyLevelsToMemberList();
		}
	}

	private void queueCurrentMembersWhenAvailable()
	{
		clientThread.invokeLater(() ->
		{
			FriendsChatManager friendsChatManager = client.getFriendsChatManager();

			if (friendsChatManager == null)
			{
				return true;
			}

			FriendsChatMember[] members = friendsChatManager.getMembers();

			if (members == null || members.length == 0)
			{
				return false;
			}

			Set<String> loadedMembers = ConcurrentHashMap.newKeySet();

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

				LowLevelMember lowLevelMember = lowLevelMembers.get(normalizedName);

				if (lowLevelMember != null)
				{
					lowLevelMember.setDepartedAt(null);
				}

				queueLookup(member.getName());
			}

			for (String normalizedName : new ArrayList<>(currentMembers))
			{
				if (!loadedMembers.contains(normalizedName))
				{
					currentMembers.remove(normalizedName);
					markMemberDeparted(normalizedName);
				}
			}

			applyLevelsToMemberList();
			return true;
		});
	}

	private void queueLookup(String playerName)
	{
		if (playerName == null || playerName.trim().isEmpty())
		{
			return;
		}

		String normalizedName = normalizeName(playerName);

		if (normalizedName.isEmpty())
		{
			return;
		}

		Instant now = Instant.now();
		Instant lastLookup = lastLookupTimes.get(normalizedName);

		if (lastLookup != null
				&& Duration.between(lastLookup, now).compareTo(LOOKUP_COOLDOWN) < 0)
		{
			updateLowLevelMemberFromCache(normalizedName, playerName);
			applyLevelsToMemberList();
			return;
		}

		if (!pendingLookups.add(normalizedName))
		{
			return;
		}

		lastLookupTimes.put(normalizedName, now);
		displayNames.put(normalizedName, Text.toJagexName(playerName));
		lookupQueue.add(normalizedName);
	}

	private void startLookup(String normalizedName)
	{
		String playerName = displayNames.getOrDefault(normalizedName, normalizedName);

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
		if (result == null)
		{
			return;
		}

		Skill thieving = result.getSkill(HiscoreSkill.THIEVING);

		if (thieving == null || thieving.getLevel() < 1)
		{
			return;
		}

		int level = thieving.getLevel();
		thievingLevels.put(normalizedName, level);

		if (level < REQUIRED_THIEVING_LEVEL)
		{
			Instant departedAt = currentMembers.contains(normalizedName)
					? null
					: Instant.now();

			lowLevelMembers.compute(normalizedName, (key, existing) ->
			{
				if (existing == null)
				{
					return new LowLevelMember(
							Text.toJagexName(playerName),
							departedAt
					);
				}

				existing.setName(Text.toJagexName(playerName));
				existing.setDepartedAt(departedAt);
				return existing;
			});
		}
		else
		{
			lowLevelMembers.remove(normalizedName);
		}

		clientThread.invoke(this::applyLevelsToMemberList);
	}

	private void updateLowLevelMemberFromCache(
			String normalizedName,
			String playerName)
	{
		Integer level = thievingLevels.get(normalizedName);

		if (level == null || level >= REQUIRED_THIEVING_LEVEL)
		{
			return;
		}

		lowLevelMembers.compute(normalizedName, (key, existing) ->
		{
			if (existing == null)
			{
				return new LowLevelMember(
						Text.toJagexName(playerName),
						currentMembers.contains(normalizedName) ? null : Instant.now()
				);
			}

			existing.setName(Text.toJagexName(playerName));

			if (currentMembers.contains(normalizedName))
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

	private void markAllMembersDeparted()
	{
		Instant now = Instant.now();

		for (LowLevelMember member : lowLevelMembers.values())
		{
			if (!member.isDeparted())
			{
				member.setDepartedAt(now);
			}
		}
	}

	private void removeExpiredDepartedMembers()
	{
		Instant now = Instant.now();

		lowLevelMembers.entrySet().removeIf(entry ->
		{
			LowLevelMember member = entry.getValue();
			Instant departedAt = member.getDepartedAt();

			return departedAt != null
					&& Duration.between(departedAt, now)
					.compareTo(DEPARTED_DISPLAY_DURATION) >= 0;
		});
	}

	List<LowLevelMember> getLowLevelMembers()
	{
		Set<String> ignoredNames = getIgnoredNames();
		List<LowLevelMember> members = new ArrayList<>();

		for (Map.Entry<String, LowLevelMember> entry : lowLevelMembers.entrySet())
		{
			if (!ignoredNames.contains(entry.getKey()))
			{
				members.add(entry.getValue());
			}
		}

		members.sort(
				Comparator.comparing(LowLevelMember::isDeparted)
						.thenComparing(
								LowLevelMember::getName,
								String.CASE_INSENSITIVE_ORDER
						)
		);

		return members;
	}

	private Set<String> getIgnoredNames()
	{
		Set<String> ignoredNames = new HashSet<>();
		String configuredNames = config.ignoredNames();

		if (configuredNames == null || configuredNames.trim().isEmpty())
		{
			return ignoredNames;
		}

		Arrays.stream(configuredNames.split("[,\\r\\n]+"))
				.map(String::trim)
				.map(this::normalizeName)
				.filter(name -> !name.isEmpty())
				.forEach(ignoredNames::add);

		return ignoredNames;
	}

	private void applyLevelsToMemberList()
	{
		Widget chatList =
				client.getWidget(InterfaceID.ChatchannelCurrent.LIST);

		if (chatList == null || chatList.getChildren() == null)
		{
			return;
		}

		Widget[] children = chatList.getChildren();

		for (int i = 0; i < children.length; i += 3)
		{
			Widget nameWidget = children[i];

			if (nameWidget == null)
			{
				continue;
			}

			String originalText = removeLevelFromText(nameWidget.getText());
			String normalizedName =
					normalizeName(Text.removeTags(originalText));
			Integer thievingLevel = thievingLevels.get(normalizedName);

			if (thievingLevel == null)
			{
				nameWidget.setText(originalText);
				continue;
			}

			String levelMarker =
					thievingLevel >= REQUIRED_THIEVING_LEVEL
							? GREEN_LEVEL_MARKER
							: RED_LEVEL_MARKER;

			nameWidget.setText(
					originalText
							+ levelMarker
							+ thievingLevel
							+ LEVEL_SUFFIX
			);
		}
	}

	private void removeLevelsFromMemberList()
	{
		Widget chatList =
				client.getWidget(InterfaceID.ChatchannelCurrent.LIST);

		if (chatList == null || chatList.getChildren() == null)
		{
			return;
		}

		Widget[] children = chatList.getChildren();

		for (int i = 0; i < children.length; i += 3)
		{
			Widget nameWidget = children[i];

			if (nameWidget != null)
			{
				nameWidget.setText(
						removeLevelFromText(nameWidget.getText())
				);
			}
		}
	}

	private String removeLevelFromText(String text)
	{
		if (text == null)
		{
			return "";
		}

		int greenIndex = text.indexOf(GREEN_LEVEL_MARKER);
		int redIndex = text.indexOf(RED_LEVEL_MARKER);
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
			markerIndex = Math.min(greenIndex, redIndex);
		}

		return markerIndex == -1
				? text
				: text.substring(0, markerIndex);
	}

	private String normalizeName(String playerName)
	{
		if (playerName == null)
		{
			return "";
		}

		return Text.toJagexName(Text.removeTags(playerName))
				.toLowerCase(Locale.ROOT);
	}

	static class LowLevelMember
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