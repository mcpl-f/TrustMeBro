package me.fulcanelly.trust.me.bro.listener.telegram;

import java.sql.SQLException;
import java.util.Optional;

import com.google.common.eventbus.Subscribe;

import me.fulcanelly.tgbridge.tapi.events.CallbackQueryEvent;
import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.service.core.TrustDecisionService.Outcome;
import me.fulcanelly.trust.me.bro.service.text.ProfileMessageBuilder;
import me.fulcanelly.trust.me.bro.service.text.ProfileMessageBuilder.Section;
import me.fulcanelly.trust.me.bro.service.util.TrustCallbackPayloadService;
import me.fulcanelly.trust.me.bro.service.util.TrustCallbackPayloadService.Payload;

@RequiredArgsConstructor
public final class TrustCallbackHandler {

    private final AppContext context;
    private final ProfileMessageBuilder profileMessages;

    @Subscribe
    public void onCallback(CallbackQueryEvent event) {
        Optional<Payload> payload = validateCallbackData(event);
        if (payload.isEmpty()) {
            return;
        }

        var callbackPayloads = context.getCallbackPayloads();
        String action = payload.get().getAction();

        if (callbackPayloads.isProfileAction(action)) {
            onProfileAction(event, payload.get());
        } else if (callbackPayloads.isDecisionAction(action)) {
            onDecisionAction(event, payload.get());
        } else {
            event.answer(context.getMessages().format("callback.unknown-action"));
        }
    }

    /**
     * @return the decoded payload; empty when the callback is not ours (ignored) or
     *         malformed (already answered with an error)
     */
    private Optional<Payload> validateCallbackData(CallbackQueryEvent event) {
        var callbackPayloads = context.getCallbackPayloads();
        String data = event.getData();

        if (!callbackPayloads.isTrustCallback(data)) {
            return Optional.empty();
        }

        Optional<Payload> payload = callbackPayloads.decode(data);

        if (payload.isEmpty()) {
            event.answer(context.getMessages().format("callback.bad-action"));
        }

        return payload;
    }

    /**
     * Trust / report buttons of a warning: the owner is the linked account of
     * whoever clicked.
     */
    private void onDecisionAction(CallbackQueryEvent event, Payload payload) {
        var messages = context.getMessages();

        Optional<String> interactor = payload.getInteractorPlayer();
        if (interactor.isEmpty()) {
            event.answer(messages.format("callback.bad-action"));
            return;
        }
        String interactorPlayer = interactor.get();

        long telegramUserId = event.getFrom().getId();

        Optional<String> linkedPlayer = context.getBridge().getReception().getPlayerByTg(telegramUserId);
        if (linkedPlayer.isEmpty()) {
            event.answer(messages.format("callback.link-required"));
            return;
        }
        String ownerPlayer = linkedPlayer.get();

        try { // TODO move to top level (onCallback)
            if (!context.getRepositories().getInteractionCounts().exists(interactorPlayer, ownerPlayer)) {
                event.answer(messages.format("callback.interaction-gone"));
                return;
            }

            var decisions = context.getServices().getTrustDecisions();

            if (TrustCallbackPayloadService.ACTION_TRUST.equals(payload.getAction())) {
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

    /**
     * Profile buttons re-render the /myprofile message in place: a section button
     * adds
     * its header line, back restores the plain profile. Only the author of the
     * command
     * may press them.
     */
    private void onProfileAction(CallbackQueryEvent event, Payload payload) {
        var messages = context.getMessages();
        long telegramUserId = event.getFrom().getId();

        Optional<Long> allowedUser = payload.getAllowedTelegramUserId();
        if (allowedUser.isEmpty() || allowedUser.get() != telegramUserId) {
            event.answer(messages.format("callback.not-your-profile"));
            return;
        }

        Optional<String> linkedPlayer = context.getBridge().getReception().getPlayerByTg(telegramUserId);
        if (linkedPlayer.isEmpty()) {
            event.answer(messages.format("callback.link-required"));
            return;
        }

        Section section = Section.MAIN;
        if (TrustCallbackPayloadService.ACTION_PROFILE_REPUTATION.equals(payload.getAction())) {
            section = Section.REPUTATION;
        } else if (TrustCallbackPayloadService.ACTION_PROFILE_RATINGS.equals(payload.getAction())) {
            section = Section.RATINGS;
        }

        try { // TODO: move to top level (onCallback)
            context.getBridge().getBot().editMessage(
                    event.getMessage().getChat().getId(),
                    event.getMessage().getMsgId(),
                    profileMessages.buildText(linkedPlayer.get(), section),
                    profileMessages.buildKeyboard(section, telegramUserId));
            event.answer("");
        } catch (SQLException e) {
            context.getLogger().warning("TrustMeBro profile callback failed: " + e.getMessage());
            event.answer(messages.format("callback.db-error"));
        }
    }
}
