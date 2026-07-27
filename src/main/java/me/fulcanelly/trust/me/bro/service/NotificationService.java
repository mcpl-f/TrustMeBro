package me.fulcanelly.trust.me.bro.service;

import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import me.fulcanelly.tgbridge.tapi.Message;
import me.fulcanelly.tgbridge.tapi.TGBot;
import me.fulcanelly.tgbridge.tools.MainConfig;
import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
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
    private final SignupLoginReception reception;
    private final MainConfig mainConfig;
    private final Logger logger;

    @Override
    public void run() {
        // Do not reloadConfig() here — FileConfiguration reload is not async-safe.
        // Admins: /reload or restart; getConfig() reads the in-memory copy each tick.
        if (!plugin.getConfig().getBoolean("telegram.notify", true)) {
            return;
        }

        if (mainConfig.getChatId() == null || mainConfig.getChatId().isBlank()) {
            return;
        }

        logger.info("Finding interactors ready for notification");
        long chatId = Long.parseLong(mainConfig.getChatId());
        long debounceMillis = Math.max(1, plugin.getConfig().getLong("detection.debounce-time-sec", 60)) * 1000L;
        try {
            var interactors = interactionCounts.findInteractorsReadyForNotification(debounceMillis);
            logger.info("Found " + interactors.size() + " interactors ready for notification");
            for (String interactor : interactors) {
                if (sendNotification(chatId, interactor)) {
                    return;
                }
            }
        } catch (Exception e) {
            logger.warning("Trust notification job failed: " + e.getMessage());
        }
    }

    private boolean sendNotification(long chatId, String interactor) throws SQLException {
        List<InteractionCount> pending = interactionCounts.findPendingByInteractor(interactor);
        if (pending.isEmpty()) {
            return false;
        }

        var totalInteractions = pending.size();

        List<InteractionCount> counts = selectOwnersForNotification(pending);

        if (requiresLinkedOwner() && !hasLinkedOwner(counts)) {
            return false;
        }

        if (counts.isEmpty()) {
            return false;
        }

        Message message = bot.sendMessage(
                chatId,
                messageBuilder.build(interactor, counts, totalInteractions),
                messageBuilder.buildKeyboard(interactor));

        long notificationId = notifications.insertTelegram(interactor, chatId, message.getMsgId());
        interactionCounts.attachNotificationByIds(
                counts.stream().map(InteractionCount::getId).collect(Collectors.toList()),
                notificationId);
        return true;
    }

    private List<InteractionCount> selectOwnersForNotification(List<InteractionCount> pending) {
        int limit = Math.max(1, plugin.getConfig().getInt("telegram.max-owners-per-notification", 10));
        return pending.stream()
                .sorted(Comparator
                        .comparing((InteractionCount count) -> isLinked(count.getOwnerPlayer()))
                        // .thenComparing((InteractionCount count) -> lastPlayed(count.getOwnerPlayer()))
                        .reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private boolean requiresLinkedOwner() {
        return plugin.getConfig().getBoolean("telegram.only-for-linked-players", true);
    }

    private boolean hasLinkedOwner(List<InteractionCount> counts) {
        return counts.stream().anyMatch(count -> isLinked(count.getOwnerPlayer()));
    }

    private boolean isLinked(String ownerPlayer) {
        return reception.getTgByUser(ownerPlayer).isPresent();
    }

    @SuppressWarnings("deprecation")
    private long lastPlayed(String ownerPlayer) {
        // Must not call OfflinePlayer from the async notification job.
        // If lastPlayed sort is re-enabled: snapshot on the main thread (sync task / cache), then read the map here.
        return plugin.getServer().getOfflinePlayer(ownerPlayer).getLastPlayed();
    }
}
