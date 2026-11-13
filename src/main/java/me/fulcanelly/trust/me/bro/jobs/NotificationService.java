package me.fulcanelly.trust.me.bro.jobs;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import me.fulcanelly.tgbridge.tapi.Message;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.config.MinInteractionThresholds;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.database.repository.model.MaterialTotal;
import me.fulcanelly.trust.me.bro.service.NotificationSkipReason;
import me.fulcanelly.trust.me.bro.service.text.TelegramWarningMessageBuilder;

public final class NotificationService implements Runnable {

    private final AppContext context;
    private final TelegramWarningMessageBuilder messageBuilder;

    public NotificationService(AppContext context) {
        this.context = context;
        var bridge = context.getBridge();
        this.messageBuilder = new TelegramWarningMessageBuilder(
                bridge.getReception(),
                context.getCallbackPayloads(),
                context.getMessages(),
                context.getCoreProtect(),
                context.getRepositories().getRegions());
    }

    @Override
    public void run() {
        // Do not reloadConfig() here — FileConfiguration reload is not async-safe.
        var plugin = context.getPlugin();
        var mainConfig = context.getBridge().getMainConfig();
        boolean groupNotificationsEnabled = plugin.getConfig().getBoolean("telegram.notify", true);
        boolean ownerDmNotificationsEnabled = plugin.getConfig().getBoolean("telegram.owner-dm-notify", true);

        Long groupChatId = null;
        if (groupNotificationsEnabled && mainConfig.getChatId() != null
                && !mainConfig.getChatId().isBlank()) {
            try {
                groupChatId = Long.parseLong(mainConfig.getChatId());
            } catch (NumberFormatException e) {
                context.getLogger().warning("Invalid Telegram group chat id: " + e.getMessage());
            }
        }
        if (!groupNotificationsEnabled && !ownerDmNotificationsEnabled) {
            return;
        }

        long debounceMillis = Math.max(1, plugin.getConfig().getLong("detection.debounce-time-sec", 60)) * 1000L;
        MinInteractionThresholds minInteractions = MinInteractionThresholds.from(plugin.getConfig());
        try {
            var interactors = context.getRepositories().getPendingInteractions()
                    .findInteractorsReadyForNotification(debounceMillis, minInteractions);

            for (String interactor : interactors) {
                List<InteractionCount> pending = context.getRepositories().getPendingInteractions()
                        .findPendingByInteractor(interactor, minInteractions);
                if (pending.isEmpty()) {
                    continue;
                }

                if (ownerDmNotificationsEnabled) {
                    sendOwnerDirectMessages(interactor, pending);
                }
                if (groupChatId != null) {
                    Optional<String> skipReason = sendGroupNotification(groupChatId, interactor, pending);
                    if (skipReason.isPresent()) {
                        context.getLogger().info("Skipped group notification for " + interactor
                                + " (" + skipReason.get() + ")");
                    }
                }
            }
        } catch (Exception e) {
            context.getLogger().warning("Trust notification job failed: " + e.getMessage());
        }
    }

