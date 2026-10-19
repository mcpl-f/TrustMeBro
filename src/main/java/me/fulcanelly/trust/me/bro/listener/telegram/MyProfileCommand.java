package me.fulcanelly.trust.me.bro.listener.telegram;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.common.eventbus.Subscribe;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.tapi.events.CommandEvent;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.service.text.ProfileMessageBuilder;
import me.fulcanelly.trust.me.bro.service.text.ProfileMessageBuilder.Section;

/**
 * /myprofile — the linked player's reputation, own decisions and pending
 * interactions, with buttons handled by {@link TrustCallbackHandler}.
 */
@RequiredArgsConstructor
public final class MyProfileCommand {

    private static final Pattern COMMAND = Pattern.compile("^/myprofile(@\\S+)?(\\s.*)?$");

    private final AppContext context;
    private final ProfileMessageBuilder profileMessages;

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

        try {
            // Plain send (not reply) so the buttons below can edit this message later.
            context.getBridge().getBot().sendMessage(
                    event.getMessage().getChat().getId(),
                    profileMessages.buildText(linkedPlayer.get(), Section.MAIN),
                    profileMessages.buildKeyboard(
                            linkedPlayer.get(), Section.MAIN, ProfileMessageBuilder.FIRST_PAGE, telegramUserId));
        } catch (SQLException e) {
            context.getLogger().warning("myprofile failed: " + e.getMessage());
            event.getMessage().reply(context.getMessages().format("telegram.command.myprofile.failed"));
        }
    }
}
