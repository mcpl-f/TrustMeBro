package me.fulcanelly.trust.me.bro.service.util;

import java.util.Optional;

import lombok.Value;

/**
 * Encodes the callback action plus one argument: the suspicious interactor for
 * trust/report buttons, or the Telegram user allowed to press for /myprofile buttons.
 *
 * The owner is intentionally not part of callback data: Telegram user id from the
 * callback is resolved through tg-bridge registration, so a button click can only
 * act for the linked Minecraft account of the person who clicked it.
 * The Telegram user id in profile buttons is not an identity, it only says who may
 * press them (the author of the /myprofile command); everyone else is turned away.
 *
 * Examples:
 * - tmb:t:Steve -> trust Steve for the linked owner
 * - tmb:r:Steve -> report Steve for the linked owner
 * - tmb:t:Alex_123 -> trust Alex_123 for the linked owner
 * - tmb:pr:123456789 -> profile "reputation" section, only for Telegram user 123456789
 * - tmb:pr:abc -> invalid profile argument, rejected as a bad action
 * - tmb:x:Steve -> valid shape, rejected later as an unknown action
 * - other:t:Steve -> ignored, not a TrustMeBro callback
 */
public final class TrustCallbackPayloadService {

    public static final String ACTION_TRUST = "t";
    public static final String ACTION_REPORT = "r";

    public static final String ACTION_PROFILE_REPUTATION = "pr";
    public static final String ACTION_PROFILE_RATINGS = "pm";
    public static final String ACTION_PROFILE_BACK = "pb";

    private static final String PREFIX = "tmb:";
    private static final int EXPECTED_PARTS = 3;

    public boolean isTrustCallback(String data) {
        return data != null && data.startsWith(PREFIX);
    }

    /** Trust / report buttons of a warning. */
    public boolean isDecisionAction(String action) {
        return ACTION_TRUST.equals(action) || ACTION_REPORT.equals(action);
    }

    public boolean isProfileAction(String action) {
        return ACTION_PROFILE_REPUTATION.equals(action)
                || ACTION_PROFILE_RATINGS.equals(action)
                || ACTION_PROFILE_BACK.equals(action);
    }

    public String encode(String action, String interactorPlayer) {
        return PREFIX + action + ":" + interactorPlayer;
    }

    public String encodeProfile(String action, long allowedTelegramUserId) {
        return PREFIX + action + ":" + allowedTelegramUserId;
    }

    public Optional<Payload> decode(String data) {
        if (!isTrustCallback(data)) {
            return Optional.empty();
        }

        String[] parts = data.split(":", EXPECTED_PARTS);
        if (parts.length != EXPECTED_PARTS) {
            return Optional.empty();
        }

        String action = parts[1];
        if (!isProfileAction(action)) {
            return Optional.of(new Payload(action, Optional.of(parts[2]), Optional.empty()));
        }

        try {
            return Optional.of(new Payload(action, Optional.empty(), Optional.of(Long.parseLong(parts[2]))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Value
    public static class Payload {

        String action;

        /** Present for trust/report actions. */
        Optional<String> interactorPlayer;

        /** Present for profile actions: the only Telegram user who may press the button. */
        Optional<Long> allowedTelegramUserId;
    }
}
