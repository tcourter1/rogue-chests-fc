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
            name = "Nearby Outsiders",
            description = "Settings for automatically captured nearby non-FC players",
            position = 2
    )
    String nearbyOutsidersSection = "nearbyOutsidersSection";

    @ConfigSection(
            name = "Overtime Panel",
            description = "Settings for tracking FC members who remain nearby too long",
            position = 3
    )
    String overtimePanelSection = "overtimePanelSection";

    @ConfigSection(
            name = "Equipment Inspection",
            description = "Settings for checking nearby FC members for missing visible equipment",
            position = 4
    )
    String equipmentInspectionSection = "equipmentInspectionSection";

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
            keyName = "showF2pJoinMessage",
            name = "F2P join message",
            description = "Show a chat message when an unranked player joins the Friends Chat from a free-to-play world",
            position = 2,
            section = generalSection
    )
    default boolean showF2pJoinMessage()
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

    @Range(
            min = 1,
            max = 120
    )
    @ConfigItem(
            keyName = "nearbyOutsiderRetentionMinutes",
            name = "Clear after",
            description = "Minutes a captured nearby outsider remains in the side-panel list before being removed automatically",
            position = 0,
            section = nearbyOutsidersSection
    )
    default int nearbyOutsiderRetentionMinutes()
    {
        return 10;
    }

    @ConfigItem(
            keyName = "showOvertimePanel",
            name = "Show overtime panel",
            description = "Show FC members who remain within render distance longer than the configured limit",
            position = 0,
            section = overtimePanelSection
    )
    default boolean showOvertimePanel()
    {
        return true;
    }

    @Range(
            min = 1,
            max = 60
    )
    @ConfigItem(
            keyName = "overtimeMinutes",
            name = "Time limit",
            description = "Minutes a nearby FC member may remain before appearing in the overtime panel",
            position = 1,
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
            position = 2,
            section = overtimePanelSection
    )
    default boolean showOvertimeNotification()
    {
        return true;
    }

    @Range(
            min = 1,
            max = 300
    )
    @ConfigItem(
            keyName = "overtimeRenderGraceSeconds",
            name = "Outside render grace",
            description = "Seconds a tracked FC member may remain outside render distance before their overtime timer resets. Set to 0 to reset immediately",
            position = 3,
            section = overtimePanelSection
    )
    default int overtimeRenderGraceSeconds()
    {
        return 45;
    }

    @ConfigItem(
            keyName = "overtimePanelFont",
            name = "Font",
            description = "Choose the font used by the overtime panel",
            position = 4,
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
            position = 5,
            section = overtimePanelSection
    )
    default int overtimePanelFontSize()
    {
        return 14;
    }

    @ConfigItem(
            keyName = "overtimePanelFontColor",
            name = "Font color",
            description = "Set the font color used by the overtime panel",
            position = 6,
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
            position = 7,
            section = overtimePanelSection
    )
    default Color overtimePanelBackgroundColor()
    {
        return new Color(0, 0, 0, 150);
    }

    @ConfigItem(
            keyName = "showMissingEquipmentWarning",
            name = "Equipment warning",
            description = "Show a warning when a nearby FC member has too many empty visible equipment slots",
            position = 0,
            section = equipmentInspectionSection
    )
    default boolean showMissingEquipmentWarning()
    {
        return true;
    }

    @Range(
            min = 1,
            max = 9
    )
    @ConfigItem(
            keyName = "missingEquipmentThreshold",
            name = "Missing item threshold",
            description = "Number of empty visible equipment slots required to trigger a warning",
            position = 1,
            section = equipmentInspectionSection
    )
    default int missingEquipmentThreshold()
    {
        return 1;
    }

    @ConfigItem(
            keyName = "equipmentInspectionIgnoredNames",
            name = "Ignore list",
            description = "Players excluded from equipment inspection. Separate names with commas or new lines",
            position = 2,
            section = equipmentInspectionSection
    )
    default String equipmentInspectionIgnoredNames()
    {
        return "";
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

    @ConfigItem(
            keyName = "capturedNearbyNameTimes",
            name = "",
            description = "",
            hidden = true
    )
    default String capturedNearbyNameTimes()
    {
        return "";
    }

    @ConfigItem(
            keyName = "overtimeWhitelistNames",
            name = "",
            description = "",
            hidden = true
    )
    default String overtimeWhitelistNames()
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