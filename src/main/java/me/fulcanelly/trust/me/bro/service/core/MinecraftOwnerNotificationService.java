package me.fulcanelly.trust.me.bro.service.core;

import java.sql.SQLException;
import java.util.Optional;
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
            Optional<InteractionCount> pending = context.getRepositories()
                    .getInteractionCounts()
                    .findTopPendingInteractionForOwner(player.getName(), strategy);
            if (pending.isEmpty()) {
                context.getLogger().info("No pending interaction found for owner: " + player.getName());
                return;
            }

            context.getLogger().info("Found pending interaction for owner: " + player.getName());
            BaseComponent[] message = messageBuilder.build(pending.get(), mergeDistance);
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
