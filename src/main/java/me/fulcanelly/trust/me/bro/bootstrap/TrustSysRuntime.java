package me.fulcanelly.trust.me.bro.bootstrap;

import java.sql.SQLException;

import com.google.common.eventbus.EventBus;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.LocalDatabase;
import me.fulcanelly.trust.me.bro.listener.telegram.TrustCallbackHandler;

import org.bukkit.plugin.java.JavaPlugin;

@RequiredArgsConstructor
public final class TrustSysRuntime {

    private final JavaPlugin plugin;
    private final LocalDatabase database;
    private final EventBus eventBus;
    private final TrustCallbackHandler trustCallbackHandler;
    private final int notificationTaskId;

    public void stop() {
        if (notificationTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(notificationTaskId);
        }
        eventBus.unregister(trustCallbackHandler);
        try {
            database.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("TrustMeBro database close failed: " + e.getMessage());
        }
    }
}
