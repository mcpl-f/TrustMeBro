package me.fulcanelly.trust.me.bro.listener.telegram;

import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Logger;

import com.google.common.eventbus.Subscribe;

import me.fulcanelly.tgbridge.tapi.events.CallbackQueryEvent;
import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
import me.fulcanelly.trust.me.bro.database.repository.local.InteractionCountsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.ReportRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.TrustRepository;
import me.fulcanelly.trust.me.bro.service.TrustCallbackPayloadService;
import me.fulcanelly.trust.me.bro.service.TrustCallbackPayloadService.Payload;

@RequiredArgsConstructor
public final class TrustCallbackHandler {

    private final TrustCallbackPayloadService callbackPayloads;
    private final SignupLoginReception reception;
    private final TrustRepository trustRepository;
    private final ReportRepository reportRepository;
    private final InteractionCountsRepository interactionCounts;
    private final Logger logger;

    @Subscribe
    public void onCallback(CallbackQueryEvent event) {
        String data = event.getData();
        if (!callbackPayloads.isTrustCallback(data)) {
            return;
        }

        Optional<Payload> payload = callbackPayloads.decode(data);
        if (payload.isEmpty()) {
            event.answer("Bad TrustMeBro action");
            return;
        }

        String action = payload.get().getAction();
        String interactorPlayer = payload.get().getInteractorPlayer();
        if (!TrustCallbackPayloadService.ACTION_TRUST.equals(action)
                && !TrustCallbackPayloadService.ACTION_REPORT.equals(action)) {
            event.answer("Unknown TrustMeBro action");
            return;
        }

        long telegramUserId = event.getFrom().getId();

        Optional<String> linkedPlayer = reception.getPlayerByTg(telegramUserId);
        if (linkedPlayer.isEmpty()) {
            event.answer("Link your Telegram account to Minecraft first");
            return;
        }
        String ownerPlayer = linkedPlayer.get();

        try {
            if (reportRepository.exists(interactorPlayer, ownerPlayer)) {
                event.answer(ownerPlayer + " already reported " + interactorPlayer);
                return;
            }

            if (!interactionCounts.exists(interactorPlayer, ownerPlayer)) {
                event.answer("Interaction is gone");
                return;
            }

            if (TrustCallbackPayloadService.ACTION_TRUST.equals(action)) {
                trustRepository.trust(ownerPlayer, interactorPlayer, telegramUserId);
                event.answer(ownerPlayer + " now trusts " + interactorPlayer);
                return;
            }

            if (TrustCallbackPayloadService.ACTION_REPORT.equals(action)) {
                reportRepository.report(ownerPlayer, telegramUserId, interactorPlayer, ownerPlayer);
                event.answer(ownerPlayer + " reported " + interactorPlayer);
            }
        } catch (SQLException e) {
            logger.warning("TrustMeBro callback failed: " + e.getMessage());
            event.answer("TrustMeBro DB error");
        }
    }
}
