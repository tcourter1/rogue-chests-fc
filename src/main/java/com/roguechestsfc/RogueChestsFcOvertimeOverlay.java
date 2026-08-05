package com.roguechestsfc;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.time.Duration;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

public class RogueChestsFcOvertimeOverlay extends OverlayPanel
{
    private static final String TITLE = "Overtime Tracker";
    private static final int PANEL_PADDING = 20;
    private static final int MINIMUM_WIDTH = 140;

    private final RogueChestsFcPlugin plugin;
    private final RogueChestsFcConfig config;

    @Inject
    private RogueChestsFcOvertimeOverlay(
            RogueChestsFcPlugin plugin,
            RogueChestsFcConfig config)
    {
        super(plugin);

        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(Overlay.PRIORITY_HIGHEST);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOvertimePanel())
        {
            return null;
        }

        List<RogueChestsFcPlugin.OvertimeMember> members =
                plugin.getOvertimeMembers();

        if (members.isEmpty())
        {
            return null;
        }

        Font normalFont = createFont();
        Font pausedFont = normalFont.deriveFont(Font.ITALIC);
        Font titleFont = normalFont.deriveFont(Font.BOLD);

        FontMetrics normalMetrics =
                graphics.getFontMetrics(normalFont);

        FontMetrics pausedMetrics =
                graphics.getFontMetrics(pausedFont);

        FontMetrics titleMetrics =
                graphics.getFontMetrics(titleFont);

        int width = titleMetrics.stringWidth(TITLE);

        String[] formattedElapsed =
                new String[members.size()];

        for (int i = 0; i < members.size(); i++)
        {
            RogueChestsFcPlugin.OvertimeMember member =
                    members.get(i);

            String elapsed = format(member.getElapsed());
            formattedElapsed[i] = elapsed;

            FontMetrics memberMetrics =
                    member.isPaused()
                            ? pausedMetrics
                            : normalMetrics;

            width = Math.max(
                    width,
                    memberMetrics.stringWidth(
                            member.getName() + elapsed
                    )
            );
        }

        panelComponent.setPreferredSize(
                new Dimension(
                        Math.max(
                                MINIMUM_WIDTH,
                                width + PANEL_PADDING
                        ),
                        0
                )
        );

        panelComponent.setBackgroundColor(
                config.overtimePanelBackgroundColor()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left(TITLE)
                        .leftColor(
                                config.overtimePanelFontColor()
                        )
                        .leftFont(titleFont)
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left(" ")
                        .build()
        );

        for (int i = 0; i < members.size(); i++)
        {
            RogueChestsFcPlugin.OvertimeMember member =
                    members.get(i);

            Font memberFont =
                    member.isPaused()
                            ? pausedFont
                            : normalFont;

            panelComponent.getChildren().add(
                    LineComponent.builder()
                            .left(member.getName())
                            .right(formattedElapsed[i])
                            .leftFont(memberFont)
                            .rightFont(memberFont)
                            .leftColor(
                                    config.overtimePanelFontColor()
                            )
                            .rightColor(
                                    config.overtimePanelFontColor()
                            )
                            .build()
            );
        }

        return super.render(graphics);
    }

    private Font createFont()
    {
        if (config.overtimePanelFont()
                == RogueChestsFcConfig.PanelFont.ARIAL)
        {
            return new Font(
                    "Arial",
                    Font.PLAIN,
                    config.overtimePanelFontSize()
            );
        }

        return FontManager.getRunescapeFont().deriveFont(
                Font.PLAIN,
                (float) config.overtimePanelFontSize()
        );
    }

    private static String format(Duration duration)
    {
        long totalSeconds = duration.getSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        StringBuilder result = new StringBuilder(5);

        if (minutes < 10)
        {
            result.append('0');
        }

        result.append(minutes).append(':');

        if (seconds < 10)
        {
            result.append('0');
        }

        return result.append(seconds).toString();
    }
}