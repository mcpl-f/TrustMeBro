package me.fulcanelly.trust.me.bro.bootstrap.bundles;

import com.google.common.eventbus.EventBus;

import lombok.Value;
import me.fulcanelly.tgbridge.Bridge;
import me.fulcanelly.tgbridge.tapi.TGBot;
import me.fulcanelly.tgbridge.tools.MainConfig;
import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reads the tg-bridge-owned services from the bridge Guice injector.
 *
 * TrustMeBro should not know how tg-bridge constructs these objects.
 * This DTO keeps that boundary in one place and gives the bootstrap code
 * named access to bot, config, account linking, and EventBus.
 */
@Value
public class BridgeServices {

    private static final String TG_BRIDGE_PLUGIN_NAME = "tg-bridge";

    TGBot bot;
    MainConfig mainConfig;
    SignupLoginReception reception;
    EventBus eventBus;

    public static BridgeServices buildFromPlugin(JavaPlugin plugin) {
        Bridge bridge = (Bridge) plugin.getServer().getPluginManager().getPlugin(TG_BRIDGE_PLUGIN_NAME);
        return new BridgeServices(
                bridge.getInjector().getInstance(TGBot.class),
                bridge.getInjector().getInstance(MainConfig.class),
                bridge.getInjector().getInstance(SignupLoginReception.class),
                bridge.getInjector().getInstance(EventBus.class));
    }
}
