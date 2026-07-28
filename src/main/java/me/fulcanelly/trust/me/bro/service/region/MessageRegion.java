package me.fulcanelly.trust.me.bro.service.region;

import lombok.Value;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;

/**
 * Bounding box of one interaction area on the XZ plane (Minecraft horizontal).
 *
 * Corners come from interaction_counts region_corner_a / region_corner_b.
 * Used only when building the Telegram text — never written back to the DB.
 */
@Value
public class MessageRegion {

    /** CoreProtect world id — regions from different worlds must not merge. */
    int worldId;

    int minX;
    int minZ;
    int maxX;
    int maxZ;

    /** Normalize a/b corners into min/max so later math does not need MIN/MAX. */
    public static MessageRegion fromInteraction(InteractionCount interaction) {
        int minX = Math.min(interaction.getRegionCornerAX(), interaction.getRegionCornerBX());
        int maxX = Math.max(interaction.getRegionCornerAX(), interaction.getRegionCornerBX());
        int minZ = Math.min(interaction.getRegionCornerAZ(), interaction.getRegionCornerBZ());
        int maxZ = Math.max(interaction.getRegionCornerAZ(), interaction.getRegionCornerBZ());
        return new MessageRegion(interaction.getWid(), minX, minZ, maxX, maxZ);
    }

    /** Expand this box so it covers {@code other} as well (same world). */
    public MessageRegion expandToCover(MessageRegion other) {
        return new MessageRegion(
                worldId,
                Math.min(minX, other.minX),
                Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX),
                Math.max(maxZ, other.maxZ));
    }

    public int centerX() {
        return (minX + maxX) / 2;
    }

    public int centerZ() {
        return (minZ + maxZ) / 2;
    }

    /** Half of the longer XZ side — same display idea as InteractionCount.regionRadius(). */
    public int displayRadius() {
        int sizeX = Math.abs(maxX - minX);
        int sizeZ = Math.abs(maxZ - minZ);
        return Math.max(sizeX, sizeZ) / 2;
    }

    /**
     * True if the two boxes are close enough to show as one Telegram block.
     *
     * Same rule as DB merge: pad each box by {@code mergeDistance} blocks on X and Z
     * and check overlap. No sqrt — axis-aligned only. Different worlds never match.
     */
    public boolean isWithinMergeDistanceOf(MessageRegion other, int mergeDistance) {
        if (worldId != other.worldId) {
            return false;
        }

        int pad = Math.max(0, mergeDistance);

        boolean overlapOnX = minX - pad <= other.maxX && other.minX - pad <= maxX;
        boolean overlapOnZ = minZ - pad <= other.maxZ && other.minZ - pad <= maxZ;
        return overlapOnX && overlapOnZ;
    }
}
