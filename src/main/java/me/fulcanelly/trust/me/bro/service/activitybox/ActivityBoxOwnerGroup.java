package me.fulcanelly.trust.me.bro.service.activitybox;

import lombok.Value;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;

import java.util.List;

/**
 * One block of the Telegram warning:
 * either a shared activity box header + several owners, or a single owner with no box.
 */
@Value
public class ActivityBoxOwnerGroup {

    /**
     * United area for this block, or null when the interaction had no activity box.
     * Null groups are never merged with anything else.
     */
    MessageBox activityBox;

    /** Owners (interaction rows) shown under that box header. */
    List<InteractionCount> interactions;

    public boolean hasActivityBox() {
        return activityBox != null;
    }
}
