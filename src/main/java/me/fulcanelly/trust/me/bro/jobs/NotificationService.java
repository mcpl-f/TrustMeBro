package me.fulcanelly.trust.me.bro.jobs;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import me.fulcanelly.tgbridge.tapi.Message;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.config.MinInteractionThresholds;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
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
        // Admins: /reload or restart; getConfig() reads the in-memory copy each tick.
        var plugin = context.getPlugin();
        var mainConfig = context.getBridge().getMainConfig();

        if (!plugin.getConfig().getBoolean("telegram.notify", true)) {
            return;
        }

        if (mainConfig.getChatId() == null || mainConfig.getChatId().isBlank()) {
            return;
        }

        context.getLogger().info("Finding interactors ready for notification");
        long chatId = Long.parseLong(mainConfig.getChatId());
        long debounceMillis = Math.max(1, plugin.getConfig().getLong("detection.debounce-time-sec", 60)) * 1000L;
        MinInteractionThresholds minInteractions = MinInteractionThresholds.from(plugin.getConfig());
        try {
            var interactors = context.getRepositories().getInteractionCounts()
                    .findInteractorsReadyForNotification(debounceMillis, minInteractions);

            context.getLogger().info("Found " + interactors.size() + " interactors ready for notification");
            for (String interactor : interactors) {

                long start = System.currentTimeMillis();
                Optional<String> skipReason = sendNotification(chatId, interactor, minInteractions);
                long tookMs = System.currentTimeMillis() - start;

                if (skipReason.isEmpty()) {
                    context.getLogger().info("Sent notification about " + interactor + " in " + tookMs + "ms");
                    return;
                }

                context.getLogger().info("Skipped " + interactor + " (" + skipReason.get() + ") in " + tookMs + "ms");
            }
        } catch (Exception e) {
            context.getLogger().warning("Trust notification job failed: " + e.getMessage());
        }
    }



    /**
     * @return empty if a Telegram message was sent; otherwise the skip reason code
     *         (also persisted on the pending rows when applicable)
     */
    private Optional<String> sendNotification(
            long chatId,
            String interactor,
            MinInteractionThresholds minInteractions //
    ) throws SQLException {
        var interactionCounts = context.getRepositories().getInteractionCounts();
        var notifications = context.getRepositories().getNotifications();
        var plugin = context.getPlugin();

        List<InteractionCount> pending = interactionCounts.findPendingByInteractor(
                interactor,
                minInteractions);
        if (pending.isEmpty()) {
            return Optional.of(NotificationSkipReason.ALREADY_HANDLED);
        }

        var totalInteractions = pending.size();

        List<InteractionCount> counts = selectOwnersForNotification(pending);

        if (requiresLinkedOwner() && !hasLinkedOwner(counts)) {
            markSkipped(pending, NotificationSkipReason.NO_LINKED_OWNER);
            return Optional.of(NotificationSkipReason.NO_LINKED_OWNER);
        }

        if (counts.isEmpty()) {
            markSkipped(pending, NotificationSkipReason.NO_LINKED_OWNER);
            return Optional.of(NotificationSkipReason.NO_LINKED_OWNER);
        }

        int mergeDistance = Math.max(
                0,
                plugin.getConfig().getInt("detection.split-by-regions.merge-distance", 500));
        Message message = context.getBridge().getBot().sendMessage(
                chatId,
                messageBuilder.build(interactor, counts, totalInteractions, mergeDistance),
                messageBuilder.buildKeyboard(interactor));

        long notificationId = notifications.insertTelegram(interactor, chatId, message.getMsgId());
        interactionCounts.attachNotificationByIds(
                counts.stream().map(InteractionCount::getId).collect(Collectors.toList()),
                notificationId);
        return Optional.empty();
    }

    private void markSkipped(List<InteractionCount> rows, String reason) throws SQLException {
        context.getRepositories().getInteractionCounts().markSkippedByIds(
                rows.stream().map(InteractionCount::getId).collect(Collectors.toList()),
                reason);
    }

    private List<InteractionCount> selectOwnersForNotification(List<InteractionCount> pending) {
        int limit = Math.max(1, context.getPlugin().getConfig().getInt("telegram.max-owners-per-notification", 10));
        return pending.stream()
                .sorted(Comparator
                        .comparing((InteractionCount count) -> isLinked(count.getOwnerPlayer()))
                        // .thenComparing((InteractionCount count) ->
                        // lastPlayed(count.getOwnerPlayer()))
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

    @SuppressWarnings("deprecation")
    private long lastPlayed(String ownerPlayer) {
        // Must not call OfflinePlayer from the async notification job.
        // If lastPlayed sort is re-enabled: snapshot on the main thread (sync task /
        // cache), then read the map here.
        return context.getPlugin().getServer().getOfflinePlayer(ownerPlayer).getLastPlayed();
    }
}
