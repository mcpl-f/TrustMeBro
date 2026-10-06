package me.fulcanelly.trust.me.expr.model;

import lombok.Value;

/**
 * Named admin {@code regions} row as XZ AABB (center ± radius → min/max).
 * Not the prod {@code bro...NamedRegion} model — this is coverage geometry only.
 */
@Value
public class NamedRegionBox {

    String name;
    int wid;
    int minX;
    int minZ;
    int maxX;
    int maxZ;
}
