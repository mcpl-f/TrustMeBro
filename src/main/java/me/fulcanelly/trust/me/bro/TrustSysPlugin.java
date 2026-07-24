package me.fulcanelly.trust.me.bro;

import java.util.Optional;

import org.bukkit.plugin.java.JavaPlugin;

import lombok.SneakyThrows;
import me.fulcanelly.trust.me.bro.bootstrap.TrustSysBootstrap;
import me.fulcanelly.trust.me.bro.bootstrap.TrustSysRuntime;

public final class TrustSysPlugin extends JavaPlugin {

    private TrustSysRuntime runtime;

    @Override
    @SneakyThrows
    public void onEnable() {
        saveDefaultConfig();
        Optional<TrustSysRuntime> started = new TrustSysBootstrap(this).start();
        if (started.isEmpty()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        runtime = started.get();
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.stop();
            runtime = null;
        }
    }
}
