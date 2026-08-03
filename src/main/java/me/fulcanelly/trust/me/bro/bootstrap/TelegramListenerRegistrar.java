package me.fulcanelly.trust.me.bro.bootstrap;

import java.util.List;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.listener.telegram.MyTrustCommand;
import me.fulcanelly.trust.me.bro.listener.telegram.TrustCallbackHandler;
import me.fulcanelly.trust.me.bro.listener.telegram.TrustStatsCommand;

/**
 * Registers Telegram/EventBus listeners owned by TrustMeBro.
 *
 * tg-bridge already posts callback and command events into EventBus.
 * This registrar wires TrustMeBro handlers into that bus and returns them
 * so runtime shutdown can unregister the same objects cleanly.
 */
@RequiredArgsConstructor
final class TelegramListenerRegistrar {

    private final AppContext context;

    List<Object> register() {
        var eventBus = context.getBridge().getEventBus();

        TrustCallbackHandler callbackHandler = new TrustCallbackHandler(context);
        MyTrustCommand myTrustCommand = new MyTrustCommand(context);
        TrustStatsCommand trustStatsCommand = new TrustStatsCommand(context);

        eventBus.register(callbackHandler);
        eventBus.register(myTrustCommand);
        eventBus.register(trustStatsCommand);
        return List.of(callbackHandler, myTrustCommand, trustStatsCommand);
    }
}
