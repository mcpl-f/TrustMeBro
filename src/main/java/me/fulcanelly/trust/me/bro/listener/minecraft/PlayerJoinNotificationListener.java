package me.fulcanelly.trust.me.bro.listener.minecraft;

import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.database.repository.local.InteractionCountsRepository;
import me.fulcanelly.trust.me.bro.service.text.MinecraftWarningMessageBuilder;

@RequiredArgsConstructor
public final class PlayerJoinNotificationListener implements Listener {

    private final Plugin plugin;
    private final InteractionCountsRepository interactionCounts;
    private final MinecraftWarningMessageBuilder messageBuilder;
    private final Logger logger;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<InteractionCount> pending = interactionCounts.findPendingForOwner(player.getName());
                if (pending.isEmpty()) {
                    return;
                }
                String message = messageBuilder.build(player.getName(), pending);
                plugin.getServer().getScheduler().runTask(
                        plugin,
                        () -> player.sendMessage(ChatColor.GOLD + message));
            } catch (SQLException e) {
                logger.warning("Trust join notification failed: " + e.getMessage());
            }
        });
        
    }
}
