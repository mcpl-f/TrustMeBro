package me.fulcanelly.trust.me.bro.bootstrap;

import java.io.File;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.repository.coreprotect.CoreProtectDatabaseResolver;
import me.fulcanelly.trust.me.bro.database.repository.coreprotect.CoreProtectReadRepository;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Builds CoreProtect read access from Bukkit plugin state.
 *
 * It owns the CoreProtect database resolution and the banned-player filter.
 * Keeping this outside TrustSysBootstrap keeps startup readable and makes
 * CoreProtect-specific config isolated from other wiring.
 */
final class CoreProtectRepositoryFactory {

    private static final String COREPROTECT_PLUGIN_NAME = "CoreProtect";

    static CoreProtectReadRepository buildFromPlugin(JavaPlugin plugin) {
        Plugin coreProtectPlugin = plugin.getServer().getPluginManager().getPlugin(COREPROTECT_PLUGIN_NAME);
        File coreProtectDatabase = new CoreProtectDatabaseResolver(coreProtectPlugin).resolve();
        return new CoreProtectReadRepository(coreProtectDatabase, bannedOwners(plugin));
    }

    // TODO: move it away from bootstrap -> services
    private static Supplier<Set<String>> bannedOwners(JavaPlugin plugin) {
        if (!plugin.getConfig().getBoolean("detection.exclude-banned-players", true)) {
            return Set::of;
        }
        return () -> plugin.getServer()
                .getBannedPlayers()
                .stream()
                .map(OfflinePlayer::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());
    }
}
