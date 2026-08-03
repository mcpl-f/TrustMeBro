package me.fulcanelly.trust.me.bro.listener.minecraft;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@RequiredArgsConstructor
public final class PlayerJoinNotificationListener implements Listener {

    private final AppContext context;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!context.getPlugin().getConfig().getBoolean("minecraft.notify-on-join", true)) {
            return;
        }
        context.getServices().getOwnerNotifications().scheduleNotify(event.getPlayer());
    }
}
