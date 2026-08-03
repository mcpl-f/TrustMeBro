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
 * /treport &lt;player&gt; — same as Telegram Report: record a complaint about
 * that player.
 */
@RequiredArgsConstructor
public final class TReportCommand implements CommandExecutor {

    private final AppContext context;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var messages = context.getMessages();

        if (!(sender instanceof Player player)) {
            reply(sender, ChatColor.GRAY, messages.format("command.report.players-only"));
            return true;
        }

        if (args.length != 1) {
            return false;
        }

        String ownerPlayer = player.getName();
        String interactorPlayer = args[0];

        if (ownerPlayer.equals(interactorPlayer)) {
            reply(player, ChatColor.GRAY, messages.format("command.report.self"));
            return true;
        }

        if (!MinecraftPlayers.isKnownPlayer(player.getServer(), interactorPlayer)) {
            reply(player, ChatColor.GRAY, messages.format(
                    "command.report.unknown-player",
                    "player", interactorPlayer));
            return true;
        }

        Long telegramUserId = context.getBridge().getReception().getTgByUser(ownerPlayer).orElse(null);

        try {
            Outcome outcome = context.getServices().getTrustDecisions()
                    .report(ownerPlayer, interactorPlayer, telegramUserId);

            if (outcome == Outcome.ALREADY_REPORTED) {
                reply(player, ChatColor.GRAY, messages.format(
                        "callback.already-reported",
                        "owner", ownerPlayer,
                        "interactor", interactorPlayer));
                context.getServices().getOwnerNotifications().notifyNow(player);
                return true;
            }

            reply(player, ChatColor.GOLD, messages.format(
                    "callback.reported",
                    "owner", ownerPlayer,
                    "interactor", interactorPlayer));
            context.getServices().getOwnerNotifications().notifyNow(player);
        } catch (Exception e) {
            context.getLogger().warning("treport failed: " + e.getMessage());
            reply(player, ChatColor.GRAY, messages.format("callback.db-error"));
        }
        return true;
    }

    private void reply(CommandSender sender, ChatColor color, String text) {
        sender.sendMessage(color + text);
    }
}
