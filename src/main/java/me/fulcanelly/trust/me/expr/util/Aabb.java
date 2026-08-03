package me.fulcanelly.trust.me.expr.util;

import lombok.experimental.UtilityClass;

/**
 * Axis-aligned merge-distance: same wid + padded XZ overlap (no sqrt).
 */
@UtilityClass
public class Aabb {

    public static boolean withinMergeDistance(
            int widA, int minXa, int minZa, int maxXa, int maxZa,
            int widB, int minXb, int minZb, int maxXb, int maxZb,
            int mergeDistance //
    ) {
        if (widA != widB) {
            return false;
        }
        int pad = Math.max(0, mergeDistance);
        boolean overlapOnX = minXa - pad <= maxXb && minXb - pad <= maxXa;
        boolean overlapOnZ = minZa - pad <= maxZb && minZb - pad <= maxZa;
        return overlapOnX && overlapOnZ;
    }
}
