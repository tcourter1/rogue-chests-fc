package com.roguechestsfc;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;

public class RogueChestsFcOverlay extends OverlayPanel
{
    private static final String TITLE = "Under 84 Thieving";
    private static final int PANEL_PADDING = 20;
    private static final int MINIMUM_WIDTH = 120;

    private final RogueChestsFcPlugin plugin;
    private final RogueChestsFcConfig config;

    @Inject
    private RogueChestsFcOverlay(
            RogueChestsFcPlugin plugin,
            RogueChestsFcConfig config)
    {
        super(plugin);

        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.MED);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showLowLevelPanel())
        {
            return null;
        }

        List<RogueChestsFcPlugin.LowLevelMember> members =
                plugin.getLowLevelMembers();

        if (members.isEmpty())
        {
            return null;
        }

        Font normalFont = createFont(false);
        Font italicFont = createFont(true);
        Font titleFont = normalFont.deriveFont(Font.BOLD);

        FontMetrics normalMetrics = graphics.getFontMetrics(normalFont);
        FontMetrics italicMetrics = graphics.getFontMetrics(italicFont);
        FontMetrics titleMetrics = graphics.getFontMetrics(titleFont);

        int width = titleMetrics.stringWidth(TITLE);

        for (RogueChestsFcPlugin.LowLevelMember member : members)
        {
            FontMetrics metrics = member.isDeparted()
                    ? italicMetrics
                    : normalMetrics;

            width = Math.max(width, metrics.stringWidth(member.getName()));
        }

        panelComponent.setPreferredSize(new Dimension(
                Math.max(MINIMUM_WIDTH, width + PANEL_PADDING),
                0
        ));

        panelComponent.setBackgroundColor(config.panelBackgroundColor());

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left(TITLE)
                        .leftColor(config.panelFontColor())
                        .leftFont(titleFont)
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left(" ")
                        .leftFont(normalFont)
                        .build()
        );

        for (RogueChestsFcPlugin.LowLevelMember member : members)
        {
            panelComponent.getChildren().add(
                    LineComponent.builder()
                            .left(member.getName())
                            .leftColor(config.panelFontColor())
                            .leftFont(member.isDeparted()
                                    ? italicFont
                                    : normalFont)
                            .build()
            );
        }

        return super.render(graphics);
    }

    private Font createFont(boolean italic)
    {
        int style = italic ? Font.ITALIC : Font.PLAIN;
        float size = config.panelFontSize();

        if (config.panelFont() == RogueChestsFcConfig.PanelFont.ARIAL)
        {
            return new Font("Arial", style, config.panelFontSize());
        }

        return FontManager.getRunescapeFont()
                .deriveFont(style, size);
    }
}