package me.fulcanelly.trust.me.bro.service;

/**
 * Stable string codes persisted on {@code interaction_counts.skip_reason}
 * when a Telegram warning is intentionally not sent.
 */
public final class NotificationSkipReason {

    public static final String ALREADY_HANDLED = "already_handled";
    public static final String NO_LINKED_OWNER = "no_linked_owner";

}
