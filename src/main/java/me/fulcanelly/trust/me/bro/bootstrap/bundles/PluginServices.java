package me.fulcanelly.trust.me.bro.bootstrap.bundles;

import lombok.Value;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.service.SuspicionDetectionService;
import me.fulcanelly.trust.me.bro.service.core.MinecraftOwnerNotificationService;
import me.fulcanelly.trust.me.bro.service.core.TrustDecisionService;
import me.fulcanelly.trust.me.bro.jobs.DirectTelegramNotificationService;

/**
 * Groups TrustMeBro service objects that depend on {@link AppContext}.
 *
 * Built after AppContext leaf fields exist. Services store AppContext and
 * resolve repos/bridge/messages through it; InstantMinecraftNotifyService
 * resolves ownerNotifications via {@code context.getServices()} only at call time.
 */
@Value
public class PluginServices {

    SuspicionDetectionService suspicionDetection;
    TrustDecisionService trustDecisions;
    MinecraftOwnerNotificationService ownerNotifications;
    DirectTelegramNotificationService directTelegramNotifications;

    public static PluginServices build(AppContext context) {
        MinecraftOwnerNotificationService ownerNotifications = new MinecraftOwnerNotificationService(context);
        DirectTelegramNotificationService directTelegramNotifications =
                new DirectTelegramNotificationService(context);
        return new PluginServices(
                new SuspicionDetectionService(context),
                new TrustDecisionService(context),
                ownerNotifications,
                directTelegramNotifications);
    }
}
