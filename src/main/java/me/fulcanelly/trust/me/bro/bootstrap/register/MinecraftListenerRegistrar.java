package me.fulcanelly.trust.me.bro.bootstrap.register;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
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
 * This class receives the shared app context and only performs Bukkit
 * listener registration based on config flags.
 */
@RequiredArgsConstructor
public final class MinecraftListenerRegistrar {

    private final AppContext context;

    public void register() {
        var plugin = context.getPlugin();

        if (plugin.getConfig().getBoolean("detection.include-block-break", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new BlockBreakSuspicionListener(context),
                    plugin);
        }
        if (plugin.getConfig().getBoolean("detection.include-block-place", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new BlockPlaceSuspicionListener(context),
                    plugin);
        }
        if (plugin.getConfig().getBoolean("detection.include-container-interaction", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new ContainerInteractionSuspicionListener(context),
                    plugin);
        }
        plugin.getServer().getPluginManager().registerEvents(
                new PlayerJoinNotificationListener(context),
                plugin);

        bindCommand("new_trust_region", new NewTrustRegionCommand(context));
        bindCommand("ttrust", new TTrustCommand(context));
        bindCommand("treport", new TReportCommand(context));
    }

    private void bindCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = context.getPlugin().getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        } else {
            context.getLogger().warning("Command " + name + " missing from plugin.yml");
        }
    }
}
