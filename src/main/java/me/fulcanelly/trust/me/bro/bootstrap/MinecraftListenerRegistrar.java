package me.fulcanelly.trust.me.bro.bootstrap;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.listener.minecraft.BlockBreakSuspicionListener;
import me.fulcanelly.trust.me.bro.listener.minecraft.BlockPlaceSuspicionListener;
import me.fulcanelly.trust.me.bro.listener.minecraft.ContainerInteractionSuspicionListener;
import me.fulcanelly.trust.me.bro.listener.minecraft.PlayerJoinNotificationListener;
import me.fulcanelly.trust.me.bro.service.text.MinecraftWarningMessageBuilder;

/**
 * Registers Bukkit-side listeners for block, container, and join events.
 *
 * The listeners are side effects, so they stay out of DTO/factory classes.
 * This class receives the shared bootstrap context and only performs Bukkit
 * listener registration based on config flags.
 */
@RequiredArgsConstructor
final class MinecraftListenerRegistrar {

    private final BootstrapContext context;

    void register() {
        var plugin = context.getPlugin();
        var repositories = context.getRepositories();
        var services = context.getServices();

        if (plugin.getConfig().getBoolean("detection.include-block-break", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new BlockBreakSuspicionListener(plugin, services.getSuspicionDetection()),
                    plugin);
        }
        if (plugin.getConfig().getBoolean("detection.include-block-place", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new BlockPlaceSuspicionListener(plugin, services.getSuspicionDetection()),
                    plugin);
        }
        if (plugin.getConfig().getBoolean("detection.include-container-interaction", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new ContainerInteractionSuspicionListener(plugin, services.getSuspicionDetection()),
                    plugin);
        }
        plugin.getServer().getPluginManager().registerEvents(
                new PlayerJoinNotificationListener(
                        plugin,
                        repositories.getInteractionCounts(),
                        new MinecraftWarningMessageBuilder(services.getMessages()),
                        plugin.getLogger()),
                plugin);
    }
}
