package me.fulcanelly.trust.me.bro.listener.telegram.command;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.common.eventbus.Subscribe;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.tapi.events.CallbackQueryEvent;
import me.fulcanelly.tgbridge.tapi.events.CommandEvent;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;

/** /trustmotify — manage private Telegram warning notifications. */
@RequiredArgsConstructor
public final class TelegramNotificationSettingsCommand {

    private static final Pattern COMMAND = Pattern.compile("^/trustmotify(@\\S+)?\\s*$");
    private static final String CALLBACK_PREFIX = "tmbn:";

    private final AppContext context;

    @Subscribe
    public void onCommand(CommandEvent event) {
        String text = event.getMessage().getText();
        if (text == null || !COMMAND.matcher(text).matches()) {
            return;
        }

        long telegramUserId = event.getMessage().getFrom().getId();
        Optional<String> linkedPlayer = context.getBridge().getReception().getPlayerByTg(telegramUserId);
        if (linkedPlayer.isEmpty()) {
            event.getMessage().reply(context.getMessages().format("telegram.command.notifications.not-linked"));
            return;
        }

        try {
            boolean enabled = context.getRepositories()
                    .getTelegramNotificationPreferences()
                    .isEnabled(linkedPlayer.get());
            context.getBridge().getBot().sendMessage(
                    event.getMessage().getChat().getId(),
                    statusText(enabled),
                    keyboard(enabled, telegramUserId));
        } catch (SQLException e) {
            context.getLogger().warning("/trustmotify failed: " + e.getMessage());
            event.getMessage().reply(context.getMessages().format("telegram.command.notifications.failed"));
        }
    }

    @Subscribe
    public void onCallback(CallbackQueryEvent event) {
        String data = event.getData();
        if (data == null || !data.startsWith(CALLBACK_PREFIX)) {
            return;
        }

        String[] parts = data.substring(CALLBACK_PREFIX.length()).split(":", 2);
        if (parts.length != 2 || !parts[1].equals("on") && !parts[1].equals("off")) {
            event.answer(context.getMessages().format("callback.bad-action"));
            return;
        }

        long telegramUserId = event.getFrom().getId();
        if (!parts[0].equals(Long.toString(telegramUserId))) {
            event.answer(context.getMessages().format("callback.not-your-profile"));
            return;
        }

        Optional<String> linkedPlayer = context.getBridge().getReception().getPlayerByTg(telegramUserId);
        if (linkedPlayer.isEmpty()) {
            event.answer(context.getMessages().format("callback.link-required"));
            return;
        }

        try {
            boolean enabled = parts[1].equals("on");
            context.getRepositories().getTelegramNotificationPreferences()
                    .setEnabled(linkedPlayer.get(), enabled);
            context.getBridge().getBot().editMessage(
                    event.getMessage().getChat().getId(),
                    event.getMessage().getMsgId(),
                    statusText(enabled),
                    keyboard(enabled, telegramUserId));
            event.answer("");
        } catch (Exception e) {
            context.getLogger().warning("Telegram notification preference callback failed: " + e.getMessage());
            event.answer(context.getMessages().format("callback.db-error"));
        }
    }

    private String statusText(boolean enabled) {
        return context.getMessages().format(
                enabled
                        ? "telegram.command.notifications.status-enabled"
                        : "telegram.command.notifications.status-disabled");
    }

    private String keyboard(boolean enabled, long telegramUserId) {
        String buttonText = context.getMessages().format(
                enabled
                        ? "telegram.command.notifications.button-disable"
                        : "telegram.command.notifications.button-enable");
        String callbackData = CALLBACK_PREFIX + telegramUserId + ":" + (enabled ? "off" : "on");
        return "{\"inline_keyboard\":[[{\"text\":\""
                + buttonText
                + "\",\"callback_data\":\""
                + callbackData
                + "\"}]]}";
    }
}
