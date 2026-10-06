package me.fulcanelly.trust.me.expr.model;

import lombok.Value;

/**
 * One {@code interaction_counts} row with XZ geometry (SQL corners → min/max).
 * Canonical Java name for that concept in {@code expr} — not “page with geometry”, not bare “box”.
 */
@Value
public class ActivityBox {

    long id;
    int wid;
    int minX;
    int minZ;
    int maxX;
    int maxZ;
}
