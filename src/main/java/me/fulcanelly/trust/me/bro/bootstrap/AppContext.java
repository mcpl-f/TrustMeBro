package me.fulcanelly.trust.me.bro.bootstrap;

import java.util.logging.Logger;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.repository.coreprotect.CoreProtectReadRepository;
import me.fulcanelly.trust.me.bro.service.TrustCallbackPayloadService;
import me.fulcanelly.trust.me.bro.service.util.LocalizationService;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Stable application graph shared by services, listeners, and jobs.
 *
 * Built in two phases so services can take {@code AppContext} without a
 * circular constructor dependency: leaf fields first, then {@link PluginServices}.
 * Lifecycle concerns (DB close, task ids, EventBus unregister) stay on
 * {@link TrustSysRuntime}, not here.
 *
 * <p>TODO: if thread rules get harder, consider splitting into sync-safe vs
 * async-safe views (Spigot main-thread APIs vs JDBC/repos usable off-thread).
 * Right now callers must know which fields are safe where.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class AppContext {

    private final JavaPlugin plugin;
    private final BridgeServices bridge;
    private final LocalRepositories repositories;
    private final CoreProtectReadRepository coreProtect;
    private final LocalizationService messages;
    private final TrustCallbackPayloadService callbackPayloads;

    /** Set once after leaf fields exist; never read during PluginServices construction. */
    private PluginServices services;

    public static AppContext create(
            JavaPlugin plugin,
            BridgeServices bridge,
            LocalRepositories repositories,
            CoreProtectReadRepository coreProtect //
    ) {
        AppContext context = new AppContext(
                plugin,
                bridge,
                repositories,
                coreProtect,
                new LocalizationService(plugin),
                new TrustCallbackPayloadService());
        context.services = PluginServices.build(context);
        return context;
    }

    public Logger getLogger() {
        return plugin.getLogger();
    }
}
