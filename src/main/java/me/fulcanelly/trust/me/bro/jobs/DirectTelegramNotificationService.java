package me.fulcanelly.trust.me.bro.jobs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.scheduler.BukkitTask;

import me.fulcanelly.tgbridge.tapi.Message;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.config.MinInteractionThresholds;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.database.repository.model.MaterialTotal;
import me.fulcanelly.trust.me.bro.service.text.TelegramWarningMessageBuilder;

public final class DirectTelegramNotificationService {

    private final AppContext context;
    private final TelegramWarningMessageBuilder messageBuilder;
    private final Map<PairKey, BukkitTask> pendingTasks = new HashMap<>();

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

    /** Reset the quiet-period timer for this owner/interactor pair. */
    public synchronized void schedule(String owner, String interactor) {
        if (!context.getPlugin().getConfig().getBoolean("telegram.owner-dm-notify", true)) {
            return;
        }

        PairKey key = new PairKey(owner, interactor);
        BukkitTask previous = pendingTasks.remove(key);
        if (previous != null) {
            previous.cancel();
        }

        long debounceSeconds = Math.max(
                1,
                context.getPlugin().getConfig().getLong("detection.debounce-time-sec", 60));
        long delayTicks = Math.max(1L, (debounceSeconds * 1000L + 49L) / 50L);

        AtomicReference<BukkitTask> taskRef = new AtomicReference<>();
        BukkitTask task = context.getPlugin().getServer().getScheduler().runTaskLaterAsynchronously(
                context.getPlugin(),
                () -> {
                    synchronized (this) {
                        pendingTasks.remove(key, taskRef.get());
                    }
                    notifyPair(owner, interactor);
                },
                delayTicks);
        taskRef.set(task);
        pendingTasks.put(key, task);
    }

    private void notifyPair(String owner, String interactor) {
        var plugin = context.getPlugin();
        if (!plugin.getConfig().getBoolean("telegram.owner-dm-notify", true)) {
            return;
        }

        try {
            var repositories = context.getRepositories();
            if (repositories.getTrust().isTrusted(owner, interactor)
                    || repositories.getReports().exists(interactor, owner)
                    || !repositories.getTelegramNotificationPreferences().isEnabled(owner)) {
                return;
            }

            var telegramUserId = context.getBridge().getReception().getTgByUser(owner);
            if (telegramUserId.isEmpty()) {
                return;
            }

            long debounceMillis = Math.max(
                    1,
                    plugin.getConfig().getLong("detection.debounce-time-sec", 60)) * 1000L;
            long cutoff = System.currentTimeMillis() - debounceMillis;

            var deliveries = repositories.getDirectTelegramNotificationDeliveries();
            List<InteractionCount> pending = repositories.getPendingInteractions()
                    .findUndeliveredForOwnerAndInteractor(owner, interactor, cutoff);
            if (pending.isEmpty()) {
                return;
            }

            MinInteractionThresholds thresholds = MinInteractionThresholds.from(plugin.getConfig());
            long placed = pending.stream().mapToLong(InteractionCount::getCountPlacedBlocks).sum();
            long broken = pending.stream().mapToLong(InteractionCount::getCountBreakBlocks).sum();
            long containers = pending.stream().mapToLong(InteractionCount::getCountInteractContainers).sum();
            if (!thresholds.meets(placed, broken, containers)) {
                return;
            }

            List<Long> ids = pending.stream().map(InteractionCount::getId).toList();
            List<MaterialTotal> materials = repositories.getInteractionMaterials().sumByInteractionCountIds(ids);
            int mergeDistance = Math.max(
                    0,
                    plugin.getConfig().getInt("detection.split-by-regions.merge-distance", 500));

            String text = messageBuilder.build(interactor, pending, pending.size(), materials, mergeDistance);
            Message message = context.getBridge().getBot().sendMessage(
                    telegramUserId.get(), text, messageBuilder.buildKeyboard(interactor));

            deliveries.markDelivered(owner, interactor);
            context.getLogger().info("Sent direct Telegram notification to " + owner
                    + " about " + interactor + " (message " + message.getMsgId() + ")");
        } catch (Exception e) {
            context.getLogger().warning("Direct Telegram notification to " + owner
                    + " about " + interactor + " failed: " + e.getMessage());
        }
    }

    private record PairKey(String owner, String interactor) {
    }
}
