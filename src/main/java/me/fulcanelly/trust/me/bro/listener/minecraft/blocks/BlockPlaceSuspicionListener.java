package me.fulcanelly.trust.me.bro.listener.minecraft.blocks;

import lombok.RequiredArgsConstructor;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;

import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;
import me.fulcanelly.trust.me.bro.service.SuspicionDetectionService;

@RequiredArgsConstructor
public final class BlockPlaceSuspicionListener implements Listener {

    private final Plugin plugin;
    private final SuspicionDetectionService detectionService;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        var player = event.getPlayer();
        var location = event.getBlock().getLocation();

        Runnable task = () -> detectionService.recordBlockAction(player, location, SuspiciousActionType.PLACE_BLOCK);

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(plugin, task);
    }
}
