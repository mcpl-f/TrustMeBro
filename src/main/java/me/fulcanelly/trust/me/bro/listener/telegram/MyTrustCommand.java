package me.fulcanelly.trust.me.bro.listener.telegram;

import java.util.Optional;
import java.util.regex.Pattern;

import com.google.common.eventbus.Subscribe;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.tapi.events.CommandEvent;
import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;

@RequiredArgsConstructor
public final class MyTrustCommand {

    private static final Pattern COMMAND = Pattern.compile("^/mctrust(@\\S+)?(\\s.*)?$");

    private final SignupLoginReception reception;

    @Subscribe
    public void onCommand(CommandEvent event) {
        String text = event.getMessage().getText();

        if (text == null || !COMMAND.matcher(text).matches()) {
            return;
        }

        long telegramUserId = event.getMessage().getFrom().getId();
        Optional<String> linkedPlayer = reception.getPlayerByTg(telegramUserId);

        if (linkedPlayer.isEmpty()) {
            event.getMessage().reply("Telegram account is not linked to Minecraft.");
            return;
        }

        event.getMessage().reply("Linked Minecraft player: " + linkedPlayer.get());
    }
}
