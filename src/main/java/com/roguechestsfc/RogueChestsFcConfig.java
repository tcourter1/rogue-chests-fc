package com.roguechestsfc;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("roguechestsfc")
public interface RogueChestsFcConfig extends Config
{
    @ConfigSection(
            name = "General",
            description = "General Rogue Chests FC settings",
            position = 0
    )
    String generalSection = "generalSection";

    @ConfigSection(
            name = "Under 84 Panel",
            description = "Settings for the under 84 Thieving member panel",
            position = 1
    )
    String lowLevelPanelSection = "lowLevelPanelSection";

    @ConfigSection(
            name = "Overtime Panel",
            description = "Settings for tracking FC members who remain nearby too long",
            position = 2
    )
    String overtimePanelSection = "overtimePanelSection";

    @ConfigItem(
            keyName = "showLowLevelJoinMessage",
            name = "Join message",
            description = "Show a chat message when a member joins with less than 84 Thieving",
            position = 0,
            section = generalSection
    )
    default boolean showLowLevelJoinMessage()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showBannedJoinMessage",
            name = "Banned join message",
            description = "Show a chat message when a player from the banned names list joins the Friends Chat",
            position = 1,
            section = generalSection
    )
    default boolean showBannedJoinMessage()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showLowLevelPanel",
            name = "Show panel",
            description = "Show FC members with less than 84 Thieving",
            position = 0,
            section = lowLevelPanelSection
    )
    default boolean showLowLevelPanel()
    {
        return true;
    }

    @ConfigItem(
            keyName = "panelFont",
            name = "Font",
            description = "Choose the font used by the panel",
            position = 1,
            section = lowLevelPanelSection
    )
    default PanelFont panelFont()
    {
        return PanelFont.DEFAULT;
    }

    @Range(
            min = 10,
            max = 32
    )
    @ConfigItem(
            keyName = "panelFontSize",
            name = "Font size",
            description = "Set the font size used by the panel",
            position = 2,
            section = lowLevelPanelSection
    )
    default int panelFontSize()
    {
        return 14;
    }

    @ConfigItem(
            keyName = "panelFontColor",
            name = "Font color",
            description = "Set the color of names displayed in the panel",
            position = 3,
            section = lowLevelPanelSection
    )
    default Color panelFontColor()
    {
        return Color.WHITE;
    }

    @Alpha
    @ConfigItem(
            keyName = "panelBackgroundColor",
            name = "Background color",
            description = "Set the panel background color and transparency",
            position = 4,
            section = lowLevelPanelSection
    )
    default Color panelBackgroundColor()
    {
        return new Color(0, 0, 0, 150);
    }

    @ConfigItem(
            keyName = "showOvertimePanel",
            name = "Show panel",
            description = "Show FC members who remain within render distance longer than the configured limit",
            position = 0,
            section = overtimePanelSection
    )
    default boolean showOvertimePanel()
    {
        return true;
    }

    @ConfigItem(
            keyName = "overtimePanelFont",
            name = "Font",
            description = "Choose the font used by the overtime panel",
            position = 1,
            section = overtimePanelSection
    )
    default PanelFont overtimePanelFont()
    {
        return PanelFont.DEFAULT;
    }

    @Range(
            min = 10,
            max = 32
    )
    @ConfigItem(
            keyName = "overtimePanelFontSize",
            name = "Font size",
            description = "Set the font size used by the overtime panel",
            position = 2,
            section = overtimePanelSection
    )
    default int overtimePanelFontSize()
    {
        return 14;
    }

    @ConfigItem(
            keyName = "overtimePanelFontColor",
            name = "Font color",
            description = "Set the color of names and timers displayed in the overtime panel",
            position = 3,
            section = overtimePanelSection
    )
    default Color overtimePanelFontColor()
    {
        return Color.WHITE;
    }

    @Alpha
    @ConfigItem(
            keyName = "overtimePanelBackgroundColor",
            name = "Background color",
            description = "Set the overtime panel background color and transparency",
            position = 4,
            section = overtimePanelSection
    )
    default Color overtimePanelBackgroundColor()
    {
        return new Color(0, 0, 0, 150);
    }

    @Range(
            min = 1,
            max = 60
    )
    @ConfigItem(
            keyName = "overtimeMinutes",
            name = "Time limit",
            description = "Minutes a nearby FC member may remain before appearing in the overtime panel",
            position = 5,
            section = overtimePanelSection
    )
    default int overtimeMinutes()
    {
        return 15;
    }

    @ConfigItem(
            keyName = "showOvertimeNotification",
            name = "Chat notification",
            description = "Show a one-time chat message when a nearby FC member exceeds the time limit",
            position = 6,
            section = overtimePanelSection
    )
    default boolean showOvertimeNotification()
    {
        return true;
    }

    @ConfigItem(
            keyName = "ignoredNames",
            name = "",
            description = "",
            hidden = true
    )
    default String ignoredNames()
    {
        return "";
    }

    @ConfigItem(
            keyName = "bannedNames",
            name = "",
            description = "",
            hidden = true
    )
    default String bannedNames()
    {
        return "";
    }

    @ConfigItem(
            keyName = "capturedNearbyNames",
            name = "",
            description = "",
            hidden = true
    )
    default String capturedNearbyNames()
    {
        return "";
    }

    enum PanelFont
    {
        DEFAULT("Default"),
        ARIAL("Arial");

        private final String name;

        PanelFont(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }
}