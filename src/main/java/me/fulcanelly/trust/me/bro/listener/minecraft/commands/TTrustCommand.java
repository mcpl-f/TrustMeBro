package me.fulcanelly.trust.me.bro.listener.minecraft.commands;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.service.core.TrustDecisionService.Outcome;
import me.fulcanelly.trust.me.bro.service.util.MinecraftPlayers;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /ttrust &lt;player&gt; — same as Telegram Trust: you trust that player with
 * your stuff.
 */
@RequiredArgsConstructor
public final class TTrustCommand implements CommandExecutor {

    private final AppContext context;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var messages = context.getMessages();

        if (!(sender instanceof Player player)) {
            reply(sender, ChatColor.GRAY, messages.format("command.trust.players-only"));
            return true;
        }
        if (args.length != 1) {
            return false;
        }

        String ownerPlayer = player.getName();
        String interactorPlayer = args[0];

        if (ownerPlayer.equals(interactorPlayer)) {
            reply(player, ChatColor.GRAY, messages.format("command.trust.self"));
            return true;
        }

        if (!MinecraftPlayers.isKnownPlayer(player.getServer(), interactorPlayer)) {
            reply(player, ChatColor.GRAY, messages.format(
                    "command.trust.unknown-player",
                    "player", interactorPlayer));
            return true;
        }

        Long telegramUserId = context.getBridge().getReception().getTgByUser(ownerPlayer).orElse(null);

        try {
            Outcome outcome = context.getServices().getTrustDecisions()
                    .trust(ownerPlayer, interactorPlayer, telegramUserId);

            if (outcome == Outcome.ALREADY_TRUSTED) {
                reply(player, ChatColor.GRAY, messages.format(
                        "callback.already-trusted",
                        "owner", ownerPlayer,
                        "interactor", interactorPlayer));
                context.getServices().getOwnerNotifications().notifyNow(player);
                return true;
            }

            reply(player, ChatColor.GREEN, messages.format(
                    "callback.trusted",
                    "owner", ownerPlayer,
                    "interactor", interactorPlayer));
            context.getServices().getOwnerNotifications().notifyNow(player);
        } catch (Exception e) {
            context.getLogger().warning("ttrust failed: " + e.getMessage());
            reply(player, ChatColor.GRAY, messages.format("callback.db-error"));
        }
        return true;
    }

    private void reply(CommandSender sender, ChatColor color, String text) {
        sender.sendMessage(color + text);
    }
}
