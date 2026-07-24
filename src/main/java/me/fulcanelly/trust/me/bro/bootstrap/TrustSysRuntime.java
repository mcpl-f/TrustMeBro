package me.fulcanelly.trust.me.bro.bootstrap;

import java.sql.SQLException;
import java.util.List;

import com.google.common.eventbus.EventBus;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.LocalDatabase;

import org.bukkit.plugin.java.JavaPlugin;

@RequiredArgsConstructor
public final class TrustSysRuntime {

    private final JavaPlugin plugin;
    private final LocalDatabase database;
    private final EventBus eventBus;
    private final List<Object> eventBusListeners;
    private final int notificationTaskId;

    public void stop() {
        if (notificationTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(notificationTaskId);
        }
        eventBusListeners.forEach(eventBus::unregister);
        try {
            database.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("TrustMeBro database close failed: " + e.getMessage());
        }
    }
}
