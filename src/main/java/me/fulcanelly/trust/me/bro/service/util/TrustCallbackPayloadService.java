package me.fulcanelly.trust.me.bro.service.util;

import java.util.Optional;

import lombok.Value;

/**
 * Encodes the callback action plus one argument: the suspicious interactor for
 * trust/report buttons, or the Telegram user allowed to press, the list page and the
 * shown player for /rep buttons.
 *
 * The owner is intentionally not part of callback data: Telegram user id from the
 * callback is resolved through tg-bridge registration, so a button click can only
 * act for the linked Minecraft account of the person who clicked it.
 * The Telegram user id in profile buttons is not an identity, it only says who may
 * press them (the author of the /rep command); everyone else is turned away.
 * Profile buttons only read, so they can carry the shown player: /rep may show
 * someone else's profile, which is not derivable from the clicker.
 *
 * Examples:
 * - tmb:t:Steve -> trust Steve for the linked owner
 * - tmb:r:Steve -> report Steve for the linked owner
 * - tmb:t:Alex_123 -> trust Alex_123 for the linked owner
 * - tmb:pr:123456789:0:Steve -> Steve's profile "reputation" section, first page, only for Telegram user 123456789
 * - tmb:prt:123456789:2:Steve -> Steve's profile "trusters" section, third page (pages count from 0)
 * - tmb:n:- -> button that does nothing (page number, player rows for now)
 * - tmb:pr:abc:0:Steve -> invalid profile argument, rejected as a bad action
 * - tmb:pr:123456789:0 -> profile argument without a player, rejected as a bad action
 * - tmb:x:Steve -> valid shape, rejected later as an unknown action
 * - other:t:Steve -> ignored, not a TrustMeBro callback
 */
public final class TrustCallbackPayloadService {

    public static final String ACTION_TRUST = "t";
    public static final String ACTION_REPORT = "r";

    /** Buttons that only show something: answered with nothing. */
    public static final String ACTION_NOOP = "n";

    /**
     * Every /rep section code (see ProfileMessageBuilder.Section) starts with this,
     * so the section list can grow without being repeated here.
     */
    private static final String PROFILE_ACTION_PREFIX = "p";

    private static final String PREFIX = "tmb:";
    private static final int EXPECTED_PARTS = 3;

    /** Third part of a profile callback: user id, page, player. */
    private static final int PROFILE_ARGS = 3;

    public boolean isTrustCallback(String data) {
        return data != null && data.startsWith(PREFIX);
    }

    /** Trust / report buttons of a warning. */
    public boolean isDecisionAction(String action) {
        return ACTION_TRUST.equals(action) || ACTION_REPORT.equals(action);
    }

    public boolean isProfileAction(String action) {
        return action.startsWith(PROFILE_ACTION_PREFIX);
    }

    public String encode(String action, String interactorPlayer) {
        return PREFIX + action + ":" + interactorPlayer;
    }

    public String encodeProfile(String sectionCode, long allowedTelegramUserId, int page, String profilePlayer) {
        return PREFIX + sectionCode + ":" + allowedTelegramUserId + ":" + page + ":" + profilePlayer;
    }

    public String encodeNoop() {
        // decode wants three parts; the last one is ignored
        return PREFIX + ACTION_NOOP + ":-";
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
            return Optional.of(new Payload(
                    action, Optional.of(parts[2]), Optional.empty(), Optional.empty(), Optional.empty()));
        }

        String[] profileArgs = parts[2].split(":", PROFILE_ARGS);
        if (profileArgs.length != PROFILE_ARGS || profileArgs[2].isEmpty()) {
            return Optional.empty();
        }

        try {
            long allowedTelegramUserId = Long.parseLong(profileArgs[0]);
            int page = Integer.parseInt(profileArgs[1]);
            if (page < 0) {
                return Optional.empty();
            }
            return Optional.of(new Payload(
                    action, Optional.empty(), Optional.of(allowedTelegramUserId), Optional.of(page),
                    Optional.of(profileArgs[2])));
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

        /** Present for profile actions: zero-based page of the section's player list. */
        Optional<Integer> page;

        /** Present for profile actions: whose profile is shown. */
        Optional<String> profilePlayer;
    }
}
