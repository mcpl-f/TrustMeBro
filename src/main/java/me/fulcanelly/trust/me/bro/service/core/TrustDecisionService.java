package me.fulcanelly.trust.me.bro.service.core;

import lombok.RequiredArgsConstructor;

import java.sql.SQLException;

import org.bukkit.entity.Player;

import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.service.util.MinecraftPlayers;

/**
 * Shared trust / report writes used by Telegram callbacks and Minecraft commands.
 */
@RequiredArgsConstructor
public final class TrustDecisionService {

    public enum Outcome {
        TRUSTED,
        ALREADY_TRUSTED,
        REPORTED,
        ALREADY_REPORTED
    }

    private final AppContext context;

    public Outcome trust(String ownerPlayer, String interactorPlayer, Long telegramUserId) throws SQLException {
        var trustRepository = context.getRepositories().getTrust();
        var reportRepository = context.getRepositories().getReports();

        if (reportRepository.exists(interactorPlayer, ownerPlayer)) {
            reportRepository.delete(interactorPlayer, ownerPlayer);
        }
        if (trustRepository.isTrusted(ownerPlayer, interactorPlayer)) {
            notifyTarget(ownerPlayer, interactorPlayer, "feedback.trusted");
            return Outcome.ALREADY_TRUSTED;
        }
        trustRepository.trust(ownerPlayer, interactorPlayer, telegramUserId);
        notifyTarget(ownerPlayer, interactorPlayer, "feedback.trusted");
        return Outcome.TRUSTED;
    }

    public Outcome report(String ownerPlayer, String interactorPlayer, Long telegramUserId) throws SQLException {
        var trustRepository = context.getRepositories().getTrust();
        var reportRepository = context.getRepositories().getReports();

        if (trustRepository.isTrusted(ownerPlayer, interactorPlayer)) {
            trustRepository.untrust(ownerPlayer, interactorPlayer);
        }
        if (reportRepository.exists(interactorPlayer, ownerPlayer)) {
            notifyTarget(ownerPlayer, interactorPlayer, "feedback.reported");
            return Outcome.ALREADY_REPORTED;
        }
        reportRepository.report(ownerPlayer, telegramUserId, interactorPlayer, ownerPlayer);
        notifyTarget(ownerPlayer, interactorPlayer, "feedback.reported");
        return Outcome.REPORTED;
    }

    private void notifyTarget(String ownerPlayer, String targetPlayer, String messageKey) {
        context.getPlugin().getServer().getScheduler().runTask(context.getPlugin(), () -> {
            Player target = MinecraftPlayers.getOnlineExact(context.getPlugin().getServer(), targetPlayer);
            if (target != null) {
                target.sendMessage(context.getMessages().format(messageKey, "owner", ownerPlayer));
            }
        });
    }
}
