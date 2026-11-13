package me.fulcanelly.trust.me.bro.listener.minecraft.commands;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class TelegramNotificationsCommand implements CommandExecutor {

    private final AppContext context;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(context.getMessages().format("command.tgnotify.players-only"));
            return true;
        }
        if (args.length != 1) {
            return false;
        }

        String option = args[0].toLowerCase(java.util.Locale.ROOT);
        if (!option.equals("on") && !option.equals("off") && !option.equals("status")) {
            return false;
        }

        try {
            var preferences = context.getRepositories().getTelegramNotificationPreferences();
            if (option.equals("status")) {
                boolean enabled = preferences.isEnabled(player.getName());
                player.sendMessage((enabled ? ChatColor.GREEN : ChatColor.RED)
                        + context.getMessages().format(
                                enabled ? "command.tgnotify.enabled" : "command.tgnotify.disabled"));
                return true;
            }

            boolean enabled = option.equals("on");
            preferences.setEnabled(player.getName(), enabled);
            player.sendMessage((enabled ? ChatColor.GREEN : ChatColor.YELLOW)
                    + context.getMessages().format(
                            enabled ? "command.tgnotify.enabled" : "command.tgnotify.disabled"));
        } catch (Exception e) {
            context.getLogger().warning("tgnotify failed: " + e.getMessage());
            player.sendMessage(ChatColor.RED + context.getMessages().format("command.tgnotify.failed"));
        }
        return true;
    }
}
