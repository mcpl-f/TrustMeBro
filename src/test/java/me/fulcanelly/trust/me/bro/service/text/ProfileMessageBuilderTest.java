package me.fulcanelly.trust.me.bro.service.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
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
 * The /myprofile keyboard. No locale files are loaded, so every button text is its own
 * message key.
 */
public class ProfileMessageBuilderTest {

    private static final long TELEGRAM_USER = 123L;

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
                    return null; // getResource: no locale files
                });
        builder = new ProfileMessageBuilder(new LocalizationService(plugin), payloads, repositories);
    }

    @AfterEach
    void closeDatabase() throws Exception {
        connection.close();
    }

    /*
     * Menus: buttons go down one level, Back goes up one level.
     *
     *   MAIN        [reputation] [ratings]
     *   REPUTATION  [trusters] [reporters] [back -> MAIN]
     */
    @Test
    void menuButtonsOpenChildrenAndBackOpensParent() throws Exception {
        assertEquals(List.of(
                "telegram.command.myprofile.button.reputation>tmb:pr:123:0",
                "telegram.command.myprofile.button.ratings>tmb:pm:123:0"),
                keyboard("Alice", Section.MAIN, 0));

        assertEquals(List.of(
                "telegram.command.myprofile.button.trusters>tmb:prt:123:0",
                "telegram.command.myprofile.button.reporters>tmb:prr:123:0",
                "telegram.button.back>tmb:p:123:0"),
                keyboard("Alice", Section.REPUTATION, 0));
    }

    /*
     * 11 players trust Alice, 5 per page. Player rows do nothing yet, the page number
     * does nothing, arrows move one page, Back goes to the section's parent.
     *
     *   page 0:  P01..P05    [1] [>]
     *   page 1:  P06..P10    [<] [2] [>]
     *   page 2:  P11         [<] [3]
     */
    @Test
    void playersArePagedFiveAtATimeWithArrowsOnlyWhereThereIsAPage() throws Exception {
        for (int i = 1; i <= 11; i++) {
            repositories.getTrust().trust(String.format("P%02d", i), "Alice", null);
        }

        assertEquals(List.of(
                "P01", "P02", "P03", "P04", "P05",
                "1 | telegram.button.page-next>tmb:prt:123:1",
                "telegram.button.back>tmb:pr:123:0"),
                keyboard("Alice", Section.TRUSTERS, 0));

        assertEquals(List.of(
                "P06", "P07", "P08", "P09", "P10",
                "telegram.button.page-previous>tmb:prt:123:0 | 2 | telegram.button.page-next>tmb:prt:123:2",
                "telegram.button.back>tmb:pr:123:0"),
                keyboard("Alice", Section.TRUSTERS, 1));

        assertEquals(List.of(
                "P11",
                "telegram.button.page-previous>tmb:prt:123:1 | 3",
                "telegram.button.back>tmb:pr:123:0"),
                keyboard("Alice", Section.TRUSTERS, 2));
    }

    /*
     * No page row when everything fits: exactly one full page, or nobody at all.
     *
     *   Alice: 5 trusters  ->  P01..P05, [back]
     *   Bob:   nobody      ->  [back]
     */
    @Test
    void noPageRowWhenEverythingFitsOnOnePage() throws Exception {
        for (int i = 1; i <= 5; i++) {
            repositories.getTrust().trust(String.format("P%02d", i), "Alice", null);
        }

        assertEquals(List.of(
                "P01", "P02", "P03", "P04", "P05",
                "telegram.button.back>tmb:pr:123:0"),
                keyboard("Alice", Section.TRUSTERS, 0));

        assertEquals(List.of("telegram.button.back>tmb:pr:123:0"),
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
