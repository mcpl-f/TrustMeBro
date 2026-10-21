package me.fulcanelly.trust.me.bro.listener.telegram;

import static me.fulcanelly.trust.me.bro.service.util.TelegramMarkdown.escape;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.eventbus.Subscribe;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.tapi.events.CommandEvent;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.service.text.ProfileMessageBuilder;
import me.fulcanelly.trust.me.bro.service.text.ProfileMessageBuilder.Section;
import me.fulcanelly.trust.me.bro.service.util.MinecraftPlayers;

/**
 * /rep [name] — reputation, decisions and pending interactions of the named
 * player, or of the one linked to the sender when no name is given. Buttons are
 * handled by {@link TrustCallbackHandler}.
 */
@RequiredArgsConstructor
public final class MyProfileCommand {

    private static final Pattern COMMAND = Pattern.compile("^/rep(?:@\\S+)?(?:\\s+(\\S+))?(?:\\s.*)?$");

    /**
     * Java names are up to 16 characters, Floodgate adds a leading dot. ASCII only and
     * short: the name travels in the button's callback data, which Telegram caps at 64 bytes.
     */
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_.]{1,37}");

    private final AppContext context;
    private final ProfileMessageBuilder profileMessages;

    @Subscribe
    public void onCommand(CommandEvent event) {
        String text = event.getMessage().getText();
        if (text == null) {
            return;
        }
        Matcher command = COMMAND.matcher(text);
        if (!command.matches()) {
            return;
        }

        long telegramUserId = event.getMessage().getFrom().getId();

        String profilePlayer = command.group(1);
        boolean nameGiven = profilePlayer != null;
        if (!nameGiven) {
            Optional<String> linkedPlayer = context.getBridge()
                    .getReception()
                    .getPlayerByTg(telegramUserId);

            if (linkedPlayer.isEmpty()) {
                event.getMessage().reply(context.getMessages().format("telegram.command.myprofile.not-linked"));
                return;
            }
            profilePlayer = linkedPlayer.get();
        } else if (!PLAYER_NAME.matcher(profilePlayer).matches()) {
            event.getMessage().reply(context.getMessages().format("telegram.command.myprofile.bad-name"));
            return;
        }

        try {
            // A linked player exists for sure; a typed name may be a typo.
            if (nameGiven && !hasProfileData(profilePlayer) && !isKnownToServer(profilePlayer)) {
                event.getMessage().reply(context.getMessages().format(
                        "telegram.command.myprofile.unknown-player",
                        "player", escape(profilePlayer)));
                return;
            }

            // Plain send (not reply) so the buttons below can edit this message later.
            context.getBridge().getBot().sendMessage(
                    event.getMessage().getChat().getId(),
                    profileMessages.buildText(profilePlayer, Section.MAIN),
                    profileMessages.buildKeyboard(
                            profilePlayer, Section.MAIN, ProfileMessageBuilder.FIRST_PAGE, telegramUserId));
        } catch (SQLException e) {
            context.getLogger().warning("/rep failed: " + e.getMessage());
            event.getMessage().reply(context.getMessages().format("telegram.command.myprofile.failed"));
        }
    }

    /** Whether the profile would show anything but zeros. */
    private boolean hasProfileData(String player) throws SQLException {
        var trust = context.getRepositories().getTrust();
        var reports = context.getRepositories().getReports();
        var interactionCounts = context.getRepositories().getInteractionCounts();

        return trust.countTrusters(player) > 0
                || trust.countTrusted(player) > 0
                || reports.countReporters(player) > 0
                || reports.countReportedByOwner(player) > 0
                || interactionCounts.countUndecidedInteractors(player) > 0;
    }

    /** Asked only when the database has nothing: it may block on the server's name cache. */
    private boolean isKnownToServer(String player) {
        return MinecraftPlayers.isKnownPlayer(context.getPlugin().getServer(), player);
    }
}
