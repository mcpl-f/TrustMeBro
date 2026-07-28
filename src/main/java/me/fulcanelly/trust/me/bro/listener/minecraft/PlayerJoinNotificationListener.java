package me.fulcanelly.trust.me.bro.listener.minecraft;

import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.database.repository.local.InteractionCountsRepository;
import me.fulcanelly.trust.me.bro.service.text.MinecraftWarningMessageBuilder;
import net.md_5.bungee.api.chat.BaseComponent;

@RequiredArgsConstructor
public final class PlayerJoinNotificationListener implements Listener {

    private final Plugin plugin;
    private final InteractionCountsRepository interactionCounts;
    private final MinecraftWarningMessageBuilder messageBuilder;
    private final Logger logger;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("minecraft.notify-on-join", true)) {
            return;
        }

        var player = event.getPlayer();

        String strategy = plugin.getConfig().getString("minecraft.fetch-interactions-strategy", "recent");

        int mergeDistance = Math.max(
                0,
                plugin.getConfig().getInt("detection.split-by-regions.merge-distance", 500));

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> fetchAndNotify(player, strategy, mergeDistance));
    }

    private void fetchAndNotify(Player player, String strategy, int mergeDistance) {
        try {
            Optional<InteractionCount> pending = interactionCounts.findTopPendingInteractionForOwner(
                    player.getName(),
                    strategy);

            if (pending.isEmpty()) {
                return;
            }

            BaseComponent[] message = messageBuilder.build(pending.get(), mergeDistance);

            plugin.getServer().getScheduler().runTask(plugin, () -> player.spigot().sendMessage(message));
        } catch (SQLException e) {
            logger.warning("Trust join notification failed: " + e.getMessage());
        }
    }


}
