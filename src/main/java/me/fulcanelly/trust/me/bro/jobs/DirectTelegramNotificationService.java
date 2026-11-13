package me.fulcanelly.trust.me.bro.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import me.fulcanelly.tgbridge.tapi.Message;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.config.MinInteractionThresholds;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.database.repository.model.MaterialTotal;
import me.fulcanelly.trust.me.bro.service.text.TelegramWarningMessageBuilder;

public final class DirectTelegramNotificationService implements Runnable {

    private final AppContext context;
    private final TelegramWarningMessageBuilder messageBuilder;

    public DirectTelegramNotificationService(AppContext context) {
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
        if (!context.getPlugin().getConfig().getBoolean("telegram.owner-dm-notify", true)) {
            return;
        }

        long debounceMillis = Math.max(
                1,
                context.getPlugin().getConfig().getLong("detection.debounce-time-sec", 60)) * 1000L;
        MinInteractionThresholds thresholds = MinInteractionThresholds.from(context.getPlugin().getConfig());

        try {
            var pendingRepository = context.getRepositories().getPendingInteractions();
            List<String> interactors = pendingRepository.findInteractorsReadyForDirectTelegramNotification(
                    debounceMillis, thresholds);
            for (String interactor : interactors) {
                List<InteractionCount> pending = pendingRepository.findPendingForDirectTelegramNotification(interactor, thresholds);
                if (!pending.isEmpty()) {
                    sendToOwners(interactor, pending);
                }
            }
        } catch (Exception e) {
            context.getLogger().warning("Direct Telegram notification job failed: " + e.getMessage());
        }
    }

    private void sendToOwners(String interactor, List<InteractionCount> pending) {
        Map<String, List<InteractionCount>> byOwner = pending.stream()
                .collect(Collectors.groupingBy(
                        InteractionCount::getOwnerPlayer,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<String, List<InteractionCount>> entry : byOwner.entrySet()) {
            String owner = entry.getKey();
            try {
                var preferences = context.getRepositories().getTelegramNotificationPreferences();
                if (!preferences.isEnabled(owner)) {
                    continue;
                }

                var telegramUserId = context.getBridge().getReception().getTgByUser(owner);
                if (telegramUserId.isEmpty()) {
                    continue;
                }

                List<InteractionCount> undelivered = entry.getValue();
                var deliveries = context.getRepositories().getDirectTelegramNotificationDeliveries();

                List<Long> ids = undelivered.stream()
                        .map(InteractionCount::getId)
                        .collect(Collectors.toList());
                List<MaterialTotal> materials = context.getRepositories()
                        .getInteractionMaterials()
                        .sumByInteractionCountIds(ids);
                int mergeDistance = Math.max(
                        0,
                        context.getPlugin().getConfig().getInt(
                                "detection.split-by-regions.merge-distance", 500));

                String text = messageBuilder.build(
                        interactor, undelivered, undelivered.size(), materials, mergeDistance);
                Message message = context.getBridge().getBot().sendMessage(
                        telegramUserId.get(), text, messageBuilder.buildKeyboard(interactor));

                for (InteractionCount row : undelivered) {
                    deliveries.markDelivered(row.getId(), owner, interactor);
                }
                context.getLogger().info("Sent direct Telegram notification to " + owner
                        + " (message " + message.getMsgId() + ")");
            } catch (Exception e) {
                context.getLogger().warning("Direct Telegram notification to " + owner
                        + " failed: " + e.getMessage());
            }
        }
    }
}
