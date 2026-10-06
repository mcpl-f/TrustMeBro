package me.fulcanelly.trust.me.expr.model;

import java.util.LinkedHashSet;
import java.util.Set;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.fulcanelly.trust.me.expr.util.Aabb;

/**
 * Persisted {@code suggested_regions} row. Absorbs nearby {@link ActivityBox}es
 * (expand min/max + attach sources). Twin of {@link NewSuggestedRegion}.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class SuggestedRegion {

    private final long id;
    private final int wid;
    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;
    private final String status;
    private final Set<Long> knownActivityBoxIds;
    private final Set<Long> newlyAttachedActivityBoxIds;
    private boolean boundsChanged;

    public static SuggestedRegion load(
            long id,
            int wid,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            String status,
            Set<Long> knownActivityBoxIds //
    ) {
        return new SuggestedRegion(
                id,
                wid,
                minX,
                minZ,
                maxX,
                maxZ,
                status,
                new LinkedHashSet<>(knownActivityBoxIds),
                new LinkedHashSet<>(),
                false);
    }

    /** Grow min/max if nearby; attach activity-box id when new. */
    public boolean absorb(ActivityBox activityBox, int mergeDistance) {
        if (!Aabb.withinMergeDistance(
                activityBox.getWid(), activityBox.getMinX(), activityBox.getMinZ(),
                activityBox.getMaxX(), activityBox.getMaxZ(),
                wid, minX, minZ, maxX, maxZ,
                mergeDistance)) {
            return false;
        }

        int nextMinX = Math.min(minX, activityBox.getMinX());
        int nextMinZ = Math.min(minZ, activityBox.getMinZ());
        int nextMaxX = Math.max(maxX, activityBox.getMaxX());
        int nextMaxZ = Math.max(maxZ, activityBox.getMaxZ());
        if (nextMinX != minX || nextMinZ != minZ || nextMaxX != maxX || nextMaxZ != maxZ) {
            minX = nextMinX;
            minZ = nextMinZ;
            maxX = nextMaxX;
            maxZ = nextMaxZ;
            boundsChanged = true;
        }

        if (!knownActivityBoxIds.contains(activityBox.getId())) {
            knownActivityBoxIds.add(activityBox.getId());
            newlyAttachedActivityBoxIds.add(activityBox.getId());
        }
        return true;
    }

    public boolean isDirty() {
        return boundsChanged || !newlyAttachedActivityBoxIds.isEmpty();
    }
}
