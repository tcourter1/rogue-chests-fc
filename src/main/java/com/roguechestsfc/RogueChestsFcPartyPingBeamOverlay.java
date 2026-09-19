package com.roguechestsfc;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public class RogueChestsFcPartyPingBeamOverlay extends Overlay
{
    private static final long PING_LIFETIME_MILLIS = 1_020L;

    private static final int BEAM_HEIGHT = 110;
    private static final int BEAM_BOTTOM_WIDTH = 2;
    private static final int BEAM_TOP_WIDTH = 20;

    private static final Color BEAM_COLOR =
            new Color(255, 40, 40);

    private final Client client;

    private final List<PartyPingBeam> activePings =
            new CopyOnWriteArrayList<>();

    @Inject
    public RogueChestsFcPartyPingBeamOverlay(Client client)
    {
        this.client = client;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    void addPing(WorldPoint worldPoint)
    {
        if (worldPoint == null)
        {
            return;
        }

        activePings.removeIf(
                ping -> ping.worldPoint.equals(worldPoint)
        );

        activePings.add(
                new PartyPingBeam(
                        worldPoint,
                        System.currentTimeMillis()
                )
        );
    }

    void clearPings()
    {
        activePings.clear();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (activePings.isEmpty())
        {
            return null;
        }

        long now = System.currentTimeMillis();

        activePings.removeIf(
                ping -> now - ping.createdAtMillis
                        >= PING_LIFETIME_MILLIS
        );

        for (PartyPingBeam ping : activePings)
        {
            long elapsed =
                    now - ping.createdAtMillis;

            renderPing(
                    graphics,
                    ping,
                    elapsed
            );
        }

        return null;
    }

    private void renderPing(
            Graphics2D graphics,
            PartyPingBeam ping,
            long elapsedMillis)
    {
        WorldPoint worldPoint = ping.worldPoint;

        if (worldPoint.getPlane() != client.getTopLevelWorldView().getPlane())
        {
            return;
        }

        LocalPoint localPoint =
                LocalPoint.fromWorld(
                        client,
                        worldPoint
                );

        if (localPoint == null)
        {
            return;
        }

        Polygon tilePolygon =
                Perspective.getCanvasTilePoly(
                        client,
                        localPoint
                );

        if (tilePolygon == null)
        {
            return;
        }

        Rectangle bounds = tilePolygon.getBounds();

        int anchorX =
                bounds.x + bounds.width / 2;

        int anchorY =
                bounds.y + bounds.height / 2;

        float lifeRemaining =
                1.0f - Math.min(
                        1.0f,
                        elapsedMillis
                                / (float) PING_LIFETIME_MILLIS
                );

        int beamAlpha =
                Math.max(
                        0,
                        Math.min(
                                190,
                                Math.round(190 * lifeRemaining)
                        )
                );

        int tileAlpha =
                Math.max(
                        0,
                        Math.min(
                                150,
                                Math.round(150 * lifeRemaining)
                        )
                );

        Graphics2D beamGraphics =
                (Graphics2D) graphics.create();

        try
        {
            beamGraphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            Polygon beam =
                    createBeamPolygon(
                            anchorX,
                            anchorY
                    );

            beamGraphics.setColor(
                    withAlpha(beamAlpha)
            );
            beamGraphics.fillPolygon(beam);

            OverlayUtil.renderPolygon(
                    beamGraphics,
                    tilePolygon,
                    withAlpha(tileAlpha)
            );
        }
        finally
        {
            beamGraphics.dispose();
        }
    }

    private Polygon createBeamPolygon(
            int anchorX,
            int anchorY)
    {
        int bottomHalf = BEAM_BOTTOM_WIDTH / 2;
        int topHalf = BEAM_TOP_WIDTH / 2;

        int topY = anchorY - BEAM_HEIGHT;

        return new Polygon(
                new int[]
                        {
                                anchorX - bottomHalf,
                                anchorX + bottomHalf,
                                anchorX + topHalf,
                                anchorX - topHalf
                        },
                new int[]
                        {
                                anchorY,
                                anchorY,
                                topY,
                                topY
                        },
                4
        );
    }

    private Color withAlpha(int alpha)
    {
        return new Color(
                BEAM_COLOR.getRed(),
                BEAM_COLOR.getGreen(),
                BEAM_COLOR.getBlue(),
                alpha
        );
    }

    private static class PartyPingBeam
    {
        private final WorldPoint worldPoint;
        private final long createdAtMillis;

        PartyPingBeam(
                WorldPoint worldPoint,
                long createdAtMillis)
        {
            this.worldPoint = worldPoint;
            this.createdAtMillis = createdAtMillis;
        }

    }
}