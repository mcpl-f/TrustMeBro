package me.fulcanelly.trust.me.bro.database.repository.model;

import lombok.Value;
import me.fulcanelly.trust.me.bro.service.activitybox.MessageBox;

/**
 * Admin-created named area from the {@code regions} table (center + radius).
 */
@Value
public class NamedRegion {

    long id;

    String name;

    boolean mutable;

    String createdByMcName;
    long createdAt;

    int wid;
    int centerX;
    int centerZ;
    int radius;

    /** Approximate the circle as an XZ AABB for merge-distance checks. */
    public MessageBox toMessageBox() {
        int r = Math.max(0, radius);
        return new MessageBox(
                wid,
                centerX - r,
                centerZ - r,
                centerX + r,
                centerZ + r);
    }
}
