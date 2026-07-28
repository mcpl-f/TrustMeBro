package me.fulcanelly.trust.me.bro.listener.minecraft.commands;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
import me.fulcanelly.trust.me.bro.service.LocalizationService;
import me.fulcanelly.trust.me.bro.service.TrustDecisionService;
import me.fulcanelly.trust.me.bro.service.TrustDecisionService.Outcome;

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
    private final Logger logger;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.trust.players-only"));
            return true;
        }
        if (args.length != 1) {
            return false;
        }

        String ownerPlayer = player.getName();
        String interactorPlayer = args[0];

        if (ownerPlayer.equals(interactorPlayer)) {
            player.sendMessage(messages.format("command.trust.self"));
            return true;
        }

        Long telegramUserId = reception.getTgByUser(ownerPlayer).orElse(null);

        try {
            Outcome outcome = decisions.trust(ownerPlayer, interactorPlayer, telegramUserId);

            if (outcome == Outcome.ALREADY_TRUSTED) {
                player.sendMessage(messages.format(
                        "callback.already-trusted",
                        "owner", ownerPlayer,
                        "interactor", interactorPlayer));
                return true;
            }

            player.sendMessage(messages.format(
                    "callback.trusted",
                    "owner", ownerPlayer,
                    "interactor", interactorPlayer));
        } catch (Exception e) {
            logger.warning("ttrust failed: " + e.getMessage());
            player.sendMessage(messages.format("callback.db-error"));
        }
        return true;
    }
}
