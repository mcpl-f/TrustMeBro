package me.fulcanelly.trust.me.bro.listener.telegram;

import java.sql.SQLException;
import java.util.Optional;

import com.google.common.eventbus.Subscribe;

import me.fulcanelly.tgbridge.tapi.events.CallbackQueryEvent;
import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.service.core.TrustDecisionService.Outcome;
import me.fulcanelly.trust.me.bro.service.util.TrustCallbackPayloadService;
import me.fulcanelly.trust.me.bro.service.util.TrustCallbackPayloadService.Payload;

@RequiredArgsConstructor
public final class TrustCallbackHandler {

    private final AppContext context;

    @Subscribe
    public void onCallback(CallbackQueryEvent event) {
        var callbackPayloads = context.getCallbackPayloads();
        var messages = context.getMessages();
        String data = event.getData();
        if (!callbackPayloads.isTrustCallback(data)) {
            return;
        }

        Optional<Payload> payload = callbackPayloads.decode(data);
        if (payload.isEmpty()) {
            event.answer(messages.format("callback.bad-action"));
            return;
        }

        String action = payload.get().getAction();
        String interactorPlayer = payload.get().getInteractorPlayer();
        if (!TrustCallbackPayloadService.ACTION_TRUST.equals(action)
                && !TrustCallbackPayloadService.ACTION_REPORT.equals(action)) {
            event.answer(messages.format("callback.unknown-action"));
            return;
        }

        long telegramUserId = event.getFrom().getId();

        Optional<String> linkedPlayer = context.getBridge().getReception().getPlayerByTg(telegramUserId);
        if (linkedPlayer.isEmpty()) {
            event.answer(messages.format("callback.link-required"));
            return;
        }
        String ownerPlayer = linkedPlayer.get();

        try {
            if (!context.getRepositories().getInteractionCounts().exists(interactorPlayer, ownerPlayer)) {
                event.answer(messages.format("callback.interaction-gone"));
                return;
            }

            var decisions = context.getServices().getTrustDecisions();

            if (TrustCallbackPayloadService.ACTION_TRUST.equals(action)) {
                Outcome outcome = decisions.trust(ownerPlayer, interactorPlayer, telegramUserId);
                if (outcome == Outcome.ALREADY_TRUSTED) {
                    event.answer(messages.format(
                            "callback.already-trusted",
                            "owner", ownerPlayer,
                            "interactor", interactorPlayer));
                    return;
                }
                event.answer(messages.format(
                        "callback.trusted",
                        "owner", ownerPlayer,
                        "interactor", interactorPlayer));
                return;
            }

            Outcome outcome = decisions.report(ownerPlayer, interactorPlayer, telegramUserId);
            if (outcome == Outcome.ALREADY_REPORTED) {
                event.answer(messages.format(
                        "callback.already-reported",
                        "owner", ownerPlayer,
                        "interactor", interactorPlayer));
                return;
            }
            event.answer(messages.format(
                    "callback.reported",
                    "owner", ownerPlayer,
                    "interactor", interactorPlayer));
        } catch (SQLException e) {
            context.getLogger().warning("TrustMeBro callback failed: " + e.getMessage());
            event.answer(messages.format("callback.db-error"));
        }
    }
}
