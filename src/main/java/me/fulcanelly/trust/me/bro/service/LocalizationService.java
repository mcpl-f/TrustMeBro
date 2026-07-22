package me.fulcanelly.trust.me.bro.service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class LocalizationService {

    private static final String DEFAULT_LOCALE = "ru";
    private static final String FALLBACK_LOCALE = "en";

    private final FileConfiguration messages;
    private final FileConfiguration fallbackMessages;

    public LocalizationService(Plugin plugin) {
        String locale = plugin.getConfig().getString("default-locale", DEFAULT_LOCALE);
        messages = load(plugin, normalize(locale));
        fallbackMessages = load(plugin, FALLBACK_LOCALE);
    }

    public String format(String key, Object... placeholders) {
        String message = messages.getString(key);
        if (message == null) {
            message = fallbackMessages.getString(key, key);
        }

        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            message = message.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return message;
    }

    private FileConfiguration load(Plugin plugin, String locale) {
        try (InputStream stream = plugin.getResource("locales/" + locale + ".yml")) {
            if (stream == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new YamlConfiguration();
        }
    }

    private String normalize(String locale) {
        if (locale == null || locale.isBlank()) {
            return DEFAULT_LOCALE;
        }
        return locale.toLowerCase(Locale.ROOT);
    }
}
