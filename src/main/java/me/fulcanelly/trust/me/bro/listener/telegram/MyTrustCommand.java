package me.fulcanelly.trust.me.bro.listener.telegram;

import java.util.Optional;
import java.util.regex.Pattern;

import com.google.common.eventbus.Subscribe;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.tapi.events.CommandEvent;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;

@RequiredArgsConstructor
public final class MyTrustCommand {

    private static final Pattern COMMAND = Pattern.compile("^/mctrust(@\\S+)?(\\s.*)?$");

    private final AppContext context;

    @Subscribe
    public void onCommand(CommandEvent event) {
        String text = event.getMessage().getText();

        if (text == null || !COMMAND.matcher(text).matches()) {
            return;
        }

        long telegramUserId = event.getMessage().getFrom().getId();
        Optional<String> linkedPlayer = context.getBridge().getReception().getPlayerByTg(telegramUserId);

        if (linkedPlayer.isEmpty()) {
            event.getMessage()
                    .reply(
                            "Telegram account is not linked to Minecraft. \n" +
                                    "Link account or specify player name to see his trust statuses.\n\n" +
                                    "Usage: /mctrust player_name");
            return;
        }

        event.getMessage().reply("Linked Minecraft player: " + linkedPlayer.get());
    }
}
