package me.fulcanelly.trust.me.bro.service.core;

import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.fulcanelly.trust.me.bro.database.repository.local.InteractionCountsRepository;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.service.text.MinecraftWarningMessageBuilder;

import net.md_5.bungee.api.chat.BaseComponent;

/**
 * Shows the owner the next pending in-game warning (join + after /ttrust|/treport).
 */
@RequiredArgsConstructor
public final class MinecraftOwnerNotificationService {

    private static final long DELAY_MIN_TICKS = 20L;
    private static final long DELAY_MAX_TICKS = 40L;

    private final Plugin plugin;
    private final InteractionCountsRepository interactionCounts;
    private final MinecraftWarningMessageBuilder messageBuilder;
    private final Logger logger;

    /** Join path: wait 1–2s, then fetch and send on the main thread. */
    public void scheduleNotify(Player player) {
        long delayTicks = ThreadLocalRandom.current().nextLong(DELAY_MIN_TICKS, DELAY_MAX_TICKS + 1);
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> notifyNow(player),
                delayTicks);
    }

    /** Immediate path: no delay (instant notify / after /ttrust|/treport). */
    public void notifyNow(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(
                plugin,
                () -> fetchAndNotify(player));
    }

    private void fetchAndNotify(Player player) {
        if (!player.isOnline()) {
            return;
        }

        String strategy = plugin.getConfig().getString("minecraft.fetch-interactions-strategy", "recent");
        int mergeDistance = Math.max(
                0,
                plugin.getConfig().getInt("detection.split-by-regions.merge-distance", 500));

        try {
            Optional<InteractionCount> pending = interactionCounts.findTopPendingInteractionForOwner(
                    player.getName(),
                    strategy);
            if (pending.isEmpty()) {
                logger.info("No pending interaction found for owner: " + player.getName());
                return;
            }

            logger.info("Found pending interaction for owner: " + player.getName());
            BaseComponent[] message = messageBuilder.build(pending.get(), mergeDistance);
            plugin.getServer().getScheduler().runTask(plugin, () -> sendWarning(player, message));
        } catch (SQLException e) {
            logger.warning("Trust owner notification failed: " + e.getMessage());
        }
    }

    private void sendWarning(Player player, BaseComponent[] message) {
        if (!player.isOnline()) {
            return;
        }
        player.spigot().sendMessage(message);
    }
}
