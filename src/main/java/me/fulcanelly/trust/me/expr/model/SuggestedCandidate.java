package me.fulcanelly.trust.me.expr.model;

import java.util.LinkedHashSet;
import java.util.Set;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** In-memory cluster of nearby uncovered activity boxes. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class SuggestedCandidate {

    private final int wid;
    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;

    private long weight;

    private final Set<String> interactors;
    private final Set<String> owners;
    private final Set<Long> interactionIds;

    public static SuggestedCandidate from(ActivityBox box) {
        SuggestedCandidate c = new SuggestedCandidate(
                box.getWid(),
                box.getMinX(),
                box.getMinZ(),
                box.getMaxX(),
                box.getMaxZ(),
                box.getWeight(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>());
        c.interactors.add(box.getInteractor());
        c.owners.add(box.getOwner());
        c.interactionIds.add(box.getId());
        return c;
    }

    public void merge(ActivityBox box) {
        minX = Math.min(minX, box.getMinX());
        minZ = Math.min(minZ, box.getMinZ());
        maxX = Math.max(maxX, box.getMaxX());
        maxZ = Math.max(maxZ, box.getMaxZ());
        weight += box.getWeight();
        interactors.add(box.getInteractor());
        owners.add(box.getOwner());
        interactionIds.add(box.getId());
    }

    public int centerX() {
        return (minX + maxX) / 2;
    }

    public int centerZ() {
        return (minZ + maxZ) / 2;
    }

    public int displayRadius() {
        int sizeX = Math.abs(maxX - minX);
        int sizeZ = Math.abs(maxZ - minZ);
        return Math.max(sizeX, sizeZ) / 2;
    }

    public RegionBox toBox() {
        return new RegionBox("candidate", wid, minX, minZ, maxX, maxZ);
    }
}
