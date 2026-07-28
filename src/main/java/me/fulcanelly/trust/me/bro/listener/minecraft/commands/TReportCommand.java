package me.fulcanelly.trust.me.bro.listener.minecraft.commands;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
import me.fulcanelly.trust.me.bro.service.LocalizationService;
import me.fulcanelly.trust.me.bro.service.MinecraftOwnerNotificationService;
import me.fulcanelly.trust.me.bro.service.TrustDecisionService;
import me.fulcanelly.trust.me.bro.service.TrustDecisionService.Outcome;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * /treport &lt;player&gt; — same as Telegram Report: record a complaint about
 * that player.
 */
@RequiredArgsConstructor
public final class TReportCommand implements CommandExecutor {

    private final TrustDecisionService decisions;
    private final SignupLoginReception reception;
    private final LocalizationService messages;
    private final MinecraftOwnerNotificationService ownerNotifications;
    private final Logger logger;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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

        Long telegramUserId = reception.getTgByUser(ownerPlayer).orElse(null);

        try {
            Outcome outcome = decisions.report(ownerPlayer, interactorPlayer, telegramUserId);

            if (outcome == Outcome.ALREADY_REPORTED) {
                reply(player, ChatColor.GRAY, messages.format(
                        "callback.already-reported",
                        "owner", ownerPlayer,
                        "interactor", interactorPlayer));
                ownerNotifications.fetchAndNotifyAsync(player);
                return true;
            }

            reply(player, ChatColor.GOLD, messages.format(
                    "callback.reported",
                    "owner", ownerPlayer,
                    "interactor", interactorPlayer));
            ownerNotifications.fetchAndNotifyAsync(player);
        } catch (Exception e) {
            logger.warning("treport failed: " + e.getMessage());
            reply(player, ChatColor.GRAY, messages.format("callback.db-error"));
        }
        return true;
    }

    private void reply(CommandSender sender, ChatColor color, String text) {
        sender.sendMessage(color + text);
    }
}
