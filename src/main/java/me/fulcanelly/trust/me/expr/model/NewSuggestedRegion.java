package me.fulcanelly.trust.me.expr.model;

import java.util.LinkedHashSet;
import java.util.Set;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * New suggested region still only in memory (not in DB yet).
 * Same grow-min/max idea as {@link SuggestedRegion#absorb}.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class NewSuggestedRegion {

    private final int wid;
    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;
    private final Set<Long> activityBoxIds;

    public static NewSuggestedRegion from(ActivityBox activityBox) {
        NewSuggestedRegion region = new NewSuggestedRegion(
                activityBox.getWid(),
                activityBox.getMinX(),
                activityBox.getMinZ(),
                activityBox.getMaxX(),
                activityBox.getMaxZ(),
                new LinkedHashSet<>());
        region.activityBoxIds.add(activityBox.getId());
        return region;
    }

    public void merge(ActivityBox activityBox) {
        minX = Math.min(minX, activityBox.getMinX());
        minZ = Math.min(minZ, activityBox.getMinZ());
        maxX = Math.max(maxX, activityBox.getMaxX());
        maxZ = Math.max(maxZ, activityBox.getMaxZ());
        activityBoxIds.add(activityBox.getId());
    }
}
