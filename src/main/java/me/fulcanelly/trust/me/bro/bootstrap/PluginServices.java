package me.fulcanelly.trust.me.bro.bootstrap;

import lombok.Value;
import me.fulcanelly.trust.me.bro.database.repository.coreprotect.CoreProtectReadRepository;
import me.fulcanelly.trust.me.bro.service.LocalizationService;
import me.fulcanelly.trust.me.bro.service.SuspicionDetectionService;
import me.fulcanelly.trust.me.bro.service.TrustCallbackPayloadService;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Groups TrustMeBro service objects that are shared by listeners and jobs.
 *
 * These services are built after repositories and CoreProtect access exist.
 * Keeping them in one bundle avoids long parameter lists while preserving
 * explicit construction order in the bootstrap.
 */
@Value
final class PluginServices {

    TrustCallbackPayloadService callbackPayloads;
    LocalizationService messages;
    SuspicionDetectionService suspicionDetection;

    static PluginServices buildFromPlugin(
            JavaPlugin plugin,
            CoreProtectReadRepository coreProtect,
            LocalRepositories repositories) {
        return new PluginServices(
                new TrustCallbackPayloadService(),
                new LocalizationService(plugin),
                new SuspicionDetectionService(
                        plugin,
                        coreProtect,
                        repositories.getTrust(),
                        repositories.getInteractionCounts(),
                        plugin.getLogger()));
    }
}
