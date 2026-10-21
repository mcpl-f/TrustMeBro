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
     * 11 players trust Alice, 5 per page. Player rows do nothing yet, the page number
     * does nothing, arrows move one page, Back goes to the section's parent.
     *
     *   page 0:  P01..P05    [1 / 3] [>]
     *   page 1:  P06..P10    [<] [2 / 3] [>]
     *   page 2:  P11         [<] [3 / 3]
     */
    @Test
    void playersArePagedFiveAtATimeWithArrowsOnlyWhereThereIsAPage() throws Exception {
        for (int i = 1; i <= 11; i++) {
            repositories.getTrust().trust(String.format("P%02d", i), "Alice", null);
        }

        assertEquals(List.of(
                "@P01", "@P02", "@P03", "@P04", "@P05",
                "1 / 3 | telegram.button.page-next>tmb:prt:123:1:Alice",
                "telegram.button.back>tmb:pr:123:0:Alice"),
                keyboard("Alice", Section.TRUSTERS, 0));

        assertEquals(List.of(
                "@P06", "@P07", "@P08", "@P09", "@P10",
                "telegram.button.page-previous>tmb:prt:123:0:Alice | 2 / 3 | telegram.button.page-next>tmb:prt:123:2:Alice",
                "telegram.button.back>tmb:pr:123:0:Alice"),
                keyboard("Alice", Section.TRUSTERS, 1));

        assertEquals(List.of(
                "@P11",
                "telegram.button.page-previous>tmb:prt:123:1:Alice | 3 / 3",
                "telegram.button.back>tmb:pr:123:0:Alice"),
                keyboard("Alice", Section.TRUSTERS, 2));
    }

    /*
     * No page row when everything fits: exactly one full page, or nobody at all.
     * A button of an older message may ask for a page that no longer exists: it shows
     * the last one.
     *
     *   Alice: 5 trusters  ->  P01..P05, [back]   (also when page 7 is asked for)
     *   Bob:   nobody      ->  [back]
     */
    @Test
    void noPageRowWhenEverythingFitsOnOnePage() throws Exception {
        for (int i = 1; i <= 5; i++) {
            repositories.getTrust().trust(String.format("P%02d", i), "Alice", null);
        }

        assertEquals(List.of(
                "@P01", "@P02", "@P03", "@P04", "@P05",
                "telegram.button.back>tmb:pr:123:0:Alice"),
                keyboard("Alice", Section.TRUSTERS, 0));

        assertEquals(keyboard("Alice", Section.TRUSTERS, 0), keyboard("Alice", Section.TRUSTERS, 7));

        assertEquals(List.of("telegram.button.back>tmb:pr:123:0:Bob"),
                keyboard("Bob", Section.TRUSTERS, 0));
    }

    /** One line per keyboard row, buttons of a row joined by " | ". */
    private List<String> keyboard(String player, Section section, int page) throws Exception {
        String json = builder.buildKeyboard(player, section, page, TELEGRAM_USER);
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
