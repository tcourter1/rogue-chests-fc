package com.roguechestsfc;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

public class RogueChestsFcPartyOverlay extends OverlayPanel
{
    private static final Color BACKGROUND_COLOR =
            new Color(160, 0, 0, 220);

    private static final Color TEXT_COLOR =
            Color.WHITE;

    private static final int OVERLAY_WIDTH = 280;

    private final RogueChestsFcPlugin plugin;

    @Inject
    public RogueChestsFcPartyOverlay(
            RogueChestsFcPlugin plugin)
    {
        this.plugin = plugin;

        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(Overlay.PRIORITY_HIGH);

        panelComponent.setBackgroundColor(
                BACKGROUND_COLOR
        );

        panelComponent.setPreferredSize(
                new Dimension(
                        OVERLAY_WIDTH,
                        0
                )
        );
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.shouldShowPartyJoinBanner())
        {
            return null;
        }

        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("NOT IN PARTY PLUGIN PARTY")
                        .leftColor(TEXT_COLOR)
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("CLICK \"JOIN\" OR \"NOT NOW\" IN SIDE PANEL")
                        .leftColor(TEXT_COLOR)
                        .build()
        );

        return super.render(graphics);
    }
}