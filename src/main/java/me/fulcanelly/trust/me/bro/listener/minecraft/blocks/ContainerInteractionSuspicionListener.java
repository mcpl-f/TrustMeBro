package me.fulcanelly.trust.me.bro.listener.minecraft.blocks;

import lombok.RequiredArgsConstructor;

import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import me.fulcanelly.trust.me.bro.service.SuspicionDetectionService;

@RequiredArgsConstructor
public final class ContainerInteractionSuspicionListener implements Listener {

    private final Plugin plugin;
    private final SuspicionDetectionService detectionService;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        /*
         * Count only real player actions in the opened block container.
         * Ignore player inventory clicks, empty no-op clicks, and virtual GUIs.
         */
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Inventory clicked = event.getClickedInventory();
        Inventory top = event.getView().getTopInventory();
        if (clicked == null || !clicked.equals(top)) {
            return;
        }
        if (event.getCurrentItem() == null && event.getCursor() == null) {
            return;
        }
        if (!(top.getHolder() instanceof BlockState)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        BlockState holder = (BlockState) top.getHolder();
        var location = holder.getLocation();

        Runnable task = () -> detectionService.recordContainerAction(player, location);
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(plugin, task);
    }
}
