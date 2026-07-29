package me.fulcanelly.trust.me.expr.model;

import lombok.Value;

/**
 * Axis-aligned XZ box in one CoreProtect world ({@code wid}).
 */
@Value
public class RegionBox {

    String label;
    int wid;
    
    int minX;
    int minZ;

    int maxX;
    int maxZ;
}
