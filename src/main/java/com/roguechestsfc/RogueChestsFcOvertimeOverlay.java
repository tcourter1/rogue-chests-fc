package com.roguechestsfc;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.time.Duration;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;

public class RogueChestsFcOvertimeOverlay extends OverlayPanel
{
    private static final String TITLE = "Over 15 Minutes";
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
        setPriority(OverlayPriority.HIGH);
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
        Font titleFont = normalFont.deriveFont(Font.BOLD);

        FontMetrics normalMetrics =
                graphics.getFontMetrics(normalFont);

        FontMetrics titleMetrics =
                graphics.getFontMetrics(titleFont);

        int width = titleMetrics.stringWidth(TITLE);

        for (RogueChestsFcPlugin.OvertimeMember member : members)
        {
            String elapsed = format(member.getElapsed());

            width = Math.max(
                    width,
                    normalMetrics.stringWidth(
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

        for (RogueChestsFcPlugin.OvertimeMember member : members)
        {
            panelComponent.getChildren().add(
                    LineComponent.builder()
                            .left(member.getName())
                            .right(format(member.getElapsed()))
                            .leftFont(normalFont)
                            .rightFont(normalFont)
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
        long seconds = duration.getSeconds();

        return String.format(
                "%02d:%02d",
                seconds / 60,
                seconds % 60
        );
    }
}