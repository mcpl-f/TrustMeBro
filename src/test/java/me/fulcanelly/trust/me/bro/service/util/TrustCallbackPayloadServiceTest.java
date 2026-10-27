package me.fulcanelly.trust.me.bro.service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import me.fulcanelly.trust.me.bro.listener.telegram.callback.TrustCallbackPayloadService;
import me.fulcanelly.trust.me.bro.listener.telegram.callback.TrustCallbackPayloadService.Payload;

public class TrustCallbackPayloadServiceTest {

    private final TrustCallbackPayloadService payloads = new TrustCallbackPayloadService();

    /*
     * Trust/report buttons carry the interactor, profile buttons carry the only
     * Telegram user allowed to press them, the list page and whose profile is shown.
     *
     *   tmb:t:Alex_123            -> action t,  interactor Alex_123
     *   tmb:pr:123456789:2:Steve  -> action pr, allowed user 123456789, page 2, profile of Steve
     *                                (no interactor)
     */
    @Test
    void decodesInteractorForDecisionsAndAllowedUserForProfile() {
        Payload trust = payloads.decode(payloads.encode(TrustCallbackPayloadService.ACTION_TRUST, "Alex_123"))
                .orElseThrow();
        assertEquals(Optional.of("Alex_123"), trust.getInteractorPlayer());
        assertEquals(Optional.empty(), trust.getAllowedTelegramUserId());
        assertEquals(Optional.empty(), trust.getPage());
        assertEquals(Optional.empty(), trust.getProfilePlayer());

        Payload profile = payloads.decode(payloads.encodeProfile("pr", 123456789L, 2, "Steve")).orElseThrow();
        assertEquals("pr", profile.getAction());
        assertEquals(Optional.of(123456789L), profile.getAllowedTelegramUserId());
        assertEquals(Optional.of(2), profile.getPage());
        assertEquals(Optional.of("Steve"), profile.getProfilePlayer());
        assertEquals(Optional.empty(), profile.getInteractorPlayer());
    }

    /*
     * A profile button that opens one player of a list also carries that player.
     *
     *   tmb:prt:123:2:Steve:Alex  -> page 2 of Steve's trusters, Alex opened
     *   tmb:prt:123:2:Steve       -> the list itself, nobody opened
     *   tmb:prt:123:2:Steve:      -> opened player is empty -> rejected
     */
    @Test
    void profileButtonMayCarryTheOpenedPlayer() {
        Payload opened = payloads.decode(payloads.encodeProfile("prt", 123L, 2, "Steve", "Alex")).orElseThrow();
        assertEquals("tmb:prt:123:2:Steve:Alex", payloads.encodeProfile("prt", 123L, 2, "Steve", "Alex"));
        assertEquals(Optional.of("Steve"), opened.getProfilePlayer());
        assertEquals(Optional.of("Alex"), opened.getListedPlayer());
        assertEquals(Optional.of(2), opened.getPage());

        Payload list = payloads.decode(payloads.encodeProfile("prt", 123L, 2, "Steve")).orElseThrow();
        assertEquals(Optional.empty(), list.getListedPlayer());

        assertTrue(payloads.decode("tmb:prt:123:2:Steve:").isEmpty());
    }

    /*
     * Broken or foreign data must never reach the handlers as a valid payload.
     *
     *   tmb:pr:abc:0:Steve  -> profile action, id is not a number -> rejected
     *   tmb:pr:123:0        -> no player                          -> rejected
     *   tmb:pr:123:0:       -> empty player                       -> rejected
     *   tmb:pr:123:-1:Steve -> page before the first one          -> rejected
     *   tmb:pr              -> no argument at all                 -> rejected
     *   other:t:Steve -> not ours                          -> not a TrustMeBro callback
     *   tmb:x:Steve  -> valid shape, unknown action        -> decodes (handler rejects it)
     */
    @Test
    void rejectsBrokenProfileDataAndKeepsUnknownActionsForTheHandler() {
        assertTrue(payloads.decode("tmb:pr:abc:0:Steve").isEmpty());
        assertTrue(payloads.decode("tmb:pr:123:0").isEmpty());
        assertTrue(payloads.decode("tmb:pr:123:0:").isEmpty());
        assertTrue(payloads.decode("tmb:pr:123:-1:Steve").isEmpty());
        assertTrue(payloads.decode("tmb:pr").isEmpty());

        assertFalse(payloads.isTrustCallback("other:t:Steve"));
        assertTrue(payloads.decode("other:t:Steve").isEmpty());

        assertEquals("x", payloads.decode("tmb:x:Steve").orElseThrow().getAction());
        assertEquals(TrustCallbackPayloadService.ACTION_NOOP, payloads.decode(payloads.encodeNoop()).orElseThrow().getAction());
    }
}
