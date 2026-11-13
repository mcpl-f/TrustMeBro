package me.fulcanelly.trust.me.bro.listener.telegram.command;

import java.util.regex.Pattern;

import com.google.common.eventbus.Subscribe;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.tapi.events.CommandEvent;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;

@RequiredArgsConstructor
public final class TelegramNotificationSettingsCommand {

    private static final Pattern COMMAND = Pattern.compile("^/notifications(@\\S+)?(?:\\s+(on|off|status))?\\s*$");

    private final AppContext context;

    @Subscribe
    public void onCommand(CommandEvent event) {
        String text = event.getMessage().getText();
        if (text == null) {
            return;
        }

        var matcher = COMMAND.matcher(text);
        if (!matcher.matches()) {
            return;
        }

        long telegramUserId = event.getMessage().getFrom().getId();
        var linkedPlayer = context.getBridge().getReception().getPlayerByTg(telegramUserId);
        if (linkedPlayer.isEmpty()) {
            event.getMessage().reply(context.getMessages().format("telegram.command.notifications.not-linked"));
            return;
        }

        String option = matcher.group(2) == null ? "status" : matcher.group(2);
        String owner = linkedPlayer.get();
        try {
            var preferences = context.getRepositories().getTelegramNotificationPreferences();
            if (!option.equals("status")) {
                preferences.setEnabled(owner, option.equals("on"));
            }

            boolean enabled = preferences.isEnabled(owner);
            event.getMessage().reply(context.getMessages().format(
                    enabled
                            ? "telegram.command.notifications.enabled"
                            : "telegram.command.notifications.disabled"));
        } catch (Exception e) {
            context.getLogger().warning("Telegram notification preference command failed: " + e.getMessage());
            event.getMessage().reply(context.getMessages().format("telegram.command.notifications.failed"));
        }
    }
}
