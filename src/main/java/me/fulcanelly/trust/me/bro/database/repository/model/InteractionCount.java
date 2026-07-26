package me.fulcanelly.trust.me.bro.database.repository.model;

import lombok.Value;

@Value
public class InteractionCount {

    long id;
    String interactorPlayer;
    String ownerPlayer;

    int countBreakBlocks;
    int countPlacedBlocks;
    int countInteractContainers;

    Integer wid;
    Integer regionCornerAX;
    Integer regionCornerAZ;
    Integer regionCornerBX;
    Integer regionCornerBZ;

    public boolean hasRegion() {
        return wid != null
                && regionCornerAX != null
                && regionCornerAZ != null
                && regionCornerBX != null
                && regionCornerBZ != null;
    }

    public int regionCenterX() {
        return (regionCornerAX + regionCornerBX) / 2;
    }

    public int regionCenterZ() {
        return (regionCornerAZ + regionCornerBZ) / 2;
    }

    /** Half of the larger XZ side of the AABB (no sqrt). */
    public int regionRadius() {
        int sizeX = Math.abs(regionCornerBX - regionCornerAX);
        int sizeZ = Math.abs(regionCornerBZ - regionCornerAZ);
        return Math.max(sizeX, sizeZ) / 2;
    }
}
