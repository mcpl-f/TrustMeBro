package me.fulcanelly.trust.me.bro.service;

import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

import me.fulcanelly.tgbridge.tapi.Message;
import me.fulcanelly.tgbridge.tapi.TGBot;
import me.fulcanelly.tgbridge.tools.MainConfig;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.database.repository.local.InteractionCountsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.NotificationRepository;
import me.fulcanelly.trust.me.bro.service.text.TelegramWarningMessageBuilder;

import org.bukkit.plugin.Plugin;

@RequiredArgsConstructor
public final class NotificationService implements Runnable {

    private final InteractionCountsRepository interactionCounts;
    private final NotificationRepository notifications;
    private final TelegramWarningMessageBuilder messageBuilder;
    private final TGBot bot;
    private final Plugin plugin;
    private final MainConfig mainConfig;
    private final long debounceMillis;
    private final Logger logger;

    @Override
    public void run() {
        plugin.reloadConfig();
        if (!plugin.getConfig().getBoolean("telegram.notify", true)) {
            return;
        }

        if (mainConfig.getChatId() == null || mainConfig.getChatId().isBlank()) {
            return;
        }

        long chatId = Long.parseLong(mainConfig.getChatId());
        try {
            for (String interactor : interactionCounts.findInteractorsReadyForNotification(debounceMillis)) {
                sendNotification(chatId, interactor);
            }
        } catch (Exception e) {
            logger.warning("Trust notification job failed: " + e.getMessage());
        }
    }

    private void sendNotification(long chatId, String interactor) throws SQLException {
        List<InteractionCount> counts = interactionCounts.findPendingByInteractor(interactor);
        if (counts.isEmpty()) {
            return;
        }

        Message message = bot.sendMessage(
                chatId,
                messageBuilder.build(interactor, counts),
                messageBuilder.buildKeyboard(interactor));
        long notificationId = notifications.insertTelegram(interactor, chatId, message.getMsgId());
        interactionCounts.attachNotification(interactor, notificationId);
    }
}
