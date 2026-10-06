package me.fulcanelly.trust.me.bro.listener.telegram;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.common.eventbus.Subscribe;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.tapi.events.CommandEvent;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;

/**
 * /myprofile — the linked player's reputation, own decisions and pending
 * interactions.
 */
@RequiredArgsConstructor
public final class MyProfileCommand {

    private static final Pattern COMMAND = Pattern.compile("^/myprofile(@\\S+)?(\\s.*)?$");

    private final AppContext context;

    @Subscribe
    public void onCommand(CommandEvent event) {
        String text = event.getMessage().getText();
        if (text == null || !COMMAND.matcher(text).matches()) {
            return;
        }

        long telegramUserId = event.getMessage().getFrom().getId();

        Optional<String> linkedPlayer = context.getBridge()
                .getReception()
                .getPlayerByTg(telegramUserId);

        if (linkedPlayer.isEmpty()) {

            event.getMessage().reply(context.getMessages().format("telegram.command.myprofile.not-linked"));
            return;
        }

        String player = linkedPlayer.get();
        var trust = context.getRepositories().getTrust();
        var reports = context.getRepositories().getReports();
        var interactionCounts = context.getRepositories().getInteractionCounts();

        try {
            event.getMessage().reply(
                    context.getMessages().format(
                            "telegram.command.myprofile.text",
                            "trusters", trust.countTrusters(player),
                            "reporters", reports.countReporters(player),
                            "trusted", trust.countTrusted(player),
                            "watched", 0, // TODO: watch relation is not implemented yet
                            "reported", reports.countReportedByOwner(player),
                            "pending", interactionCounts.countUndecidedInteractors(player)));
        } catch (SQLException e) {
            context.getLogger().warning("myprofile failed: " + e.getMessage());
            event.getMessage().reply(context.getMessages().format("telegram.command.myprofile.failed"));
        }
    }
}
