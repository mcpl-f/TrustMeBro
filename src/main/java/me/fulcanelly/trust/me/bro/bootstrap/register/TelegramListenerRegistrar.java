package me.fulcanelly.trust.me.bro.bootstrap.register;

import java.util.List;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.listener.telegram.callback.TrustCallbackHandler;
import me.fulcanelly.trust.me.bro.listener.telegram.command.MyProfileCommand;
import me.fulcanelly.trust.me.bro.listener.telegram.command.MyTrustCommand;
import me.fulcanelly.trust.me.bro.listener.telegram.command.TrustStatsCommand;
import me.fulcanelly.trust.me.bro.service.text.ProfileMessageBuilder;

/**
 * Registers Telegram/EventBus listeners owned by TrustMeBro.
 *
 * tg-bridge already posts callback and command events into EventBus.
 * This registrar wires TrustMeBro handlers into that bus and returns them
 * so runtime shutdown can unregister the same objects cleanly.
 */
@RequiredArgsConstructor
public final class TelegramListenerRegistrar {

    private final AppContext context;

    public List<Object> register() {
        var eventBus = context.getBridge().getEventBus();

        // Shared: the command sends the profile, the callback handler edits it.
        ProfileMessageBuilder profileMessages = new ProfileMessageBuilder(
                context.getMessages(),
                context.getCallbackPayloads(),
                context.getRepositories());

        TrustCallbackHandler callbackHandler = new TrustCallbackHandler(context, profileMessages);
        MyTrustCommand myTrustCommand = new MyTrustCommand(context);
        TrustStatsCommand trustStatsCommand = new TrustStatsCommand(context);
        MyProfileCommand myProfileCommand = new MyProfileCommand(context, profileMessages);

        eventBus.register(callbackHandler);
        eventBus.register(myTrustCommand);
        eventBus.register(trustStatsCommand);
        eventBus.register(myProfileCommand);

        return List.of(callbackHandler, myTrustCommand, trustStatsCommand, myProfileCommand);
    }
}
