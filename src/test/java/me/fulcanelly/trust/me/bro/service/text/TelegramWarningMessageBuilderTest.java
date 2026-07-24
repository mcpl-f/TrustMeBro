package me.fulcanelly.trust.me.bro.service.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.Test;

import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
import me.fulcanelly.trust.me.bro.service.LocalizationService;
import me.fulcanelly.trust.me.bro.service.TrustCallbackPayloadService;

public class TelegramWarningMessageBuilderTest {

    @Test
    void keyboardHasOneTrustAndOneReportButtonForInteractor() throws Exception {
        TelegramWarningMessageBuilder builder = new TelegramWarningMessageBuilder(
                new SignupLoginReception(),
                new TrustCallbackPayloadService(),
                englishMessages());

        JSONObject keyboard = (JSONObject) new JSONParser().parse(builder.buildKeyboard("Griefer"));
        JSONArray rows = (JSONArray) keyboard.get("inline_keyboard");
        JSONArray row = (JSONArray) rows.get(0);
        JSONObject trust = (JSONObject) row.get(0);
        JSONObject report = (JSONObject) row.get(1);

        assertEquals(1, rows.size());
        assertEquals(2, row.size());
        assertEquals("Trust", trust.get("text"));
        assertEquals("tmb:t:Griefer", trust.get("callback_data"));
        assertEquals("Report", report.get("text"));
        assertEquals("tmb:r:Griefer", report.get("callback_data"));
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
                        return TelegramWarningMessageBuilderTest.class
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
