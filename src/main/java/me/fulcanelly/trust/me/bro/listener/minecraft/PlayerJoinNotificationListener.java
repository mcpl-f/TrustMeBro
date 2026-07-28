package me.fulcanelly.trust.me.bro.listener.minecraft;

import lombok.RequiredArgsConstructor;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import me.fulcanelly.trust.me.bro.service.MinecraftOwnerNotificationService;

@RequiredArgsConstructor
public final class PlayerJoinNotificationListener implements Listener {

    private final Plugin plugin;
    private final MinecraftOwnerNotificationService ownerNotifications;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("minecraft.notify-on-join", true)) {
            return;
        }
        ownerNotifications.scheduleNotify(event.getPlayer());
    }
}
