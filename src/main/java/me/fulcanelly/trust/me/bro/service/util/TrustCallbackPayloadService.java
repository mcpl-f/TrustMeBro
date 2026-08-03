package me.fulcanelly.trust.me.bro.service.util;

import java.util.Optional;

import lombok.Value;

/**
 * Encodes only the callback action and the suspicious interactor.
 *
 * The owner is intentionally not part of callback data: Telegram user id from the
 * callback is resolved through tg-bridge registration, so a button click can only
 * act for the linked Minecraft account of the person who clicked it.
 *
 * Examples:
 * - tmb:t:Steve -> trust Steve for the linked owner
 * - tmb:r:Steve -> report Steve for the linked owner
 * - tmb:t:Alex_123 -> trust Alex_123 for the linked owner
 * - tmb:x:Steve -> valid shape, rejected later as an unknown action
 * - other:t:Steve -> ignored, not a TrustMeBro callback
 */
public final class TrustCallbackPayloadService {

    public static final String ACTION_TRUST = "t";
    public static final String ACTION_REPORT = "r";

    private static final String PREFIX = "tmb:";
    private static final int EXPECTED_PARTS = 3;

    public boolean isTrustCallback(String data) {
        return data != null && data.startsWith(PREFIX);
    }

    public String encode(String action, String interactorPlayer) {
        return PREFIX + action + ":" + interactorPlayer;
    }

    public Optional<Payload> decode(String data) {
        if (!isTrustCallback(data)) {
            return Optional.empty();
        }

        String[] parts = data.split(":", EXPECTED_PARTS);
        if (parts.length != EXPECTED_PARTS) {
            return Optional.empty();
        }
        return Optional.of(new Payload(parts[1], parts[2]));
    }

    @Value
    public static class Payload {

        String action;
        String interactorPlayer;
    }
}
