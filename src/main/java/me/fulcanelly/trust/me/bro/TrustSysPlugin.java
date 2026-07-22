package me.fulcanelly.trust.me.bro;

import java.util.Optional;

import org.bukkit.plugin.java.JavaPlugin;

import me.fulcanelly.trust.me.bro.bootstrap.TrustSysBootstrap;
import me.fulcanelly.trust.me.bro.bootstrap.TrustSysRuntime;

public final class TrustSysPlugin extends JavaPlugin {

    private TrustSysRuntime runtime;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            Optional<TrustSysRuntime> started = new TrustSysBootstrap(this).start();
            if (started.isEmpty()) {
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            runtime = started.get();
        } catch (Exception e) {
            getLogger().warning("TrustMeBro failed to start: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.stop();
            runtime = null;
        }
    }
}
