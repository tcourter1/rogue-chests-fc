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
            name = "Under 84 Panel",
            description = "Settings for the under 84 Thieving member panel",
            position = 0
    )
    String lowLevelPanelSection = "lowLevelPanelSection";

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
            keyName = "ignoredNames",
            name = "Ignored names",
            description = "RSNs excluded from the low-level panel. Separate multiple names with commas or new lines",
            position = 5,
            section = lowLevelPanelSection
    )
    default String ignoredNames()
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