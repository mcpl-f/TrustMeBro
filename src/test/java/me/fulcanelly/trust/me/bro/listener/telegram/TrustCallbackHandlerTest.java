package me.fulcanelly.trust.me.bro.listener.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import com.google.common.eventbus.EventBus;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;

import me.fulcanelly.tgbridge.tapi.TGBot;
import me.fulcanelly.tgbridge.tapi.events.CallbackQueryEvent;
import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
import me.fulcanelly.trust.me.bro.database.MigrationRunner;
import me.fulcanelly.trust.me.bro.database.repository.local.InteractionCountsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.ReportRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.TrustRepository;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;
import me.fulcanelly.trust.me.bro.service.LocalizationService;
import me.fulcanelly.trust.me.bro.service.TrustCallbackPayloadService;

public class TrustCallbackHandlerTest {

    private static final long TELEGRAM_USER_ID = 42L;
    private static final String OWNER = "Owner";
    private static final String INTERACTOR = "Griefer";

    /*
     * Owner is linked and an interaction exists.
     * click report  -> report row is created.
     * click report  -> same state, "already reported".
     */
    @Test
    void reportClickCreatesReportAndSecondReportIsRejected() throws Exception {
        Harness harness = new Harness(Optional.of(OWNER));
        harness.interactionCounts.increment(INTERACTOR, OWNER, SuspiciousActionType.BREAK_BLOCK);

        harness.handle("tmb:r:" + INTERACTOR);
        assertTrue(harness.reports.exists(INTERACTOR, OWNER));
        assertEquals("Owner reported Griefer", harness.bot.lastAnswer());

        harness.handle("tmb:r:" + INTERACTOR);
        assertEquals("Owner already reported Griefer", harness.bot.lastAnswer());
    }

    /*
     * Owner is linked and already reported the interactor.
     * click trust   -> previous report is cancelled.
     * final state   -> trust row exists, report row is gone.
     */
    @Test
    void trustClickCancelsExistingReport() throws Exception {
        Harness harness = new Harness(Optional.of(OWNER));
        harness.interactionCounts.increment(INTERACTOR, OWNER, SuspiciousActionType.BREAK_BLOCK);
        harness.reports.report(OWNER, TELEGRAM_USER_ID, INTERACTOR, OWNER);

        harness.handle("tmb:t:" + INTERACTOR);

        assertFalse(harness.reports.exists(INTERACTOR, OWNER));
        assertTrue(harness.trust.isTrusted(OWNER, INTERACTOR));
        assertEquals("Owner now trusts Griefer", harness.bot.lastAnswer());
    }

    /*
     * Owner is linked and already trusts the interactor.
     * click report  -> previous trust is cancelled.
     * final state   -> report row exists, trust row is gone.
     */
    @Test
    void reportClickCancelsExistingTrust() throws Exception {
        Harness harness = new Harness(Optional.of(OWNER));
        harness.interactionCounts.increment(INTERACTOR, OWNER, SuspiciousActionType.BREAK_BLOCK);
        harness.trust.trust(OWNER, INTERACTOR, TELEGRAM_USER_ID);

        harness.handle("tmb:r:" + INTERACTOR);

        assertFalse(harness.trust.isTrusted(OWNER, INTERACTOR));
        assertTrue(harness.reports.exists(INTERACTOR, OWNER));
        assertEquals("Owner reported Griefer", harness.bot.lastAnswer());
    }

    /*
     * Owner is linked and already trusts the interactor.
     * click trust   -> no duplicate write or state change.
     * final state   -> still trusted, "already trusts".
     */
    @Test
    void repeatedTrustClickIsIdempotent() throws Exception {
        Harness harness = new Harness(Optional.of(OWNER));
        harness.interactionCounts.increment(INTERACTOR, OWNER, SuspiciousActionType.BREAK_BLOCK);
        harness.trust.trust(OWNER, INTERACTOR, TELEGRAM_USER_ID);

        harness.handle("tmb:t:" + INTERACTOR);

        assertTrue(harness.trust.isTrusted(OWNER, INTERACTOR));
        assertFalse(harness.reports.exists(INTERACTOR, OWNER));
        assertEquals("Owner already trusts Griefer", harness.bot.lastAnswer());
    }

