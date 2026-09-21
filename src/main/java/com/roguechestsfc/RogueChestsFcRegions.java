package com.roguechestsfc;

import java.util.Set;

final class RogueChestsFcRegions
{
    private static final Set<Integer> TRACKING_REGION_IDS = Set.of(
            12605,
            12861,
            12860,
            13116,
            13117,
            13373,
            13372
    );

    private RogueChestsFcRegions()
    {
    }

    static boolean isTrackingRegion(int regionId)
    {
        return TRACKING_REGION_IDS.contains(regionId);
    }
}