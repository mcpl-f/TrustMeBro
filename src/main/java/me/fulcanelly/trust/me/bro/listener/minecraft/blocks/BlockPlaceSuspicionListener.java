package me.fulcanelly.trust.me.bro.listener.minecraft.blocks;

import lombok.RequiredArgsConstructor;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;

@RequiredArgsConstructor
public final class BlockPlaceSuspicionListener implements Listener {

    private final AppContext context;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        var player = event.getPlayer();
        var location = event.getBlock().getLocation();
        var material = event.getBlock().getType();
        var plugin = context.getPlugin();

        Runnable task = () -> context.getServices().getSuspicionDetection()
                .recordBlockAction(player, location, SuspiciousActionType.PLACE_BLOCK, material);

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(plugin, task);
    }
}
