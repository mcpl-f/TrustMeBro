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
    Integer activityBoxCornerAX;
    Integer activityBoxCornerAZ;
    Integer activityBoxCornerBX;
    Integer activityBoxCornerBZ;

    public boolean hasActivityBox() {
        return wid != null
                && activityBoxCornerAX != null
                && activityBoxCornerAZ != null
                && activityBoxCornerBX != null
                && activityBoxCornerBZ != null;
    }

    public int activityBoxCenterX() {
        return (activityBoxCornerAX + activityBoxCornerBX) / 2;
    }

    public int activityBoxCenterZ() {
        return (activityBoxCornerAZ + activityBoxCornerBZ) / 2;
    }

    /** Half of the larger XZ side of the AABB (no sqrt). */
    public int activityBoxRadius() {
        int sizeX = Math.abs(activityBoxCornerBX - activityBoxCornerAX);
        int sizeZ = Math.abs(activityBoxCornerBZ - activityBoxCornerAZ);
        return Math.max(sizeX, sizeZ) / 2;
    }
}
