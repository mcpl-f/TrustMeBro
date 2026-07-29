package me.fulcanelly.trust.me.expr.util;

import lombok.experimental.UtilityClass;
import me.fulcanelly.trust.me.expr.model.RegionBox;

@UtilityClass
public class Aabb {

    public static boolean withinMergeDistance(RegionBox a, RegionBox b, int mergeDistance) {
        return withinMergeDistance(
                a.getWid(), a.getMinX(), a.getMinZ(), a.getMaxX(), a.getMaxZ(),
                b.getWid(), b.getMinX(), b.getMinZ(), b.getMaxX(), b.getMaxZ(),
                mergeDistance);
    }

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
