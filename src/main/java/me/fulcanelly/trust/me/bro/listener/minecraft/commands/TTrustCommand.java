package me.fulcanelly.trust.me.bro.listener.minecraft.commands;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
import me.fulcanelly.trust.me.bro.service.core.MinecraftOwnerNotificationService;
import me.fulcanelly.trust.me.bro.service.core.TrustDecisionService;
import me.fulcanelly.trust.me.bro.service.core.TrustDecisionService.Outcome;
import me.fulcanelly.trust.me.bro.service.util.LocalizationService;
import me.fulcanelly.trust.me.bro.service.util.MinecraftPlayers;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * /ttrust &lt;player&gt; — same as Telegram Trust: you trust that player with
 * your stuff.
 */
@RequiredArgsConstructor
public final class TTrustCommand implements CommandExecutor {

    private final TrustDecisionService decisions;
    private final SignupLoginReception reception;
    private final LocalizationService messages;
    private final MinecraftOwnerNotificationService ownerNotifications;
    private final Logger logger;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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

        Long telegramUserId = reception.getTgByUser(ownerPlayer).orElse(null);

        try {
            Outcome outcome = decisions.trust(ownerPlayer, interactorPlayer, telegramUserId);

            if (outcome == Outcome.ALREADY_TRUSTED) {
                reply(player, ChatColor.GRAY, messages.format(
                        "callback.already-trusted",
                        "owner", ownerPlayer,
                        "interactor", interactorPlayer));
                ownerNotifications.notifyNow(player);
                return true;
            }

            reply(player, ChatColor.GREEN, messages.format(
                    "callback.trusted",
                    "owner", ownerPlayer,
                    "interactor", interactorPlayer));
            ownerNotifications.notifyNow(player);
        } catch (Exception e) {
            logger.warning("ttrust failed: " + e.getMessage());
            reply(player, ChatColor.GRAY, messages.format("callback.db-error"));
        }
        return true;
    }

    private void reply(CommandSender sender, ChatColor color, String text) {
        sender.sendMessage(color + text);
    }
}
