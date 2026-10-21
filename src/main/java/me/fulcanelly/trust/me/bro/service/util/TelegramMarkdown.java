package me.fulcanelly.trust.me.bro.service.util;

/**
 * tg-bridge sends messages with Telegram's legacy Markdown parse mode, where an
 * unpaired {@code _} or {@code *} fails the whole message with HTTP 400. Minecraft names
 * contain underscores, so every name put into a message text goes through here.
 */
public final class TelegramMarkdown {

    public static String escape(String text) {
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("`", "\\`");
    }
}
