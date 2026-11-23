package me.fulcanelly.trust.me.bro.service.core;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;

import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.service.text.MinecraftWarningMessageBuilder;

import net.md_5.bungee.api.chat.BaseComponent;

/**
 * Shows the owner the next pending in-game warning (join + after /ttrust|/treport).
 */
public final class MinecraftOwnerNotificationService {

    private static final int MAX_INTERACTIONS_PER_NOTIFICATION = 2;
    private static final long DELAY_MIN_TICKS = 20L;
    private static final long DELAY_MAX_TICKS = 40L;

    private final AppContext context;
    private final MinecraftWarningMessageBuilder messageBuilder;

    public MinecraftOwnerNotificationService(AppContext context) {
        this.context = context;
        this.messageBuilder = new MinecraftWarningMessageBuilder(
                context.getMessages(),
                context.getCoreProtect(),
                context.getRepositories().getRegions());
    }

    /** Join path: wait 1–2s, then fetch and send on the main thread. */
    public void scheduleNotify(Player player) {
        long delayTicks = ThreadLocalRandom.current().nextLong(DELAY_MIN_TICKS, DELAY_MAX_TICKS + 1);
        context.getPlugin().getServer().getScheduler().runTaskLater(
                context.getPlugin(),
                () -> notifyNow(player),
                delayTicks);
    }

    /** Immediate path: no delay (instant notify / after /ttrust|/treport). */
    public void notifyNow(Player player) {
        context.getPlugin().getServer().getScheduler().runTaskAsynchronously(
                context.getPlugin(),
                () -> fetchAndNotify(player));
    }

    private void fetchAndNotify(Player player) {
        if (!player.isOnline()) {
            return;
        }

        var plugin = context.getPlugin();
        String strategy = plugin.getConfig().getString("minecraft.fetch-interactions-strategy", "recent");
        int mergeDistance = Math.max(
                0,
                plugin.getConfig().getInt("detection.split-by-regions.merge-distance", 500));

        try {
            var pendingRepository = context.getRepositories().getPendingInteractions();
            List<InteractionCount> pending = pendingRepository.findTopPendingInteractionsForOwner(
                    player.getName(), strategy, MAX_INTERACTIONS_PER_NOTIFICATION + 11);
            if (pending.isEmpty()) {
                context.getLogger().info("No pending interaction found for owner: " + player.getName());
                return;
            }

            List<InteractionCount> displayed = pending.subList(0, Math.min(MAX_INTERACTIONS_PER_NOTIFICATION, pending.size()));
            int remainingCount = pending.size() - displayed.size();
            String remainingInteractions = pending.size() == MAX_INTERACTIONS_PER_NOTIFICATION + 11
                    ? "10+"
                    : Integer.toString(remainingCount);
            context.getLogger().info("Found " + pending.size() + " pending interactions for owner: "
                    + player.getName());
            BaseComponent[] message = messageBuilder.build(displayed, remainingInteractions, mergeDistance);
            plugin.getServer().getScheduler().runTask(plugin, () -> sendWarning(player, message));
        } catch (SQLException e) {
            context.getLogger().warning("Trust owner notification failed: " + e.getMessage());
        }
    }

    private void sendWarning(Player player, BaseComponent[] message) {
        if (!player.isOnline()) {
            return;
        }
        player.spigot().sendMessage(message);
    }
}
