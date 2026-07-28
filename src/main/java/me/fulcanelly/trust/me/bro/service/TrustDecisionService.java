package me.fulcanelly.trust.me.bro.service;

import lombok.RequiredArgsConstructor;

import java.sql.SQLException;

import me.fulcanelly.trust.me.bro.database.repository.local.ReportRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.TrustRepository;

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

    private final TrustRepository trustRepository;
    private final ReportRepository reportRepository;

    public Outcome trust(String ownerPlayer, String interactorPlayer, Long telegramUserId) throws SQLException {
        if (reportRepository.exists(interactorPlayer, ownerPlayer)) {
            reportRepository.delete(interactorPlayer, ownerPlayer);
        }
        if (trustRepository.isTrusted(ownerPlayer, interactorPlayer)) {
            return Outcome.ALREADY_TRUSTED;
        }
        trustRepository.trust(ownerPlayer, interactorPlayer, telegramUserId);
        return Outcome.TRUSTED;
    }

    public Outcome report(String ownerPlayer, String interactorPlayer, Long telegramUserId) throws SQLException {
        if (trustRepository.isTrusted(ownerPlayer, interactorPlayer)) {
            trustRepository.untrust(ownerPlayer, interactorPlayer);
        }
        if (reportRepository.exists(interactorPlayer, ownerPlayer)) {
            return Outcome.ALREADY_REPORTED;
        }
        reportRepository.report(ownerPlayer, telegramUserId, interactorPlayer, ownerPlayer);
        return Outcome.REPORTED;
    }
}