    private void sendOwnerDirectMessages(String interactor, List<InteractionCount> pending) {
        Map<String, List<InteractionCount>> byOwner = pending.stream()
                .collect(Collectors.groupingBy(
                        InteractionCount::getOwnerPlayer,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<String, List<InteractionCount>> entry : byOwner.entrySet()) {
            String owner = entry.getKey();
            List<InteractionCount> ownerRows = entry.getValue();
            try {
                var notifications = context.getRepositories().getNotifications();
                var preferences = context.getRepositories().getTelegramNotificationPreferences();
                if (!preferences.isEnabled(owner)) {
                    continue;
                }

                Optional<Long> linkedTelegramUser = context.getBridge().getReception().getTgByUser(owner);
                if (linkedTelegramUser.isEmpty()) {
                    continue;
                }

                List<InteractionCount> undelivered = ownerRows.stream()
                        .filter(row -> {
                            try {
                                return !notifications.hasTelegramOwnerDelivery(row.getId());
                            } catch (SQLException e) {
                                throw new IllegalStateException(e);
                            }
                        })
                        .collect(Collectors.toList());
                if (undelivered.isEmpty()) {
                    continue;
                }

                List<Long> ids = undelivered.stream()
                        .map(InteractionCount::getId)
                        .collect(Collectors.toList());
                List<MaterialTotal> materials = context.getRepositories()
                        .getInteractionMaterials()
                        .sumByInteractionCountIds(ids);
                int mergeDistance = Math.max(
                        0,
                        context.getPlugin().getConfig().getInt("detection.split-by-regions.merge-distance", 500));

                String messageText = messageBuilder.build(
                        interactor, undelivered, undelivered.size(), materials, mergeDistance);
                Message message = context.getBridge().getBot().sendMessage(
                        linkedTelegramUser.get(),
                        messageText,
                        messageBuilder.buildKeyboard(interactor));

                for (InteractionCount row : undelivered) {
                    notifications.markTelegramOwnerDelivered(row.getId(), owner, interactor);
                }
                context.getLogger().info("Sent private Telegram warning to " + owner + " about " + interactor
                        + " (message " + message.getMsgId() + ")");
            } catch (Exception e) {
                context.getLogger().warning("Private Telegram warning to " + owner + " failed: " + e.getMessage());
            }
        }
    }

    private Optional<String> sendGroupNotification(
            long chatId,
            String interactor,
            List<InteractionCount> pending) throws SQLException {
        List<InteractionCount> counts = selectOwnersForNotification(pending);
        if (requiresLinkedOwner() && !hasLinkedOwner(counts)) {
            markSkipped(pending, NotificationSkipReason.NO_LINKED_OWNER);
            return Optional.of(NotificationSkipReason.NO_LINKED_OWNER);
        }
        if (counts.isEmpty()) {
            markSkipped(pending, NotificationSkipReason.NO_LINKED_OWNER);
            return Optional.of(NotificationSkipReason.NO_LINKED_OWNER);
        }

        List<Long> ids = counts.stream()
                .map(InteractionCount::getId)
                .collect(Collectors.toList());
        List<MaterialTotal> materials = context.getRepositories()
                .getInteractionMaterials()
                .sumByInteractionCountIds(ids);
        int mergeDistance = Math.max(
                0,
                context.getPlugin().getConfig().getInt("detection.split-by-regions.merge-distance", 500));

        String messageText = messageBuilder.build(interactor, counts, pending.size(), materials, mergeDistance);
        Message message = context.getBridge().getBot().sendMessage(
                chatId, messageText, messageBuilder.buildKeyboard(interactor));

        long notificationId = context.getRepositories().getNotifications()
                .insertTelegram(interactor, chatId, message.getMsgId());
        context.getRepositories().getPendingInteractions().attachNotificationByIds(ids, notificationId);
        return Optional.empty();
    }

    private void markSkipped(List<InteractionCount> rows, String reason) throws SQLException {
        context.getRepositories().getPendingInteractions().markSkippedByIds(
                rows.stream().map(InteractionCount::getId).collect(Collectors.toList()),
                reason);
    }

    private List<InteractionCount> selectOwnersForNotification(List<InteractionCount> pending) {
        int limit = Math.max(1, context.getPlugin().getConfig().getInt("telegram.max-owners-per-notification", 10));
        return pending.stream()
                .sorted(Comparator
                        .comparing((InteractionCount count) -> isLinked(count.getOwnerPlayer()))
                        .reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private boolean requiresLinkedOwner() {
        return context.getPlugin().getConfig().getBoolean("telegram.only-for-linked-players", true);
    }

    private boolean hasLinkedOwner(List<InteractionCount> counts) {
        return counts.stream().anyMatch(count -> isLinked(count.getOwnerPlayer()));
    }

    private boolean isLinked(String ownerPlayer) {
        return context.getBridge().getReception().getTgByUser(ownerPlayer).isPresent();
    }
}
