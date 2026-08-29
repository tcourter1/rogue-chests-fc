package com.roguechestsfc;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

public class RogueChestsFcEnemyOverlay extends OverlayPanel
{
    private final RogueChestsFcPlugin plugin;
    private final RogueChestsFcConfig config;

    @Inject
    public RogueChestsFcEnemyOverlay(
            RogueChestsFcPlugin plugin,
            RogueChestsFcConfig config)
    {
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);

        panelComponent.setPreferredSize(
                new Dimension(80, 0)
        );

        panelComponent.setBorder(
                new Rectangle(4, 2, 4, 2)
        );
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showEnemyCounter())
        {
            return null;
        }

        int enemyCount = plugin.getNearbyEnemyCount();
        int fcCount = plugin.getNearbyFcCount();

        if (enemyCount < 0 || fcCount < 0)
        {
            return null;
        }

        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Enemies")
                        .right(
                                Integer.toString(
                                        enemyCount
                                )
                        )
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("FC")
                        .right(
                                Integer.toString(
                                        fcCount
                                )
                        )
                        .build()
        );

        return super.render(graphics);
    }
}