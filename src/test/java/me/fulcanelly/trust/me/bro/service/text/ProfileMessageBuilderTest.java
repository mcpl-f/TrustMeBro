package me.fulcanelly.trust.me.bro.service.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.fulcanelly.trust.me.bro.bootstrap.bundles.LocalRepositories;
import me.fulcanelly.trust.me.bro.database.MigrationRunner;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;
import me.fulcanelly.trust.me.bro.service.text.ProfileMessageBuilder.Section;
import me.fulcanelly.trust.me.bro.service.util.LocalizationService;
import me.fulcanelly.trust.me.bro.service.util.TrustCallbackPayloadService;

/**
 * The /rep keyboard. Only the messages that carry a placeholder are defined (see
 * MESSAGES); every other button text is its own message key.
 */
public class ProfileMessageBuilderTest {

    private static final long TELEGRAM_USER = 123L;

    private static final String MESSAGES = """
            telegram:
              command:
                myprofile:
                  text: "{player}: {trusters} trust, {reporters} reports"
                  title:
                    reputation: "R"
                    trusters: "T"
                    reporters: "Rp"
                  button-with-count: "{title} ({count})"
                  player-button: "@{player}"
                  player-view: "{player} | {path} | {towardsPlayer} to {player}, {towardsOther} to {other}"
            """;

    private final TrustCallbackPayloadService payloads = new TrustCallbackPayloadService();

    private Connection connection;
    private LocalRepositories repositories;
    private ProfileMessageBuilder builder;

    @BeforeEach
    void openFreshDatabase() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        new MigrationRunner(connection).migrate();
        repositories = LocalRepositories.buildFromConnection(connection);

