package me.fulcanelly.trust.me.bro.bootstrap;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.listener.minecraft.PlayerJoinNotificationListener;
import me.fulcanelly.trust.me.bro.listener.minecraft.blocks.BlockBreakSuspicionListener;
import me.fulcanelly.trust.me.bro.listener.minecraft.blocks.BlockPlaceSuspicionListener;
import me.fulcanelly.trust.me.bro.listener.minecraft.blocks.ContainerInteractionSuspicionListener;
import me.fulcanelly.trust.me.bro.listener.minecraft.commands.NewTrustRegionCommand;
import me.fulcanelly.trust.me.bro.listener.minecraft.commands.TReportCommand;
import me.fulcanelly.trust.me.bro.listener.minecraft.commands.TTrustCommand;

import org.bukkit.command.PluginCommand;

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
                new PlayerJoinNotificationListener(plugin, services.getOwnerNotifications()),
                plugin);

        bindCommand("new_trust_region", new NewTrustRegionCommand(
                repositories.getRegions(),
                context.getCoreProtect(),
                services.getMessages(),
                plugin.getLogger()));
        bindCommand("ttrust", new TTrustCommand(
                services.getTrustDecisions(),
                context.getBridge().getReception(),
                services.getMessages(),
                services.getOwnerNotifications(),
                plugin.getLogger()));
        bindCommand("treport", new TReportCommand(
                services.getTrustDecisions(),
                context.getBridge().getReception(),
                services.getMessages(),
                services.getOwnerNotifications(),
                plugin.getLogger()));
    }

    private void bindCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = context.getPlugin().getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        } else {
            context.getPlugin().getLogger().warning("Command " + name + " missing from plugin.yml");
        }
    }
}
