package me.fulcanelly.trust.me.bro.service.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import me.fulcanelly.trust.me.bro.service.text.ProfileMessageBuilder.Section;
import me.fulcanelly.trust.me.bro.service.util.TrustCallbackPayloadService;

/**
 * Wiring of the /rep buttons: a pressed button is found by its callback code.
 */
public class ProfileSectionTest {

    private final TrustCallbackPayloadService payloads = new TrustCallbackPayloadService();

    /*
     * button -> callback data -> handler:
     *
     *   [Trust you]  ->  tmb:prt:123:0:Steve  ->  decoded as a profile action  ->  Section.TRUSTERS
     *   [Back]       ->  code of the parent, so from TRUSTERS it opens REPUTATION
     *
     * A section with a duplicate code, or a code the payload service does not take for a
     * profile action, would give a button that silently opens the wrong screen or answers
     * "unknown action".
     */
    @Test
    void everySectionIsFoundByItsOwnCallbackCode() {
        Set<String> codes = new HashSet<>();

        for (Section section : Section.values()) {
            assertTrue(codes.add(section.getCode()), "duplicate code " + section.getCode());
            assertTrue(payloads.isProfileAction(section.getCode()), section.getCode());

            var decoded = payloads.decode(payloads.encodeProfile(section.getCode(), 123L, 0, "Steve")).orElseThrow();
            assertEquals(section, Section.fromCode(decoded.getAction()).orElseThrow());
        }
    }

    /*
     * Back never loops: every section reaches MAIN by going to its parent.
     *
     *   TRUSTERS -> REPUTATION -> MAIN
     */
    @Test
    void backFromAnySectionEndsAtMain() {
        for (Section section : Section.values()) {
            Section step = section;
            int hops = 0;
            while (step != Section.MAIN) {
                step = step.getParent();
                assertTrue(++hops <= Section.values().length, "loop at " + section);
            }
        }
    }
}
