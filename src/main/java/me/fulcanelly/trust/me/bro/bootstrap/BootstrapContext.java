package me.fulcanelly.trust.me.bro.bootstrap;

import lombok.Value;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Carries the already-built bootstrap graph through registration steps.
 *
 * This keeps registrars small and gives them one consistent constructor shape.
 * It also makes the order explicit: plugin and service bundles are built first,
 * then side effects like listeners and scheduler registration happen later.
 */
@Value
// TODO: might make sernse to raname and use it not only in bootstrap but also pass it to services and listeners
final class BootstrapContext {

    JavaPlugin plugin;
    BridgeServices bridge;
    LocalRepositories repositories;
    PluginServices services;

    static BootstrapContext buildFromPlugin(
            JavaPlugin plugin,
            BridgeServices bridge,
            LocalRepositories repositories,
            PluginServices services) {
        return new BootstrapContext(plugin, bridge, repositories, services);
    }
}
