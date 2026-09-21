package com.roguechestsfc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

public class RogueChestsFcListRenderer
{
    private static final int REQUIRED_THIEVING_LEVEL = 84;

    private static final String GREEN_LEVEL_MARKER = " <col=00ff00>";
    private static final String RED_LEVEL_MARKER = " <col=ff0000>";
    private static final String LEVEL_SUFFIX = "</col>";
    private static final String RED_TEXT_OPEN = "<col=ff0000>";
    private static final String TEXT_CLOSE = "</col>";
    private static final String BANNED_MEMBER_TEXT = " BAN";
    private static final String F2P_MEMBER_TEXT = " F2P";

    private final Client client;

    @Inject
    public RogueChestsFcListRenderer(Client client)
    {
        this.client = client;
    }

    void applyLevelsToMemberList(
            boolean staffFeaturesActive,
            Set<String> ignoredNames,
            Set<String> bannedNames,
            Set<String> unrankedF2pMembers,
            Map<String, Integer> thievingLevels,
            Consumer<String> bannedMemberHandler)
    {
        if (!staffFeaturesActive)
        {
            return;
        }

        Widget chatList = client.getWidget(
                InterfaceID.ChatchannelCurrent.LIST
        );

        if (chatList == null || chatList.getChildren() == null)
        {
            return;
        }

        Set<String> ignored = ignoredNames == null
                ? Collections.emptySet()
                : ignoredNames;

        Set<String> banned = bannedNames == null
                ? Collections.emptySet()
                : bannedNames;

        Set<String> f2pMembers = unrankedF2pMembers == null
                ? Collections.emptySet()
                : unrankedF2pMembers;

        Map<String, Integer> levels = thievingLevels == null
                ? Collections.emptyMap()
                : thievingLevels;

        Widget[] children = chatList.getChildren();
        List<FriendsChatRow> rows = new ArrayList<>((children.length + 2) / 3);

        for (int i = 0; i < children.length; i += 3)
        {
            Widget nameWidget = children[i];
            if (nameWidget == null)
            {
                continue;
            }

            String originalText = removePluginFormatting(nameWidget.getText());
            String playerName = Text.toJagexName(Text.removeTags(originalText));
            String normalizedName = normalizeName(playerName);

            rows.add(new FriendsChatRow(
                    children,
                    i,
                    nameWidget.getOriginalY(),
                    getFriendsChatSortPriority(
                            normalizedName,
                            ignored,
                            banned,
                            f2pMembers,
                            levels
                    )
            ));

            if (banned.contains(normalizedName))
            {
                if (bannedMemberHandler != null)
                {
                    bannedMemberHandler.accept(normalizedName);
                }

                nameWidget.setText(
                        RED_TEXT_OPEN
                                + originalText
                                + BANNED_MEMBER_TEXT
                                + TEXT_CLOSE
                );
                continue;
            }

            if (f2pMembers.contains(normalizedName))
            {
                String colorOpen = ignored.contains(normalizedName)
                        ? "<col=00ff00>"
                        : RED_TEXT_OPEN;

                nameWidget.setText(
                        colorOpen
                                + originalText
                                + F2P_MEMBER_TEXT
                                + TEXT_CLOSE
                );
                continue;
            }

            Integer thievingLevel = levels.get(normalizedName);
            if (thievingLevel == null)
            {
                nameWidget.setText(originalText);
                continue;
            }

            boolean showGreen = thievingLevel >= REQUIRED_THIEVING_LEVEL
                    || ignored.contains(normalizedName);

            nameWidget.setText(
                    originalText
                            + (showGreen ? GREEN_LEVEL_MARKER : RED_LEVEL_MARKER)
                            + thievingLevel
                            + LEVEL_SUFFIX
            );
        }

        sortFriendsChatRows(rows);
    }

    void removeLevelsFromMemberList()
    {
        Widget chatList = client.getWidget(
                InterfaceID.ChatchannelCurrent.LIST
        );

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

            String currentText = nameWidget.getText();
            String cleanedText = removePluginFormatting(currentText);

            if (!cleanedText.equals(currentText))
            {
                nameWidget.setText(cleanedText);
            }
        }
    }

    private int getFriendsChatSortPriority(
            String normalizedName,
            Set<String> ignoredNames,
            Set<String> bannedNames,
            Set<String> unrankedF2pMembers,
            Map<String, Integer> thievingLevels)
    {
        if (bannedNames.contains(normalizedName))
        {
            return 0;
        }

        if (unrankedF2pMembers.contains(normalizedName)
                && !ignoredNames.contains(normalizedName))
        {
            return 1;
        }

        Integer thievingLevel = thievingLevels.get(normalizedName);
        if (thievingLevel != null
                && thievingLevel < REQUIRED_THIEVING_LEVEL
                && !ignoredNames.contains(normalizedName))
        {
            return 2;
        }

        return 3;
    }

    private void sortFriendsChatRows(List<FriendsChatRow> rows)
    {
        if (rows.size() < 2)
        {
            return;
        }

        int[] rowPositions = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++)
        {
            rowPositions[i] = rows.get(i).baseY;
        }

        Arrays.sort(rowPositions);
        rows.sort(Comparator.comparingInt(row -> row.priority));

        for (int i = 0; i < rows.size(); i++)
        {
            rows.get(i).moveTo(rowPositions[i]);
        }
    }

    private String removePluginFormatting(String text)
    {
        if (text == null)
        {
            return "";
        }

        String plainText = Text.removeTags(removeLevelFromText(text));

        if (plainText.endsWith(BANNED_MEMBER_TEXT))
        {
            return plainText.substring(
                    0,
                    plainText.length() - BANNED_MEMBER_TEXT.length()
            );
        }

        if (plainText.endsWith(F2P_MEMBER_TEXT))
        {
            return plainText.substring(
                    0,
                    plainText.length() - F2P_MEMBER_TEXT.length()
            );
        }

        return plainText;
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

    private static final class FriendsChatRow
    {
        private final Widget nameWidget;
        private final Widget rankWidget;
        private final Widget worldWidget;
        private final int baseY;
        private final int priority;

        private FriendsChatRow(
                Widget[] children,
                int startIndex,
                int baseY,
                int priority)
        {
            this.nameWidget = children[startIndex];
            this.rankWidget = startIndex + 1 < children.length
                    ? children[startIndex + 1]
                    : null;
            this.worldWidget = startIndex + 2 < children.length
                    ? children[startIndex + 2]
                    : null;
            this.baseY = baseY;
            this.priority = priority;
        }

        private void moveTo(int targetY)
        {
            moveWidget(nameWidget, targetY);
            moveWidget(rankWidget, targetY);
            moveWidget(worldWidget, targetY);
        }

        private void moveWidget(Widget widget, int targetY)
        {
            if (widget == null)
            {
                return;
            }

            int offset = widget.getOriginalY() - baseY;
            widget.setOriginalY(targetY + offset);
            widget.revalidate();
        }
    }
}