        Plugin plugin = (Plugin) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { Plugin.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getConfig")) {
                        return new YamlConfiguration();
                    }
                    if (method.getName().equals("getResource")) {
                        return new ByteArrayInputStream(MESSAGES.getBytes(StandardCharsets.UTF_8));
                    }
                    return null;
                });
        builder = new ProfileMessageBuilder(new LocalizationService(plugin), payloads, repositories);
    }

    @AfterEach
    void closeDatabase() throws Exception {
        connection.close();
    }

    /*
     * Menus: buttons go down one level, Back goes up one level. Buttons of list
     * sections tell how many players are behind them.
     *
     *   Alice: trusted by Pa, Pb; reported by Pc
     *
     *   MAIN        [reputation] [ratings]
     *   REPUTATION  [T (2)] [Rp (1)] [back -> MAIN]
     */
    @Test
    void menuButtonsOpenChildrenAndBackOpensParent() throws Exception {
        repositories.getTrust().trust("Pa", "Alice", null);
        repositories.getTrust().trust("Pb", "Alice", null);
        repositories.getReports().report("Pc", null, "Alice", "Pc");

        assertEquals(List.of(
                "R>tmb:pr:123:0:Alice",
                "telegram.command.myprofile.title.ratings>tmb:pm:123:0:Alice"),
                keyboard("Alice", Section.MAIN, 0));

        assertEquals(List.of(
                "T (2)>tmb:prt:123:0:Alice",
                "Rp (1)>tmb:prr:123:0:Alice",
                "telegram.button.back>tmb:p:123:0:Alice"),
                keyboard("Alice", Section.REPUTATION, 0));
    }

    /*
     * Text: the profile of the shown player, and under it the titles from the root's
     * child down to the section.
     *
     *   MAIN      ->  profile
     *   TRUSTERS  ->  profile, blank line, "R / T:"
     */
    @Test
    void sectionScreenAddsThePathOfTitlesUnderTheProfile() throws Exception {
        repositories.getTrust().trust("Pa", "Alice", null);
        repositories.getTrust().trust("Pb", "Alice", null);
        repositories.getReports().report("Pc", null, "Alice", "Pc");

        assertEquals("Alice: 2 trust, 1 reports", builder.buildText("Alice", Section.MAIN));
        assertEquals("Alice: 2 trust, 1 reports\n\nR / T:", builder.buildText("Alice", Section.TRUSTERS));
    }

    /*
     * Messages are sent in Telegram's Markdown mode, where a lone "_" fails the whole
     * message. Names with underscores are escaped in the text, but not in callback data.
     *
     *   v_mute  ->  text "v\_mute: ...",  button data "...:v_mute"
     */
    @Test
    void underscoreInPlayerNameIsEscapedInTextOnly() throws Exception {
        assertEquals("v\\_mute: 0 trust, 0 reports", builder.buildText("v_mute", Section.MAIN));
        assertEquals(List.of(
                "R>tmb:pr:123:0:v_mute",
                "telegram.command.myprofile.title.ratings>tmb:pm:123:0:v_mute"),
                keyboard("v_mute", Section.MAIN, 0));
    }

    /*
     * 11 players trust Alice, 5 per page, newest trust first. Player rows open that player
     * from the page they are on, the page number does nothing, arrows move one page, Back
     * goes to the section's parent.
     *
     *   page 0:  P11..P07    [1 / 3] [>]
     *   page 1:  P06..P02    [<] [2 / 3] [>]
     *   page 2:  P01         [<] [3 / 3]
     */
    @Test
    void playersArePagedFiveAtATimeWithArrowsOnlyWhereThereIsAPage() throws Exception {
        for (int i = 1; i <= 11; i++) {
            repositories.getTrust().trust(String.format("P%02d", i), "Alice", null);
        }
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE trust_edges
                    SET created_at = CAST(substr(owner_mc_name, 2) AS INTEGER)
                    WHERE trusted_mc_name = 'Alice'
                    """);
        }

        assertEquals(List.of(
                "@P11>tmb:prt:123:0:Alice:P11", "@P10>tmb:prt:123:0:Alice:P10", "@P09>tmb:prt:123:0:Alice:P09",
                "@P08>tmb:prt:123:0:Alice:P08", "@P07>tmb:prt:123:0:Alice:P07",
                "1 / 3 | telegram.button.page-next>tmb:prt:123:1:Alice",
                "telegram.button.back>tmb:pr:123:0:Alice"),
                keyboard("Alice", Section.TRUSTERS, 0));

        assertEquals(List.of(
                "@P06>tmb:prt:123:1:Alice:P06", "@P05>tmb:prt:123:1:Alice:P05", "@P04>tmb:prt:123:1:Alice:P04",
                "@P03>tmb:prt:123:1:Alice:P03", "@P02>tmb:prt:123:1:Alice:P02",
                "telegram.button.page-previous>tmb:prt:123:0:Alice | 2 / 3 | telegram.button.page-next>tmb:prt:123:2:Alice",
                "telegram.button.back>tmb:pr:123:0:Alice"),
                keyboard("Alice", Section.TRUSTERS, 1));

        assertEquals(List.of(
                "@P01>tmb:prt:123:2:Alice:P01",
                "telegram.button.page-previous>tmb:prt:123:1:Alice | 3 / 3",
                "telegram.button.back>tmb:pr:123:0:Alice"),
                keyboard("Alice", Section.TRUSTERS, 2));
    }

    /*
     * No page row when everything fits: exactly one full page, or nobody at all.
     * A button of an older message may ask for a page that no longer exists: it shows
     * the last one.
     *
     *   Alice: 5 trusters  ->  P05..P01, [back]   (also when page 7 is asked for)
     *   Bob:   nobody      ->  [back]
     */
    @Test
    void noPageRowWhenEverythingFitsOnOnePage() throws Exception {
        for (int i = 1; i <= 5; i++) {
            repositories.getTrust().trust(String.format("P%02d", i), "Alice", null);
        }
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE trust_edges
                    SET created_at = CAST(substr(owner_mc_name, 2) AS INTEGER)
                    WHERE trusted_mc_name = 'Alice'
                    """);
        }

        assertEquals(List.of(
                "@P05>tmb:prt:123:0:Alice:P05", "@P04>tmb:prt:123:0:Alice:P04", "@P03>tmb:prt:123:0:Alice:P03",
                "@P02>tmb:prt:123:0:Alice:P02", "@P01>tmb:prt:123:0:Alice:P01",
                "telegram.button.back>tmb:pr:123:0:Alice"),
                keyboard("Alice", Section.TRUSTERS, 0));

        assertEquals(keyboard("Alice", Section.TRUSTERS, 0), keyboard("Alice", Section.TRUSTERS, 7));

        assertEquals(List.of("telegram.button.back>tmb:pr:123:0:Bob"),
                keyboard("Bob", Section.TRUSTERS, 0));
    }

    /*
     * Picking a player from a list opens a screen of its own: the path of the list with
     * the player at the end, how many interaction rows each of the two has towards the
     * other, and a single Back to the same page of the list.
     *
     *   Alice's trusters: P_a
     *   P_a -> Alice: 2 rows (a legacy one and an activity box)
     *   Alice -> P_a: 1 row, however many times she acted
     *
     *   list button   ->  tmb:prt:123:0:Alice:P_a
     *   text          ->  "Alice | R / T / @P\_a: | 2 to Alice, 1 to P\_a"
     *   keyboard      ->  [back -> tmb:prt:123:1:Alice]   (opened from page 1)
     */
    @Test
    void playerScreenShowsInteractionsBetweenTheTwoAndBackToTheSamePage() throws Exception {
        repositories.getTrust().trust("P_a", "Alice", null);
        var writes = repositories.getInteractionWrites();
        writes.increment("P_a", "Alice", SuspiciousActionType.BREAK_BLOCK);
        writes.incrementInActivityBox("P_a", "Alice", 1, 0, 0, 500, SuspiciousActionType.BREAK_BLOCK);
        writes.increment("Alice", "P_a", SuspiciousActionType.BREAK_BLOCK);
        writes.increment("Alice", "P_a", SuspiciousActionType.PLACE_BLOCK);
        writes.increment("Alice", "P_a", SuspiciousActionType.PLACE_BLOCK);

        assertEquals("@P_a>tmb:prt:123:0:Alice:P_a", keyboard("Alice", Section.TRUSTERS, 0).get(0));
        assertEquals("Alice | R / T / @P\\_a: | 2 to Alice, 1 to P\\_a",
                builder.buildPlayerText("Alice", Section.TRUSTERS, "P_a"));
        assertEquals(List.of("telegram.button.back>tmb:prt:123:1:Alice"),
                lines(builder.buildPlayerKeyboard("Alice", Section.TRUSTERS, 1, TELEGRAM_USER)));
    }

    /*
     * Telegram refuses the whole keyboard when one button's callback data is over 64
     * bytes. Two names that no longer fit make that one button inert; the rest stay.
     *
     *   profile of a 37-letter name, trusted by "Short" and by a 16-letter name
     *
     *   Short        ->  opens
     *   16 letters   ->  text only, no callback
     */
    @Test
    void playerButtonWhoseCallbackDataIsTooLongDoesNothing() throws Exception {
        String longProfile = "A".repeat(37);
        String longListed = "L".repeat(16);
        repositories.getTrust().trust("Short", longProfile, null);
        repositories.getTrust().trust(longListed, longProfile, null);
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE trust_edges SET created_at = 1");
        }

        assertEquals(List.of(
                "@" + longListed,
                "@Short>tmb:prt:123:0:" + longProfile + ":Short",
                "telegram.button.back>tmb:pr:123:0:" + longProfile),
                keyboard(longProfile, Section.TRUSTERS, 0));
    }

    /** One line per keyboard row, buttons of a row joined by " | ". */
    private List<String> keyboard(String player, Section section, int page) throws Exception {
        return lines(builder.buildKeyboard(player, section, page, TELEGRAM_USER));
    }

    private List<String> lines(String json) throws Exception {
        JSONArray rows = (JSONArray) ((JSONObject) new JSONParser().parse(json)).get("inline_keyboard");

        List<String> lines = new ArrayList<>();
        for (Object row : rows) {
            List<String> buttons = new ArrayList<>();
            for (Object button : (JSONArray) row) {
                String text = (String) ((JSONObject) button).get("text");
                String callback = (String) ((JSONObject) button).get("callback_data");
                if (callback.equals(payloads.encodeNoop())) {
                    buttons.add(text);
                } else {
                    buttons.add(text + ">" + callback);
                }
            }
            lines.add(String.join(" | ", buttons));
        }
        return lines;
    }
}
