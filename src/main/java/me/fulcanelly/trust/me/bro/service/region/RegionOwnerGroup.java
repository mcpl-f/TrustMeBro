package me.fulcanelly.trust.me.bro.service.region;

import lombok.Value;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;

import java.util.List;

/**
 * One block of the Telegram warning:
 * either a shared region header + several owners, or a single owner with no region.
 */
@Value
public class RegionOwnerGroup {

    /**
     * United area for this block, or null when the interaction had no region data.
     * Null groups are never merged with anything else.
     */
    MessageRegion region;

    /** Owners (interaction rows) shown under that region header. */
    List<InteractionCount> interactions;

    public boolean hasRegion() {
        return region != null;
    }
}