    /*
     * Telegram user has no linked Minecraft account.
     * click report  -> handler stops before DB state changes.
     * final state   -> no report row, link-required answer.
     */
    @Test
    void unlinkedTelegramUserCannotReport() throws Exception {
        Harness harness = new Harness(Optional.empty());
        harness.interactionCounts.increment(INTERACTOR, OWNER, SuspiciousActionType.BREAK_BLOCK);

        harness.handle("tmb:r:" + INTERACTOR);

        assertFalse(harness.reports.exists(INTERACTOR, OWNER));
        assertEquals("Link your Telegram account to Minecraft first", harness.bot.lastAnswer());
    }

    /*
     * Owner is linked but there is no pending interaction.
     * click report  -> stale callback is rejected.
     * final state   -> no report row, interaction-gone answer.
     */
    @Test
    void missingInteractionIsRejectedBeforeReport() throws Exception {
        Harness harness = new Harness(Optional.of(OWNER));

        harness.handle("tmb:r:" + INTERACTOR);

        assertFalse(harness.reports.exists(INTERACTOR, OWNER));
        assertEquals("Interaction is gone", harness.bot.lastAnswer());
    }

    private static class Harness {

        private final TestBot bot = new TestBot();
        private final TrustRepository trust;
        private final ReportRepository reports;
        private final InteractionCountsRepository interactionCounts;
        private final TrustCallbackHandler handler;

        private Harness(Optional<String> linkedPlayer) throws Exception {
            Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
            new MigrationRunner(connection).migrate();

            trust = new TrustRepository(connection);
            reports = new ReportRepository(connection);
            interactionCounts = new InteractionCountsRepository(connection);
            handler = new TrustCallbackHandler(
                    new TrustCallbackPayloadService(),
                    englishMessages(),
                    new FakeReception(linkedPlayer),
                    trust,
                    reports,
                    interactionCounts,
                    Logger.getLogger(TrustCallbackHandlerTest.class.getName()));
        }

        private void handle(String data) {
            handler.onCallback(callback(data, bot));
        }
    }

    private static class FakeReception extends SignupLoginReception {

        private final Optional<String> linkedPlayer;

        private FakeReception(Optional<String> linkedPlayer) {
            this.linkedPlayer = linkedPlayer;
        }

        @Override
        public Optional<String> getPlayerByTg(long userId) {
            return linkedPlayer;
        }
    }

    private static class TestBot extends TGBot {

        private final List<String> answers = new ArrayList<>();

        private TestBot() {
            super("test-token", new EventBus(), Logger.getLogger(TestBot.class.getName()));
        }

        @Override
        public void answerCallbackQuery(String callbackQueryId, String text) {
            answers.add(text);
        }

        private String lastAnswer() {
            return answers.get(answers.size() - 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static CallbackQueryEvent callback(String data, TestBot bot) {
        JSONObject from = new JSONObject();
        from.put("id", TELEGRAM_USER_ID);

        JSONObject callback = new JSONObject();
        callback.put("id", "callback-id");
        callback.put("data", data);
        callback.put("from", from);
        return new CallbackQueryEvent(callback, bot);
    }

    private static LocalizationService englishMessages() {
        FileConfiguration config = new YamlConfiguration();
        config.set("default-locale", "en");
        Plugin plugin = (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[] { Plugin.class },
                (proxy, method, args) -> {
                    if ("getConfig".equals(method.getName())) {
                        return config;
                    }
                    if ("getResource".equals(method.getName())) {
                        return TrustCallbackHandlerTest.class
                                .getClassLoader()
                                .getResourceAsStream((String) args[0]);
                    }
                    if ("toString".equals(method.getName())) {
                        return "trust-me-bro-test-plugin";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return new LocalizationService(plugin);
    }
}
