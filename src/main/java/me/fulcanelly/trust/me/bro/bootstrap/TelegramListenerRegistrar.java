package me.fulcanelly.trust.me.bro.bootstrap;

import java.util.List;
import java.util.logging.Logger;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.listener.telegram.MyTrustCommand;
import me.fulcanelly.trust.me.bro.listener.telegram.TrustCallbackHandler;
import me.fulcanelly.trust.me.bro.listener.telegram.TrustStatsCommand;
import me.fulcanelly.trust.me.bro.service.text.TrustStatsMessageBuilder;

/**
 * Registers Telegram/EventBus listeners owned by TrustMeBro.
 *
 * tg-bridge already posts callback and command events into EventBus.
 * This registrar wires TrustMeBro handlers into that bus and returns them
 * so runtime shutdown can unregister the same objects cleanly.
 */
@RequiredArgsConstructor
final class TelegramListenerRegistrar {

    private final BootstrapContext context;

    List<Object> register() {
        var bridge = context.getBridge();
        var repositories = context.getRepositories();
        var services = context.getServices();
        Logger logger = context.getPlugin().getLogger();

        TrustCallbackHandler callbackHandler = new TrustCallbackHandler(
                services.getCallbackPayloads(),
                services.getMessages(),
                bridge.getReception(),
                services.getTrustDecisions(),
                repositories.getInteractionCounts(),
                logger);
        MyTrustCommand myTrustCommand = new MyTrustCommand(bridge.getReception());
        TrustStatsCommand trustStatsCommand = new TrustStatsCommand(
                context.getPlugin(),
                repositories.getTrust(),
                repositories.getInteractionCounts(),
                repositories.getReports(),
                new TrustStatsMessageBuilder(services.getMessages()),
                services.getMessages(),
                logger);

        bridge.getEventBus().register(callbackHandler);
        bridge.getEventBus().register(myTrustCommand);
        bridge.getEventBus().register(trustStatsCommand);
        return List.of(callbackHandler, myTrustCommand, trustStatsCommand);
    }
}
