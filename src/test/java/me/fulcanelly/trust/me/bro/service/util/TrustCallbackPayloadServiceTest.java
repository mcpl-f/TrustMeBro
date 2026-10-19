package me.fulcanelly.trust.me.bro.service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import me.fulcanelly.trust.me.bro.service.util.TrustCallbackPayloadService.Payload;

public class TrustCallbackPayloadServiceTest {

    private final TrustCallbackPayloadService payloads = new TrustCallbackPayloadService();

    /*
     * Trust/report buttons carry the interactor, profile buttons carry the only
     * Telegram user allowed to press them.
     *
     *   tmb:t:Alex_123    -> action t,  interactor Alex_123
     *   tmb:pr:123456789  -> action pr, allowed user 123456789 (no interactor)
     */
    @Test
    void decodesInteractorForDecisionsAndAllowedUserForProfile() {
        Payload trust = payloads.decode(payloads.encode(TrustCallbackPayloadService.ACTION_TRUST, "Alex_123"))
                .orElseThrow();
        assertEquals(Optional.of("Alex_123"), trust.getInteractorPlayer());
        assertEquals(Optional.empty(), trust.getAllowedTelegramUserId());

        Payload profile = payloads.decode(
                payloads.encodeProfile(TrustCallbackPayloadService.ACTION_PROFILE_REPUTATION, 123456789L))
                .orElseThrow();
        assertEquals(TrustCallbackPayloadService.ACTION_PROFILE_REPUTATION, profile.getAction());
        assertEquals(Optional.of(123456789L), profile.getAllowedTelegramUserId());
        assertEquals(Optional.empty(), profile.getInteractorPlayer());
    }

    /*
     * Broken or foreign data must never reach the handlers as a valid payload.
     *
     *   tmb:pr:abc   -> profile action, id is not a number -> rejected
     *   tmb:pr       -> no argument at all                 -> rejected
     *   other:t:Steve -> not ours                          -> not a TrustMeBro callback
     *   tmb:x:Steve  -> valid shape, unknown action        -> decodes (handler rejects it)
     */
    @Test
    void rejectsBrokenProfileDataAndKeepsUnknownActionsForTheHandler() {
        assertTrue(payloads.decode("tmb:pr:abc").isEmpty());
        assertTrue(payloads.decode("tmb:pr").isEmpty());

        assertFalse(payloads.isTrustCallback("other:t:Steve"));
        assertTrue(payloads.decode("other:t:Steve").isEmpty());

        assertEquals("x", payloads.decode("tmb:x:Steve").orElseThrow().getAction());
    }
}
