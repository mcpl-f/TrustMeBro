package me.fulcanelly.trust.me.bro.service.activitybox;

import lombok.Value;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;

/**
 * Bounding box on the XZ plane (Minecraft horizontal). Used for both an
 * interaction's activity box and the square around a {@code NamedRegion}, so
 * that the two can be compared with {@link #isWithinMergeDistanceOf}.
 *
 * Activity box corners come from interaction_counts region_corner_a / region_corner_b.
 * Used only when building message text — never written back to the DB.
 */
@Value
public class MessageBox {

    /** CoreProtect world id — boxes from different worlds must not merge. */
    int worldId;

    int minX;
    int minZ;
    int maxX;
    int maxZ;

    /** Normalize a/b corners into min/max so later math does not need MIN/MAX. */
    public static MessageBox fromInteraction(InteractionCount interaction) {
        int minX = Math.min(interaction.getActivityBoxCornerAX(), interaction.getActivityBoxCornerBX());
        int maxX = Math.max(interaction.getActivityBoxCornerAX(), interaction.getActivityBoxCornerBX());
        int minZ = Math.min(interaction.getActivityBoxCornerAZ(), interaction.getActivityBoxCornerBZ());
        int maxZ = Math.max(interaction.getActivityBoxCornerAZ(), interaction.getActivityBoxCornerBZ());
        return new MessageBox(interaction.getWid(), minX, minZ, maxX, maxZ);
    }

    /** Expand this box so it covers {@code other} as well (same world). */
    public MessageBox expandToCover(MessageBox other) {
        return new MessageBox(
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

    /** Half of the longer XZ side — same display idea as InteractionCount.activityBoxRadius(). */
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
    public boolean isWithinMergeDistanceOf(MessageBox other, int mergeDistance) {
        if (worldId != other.worldId) {
            return false;
        }

        int pad = Math.max(0, mergeDistance);

        boolean overlapOnX = minX - pad <= other.maxX && other.minX - pad <= maxX;
        boolean overlapOnZ = minZ - pad <= other.maxZ && other.minZ - pad <= maxZ;
        return overlapOnX && overlapOnZ;
    }
}
