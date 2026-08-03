package me.fulcanelly.trust.me.bro.bootstrap;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.LocalDatabase;
import me.fulcanelly.trust.me.bro.database.MigrationRunner;
import me.fulcanelly.trust.me.bro.database.repository.coreprotect.CoreProtectReadRepository;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Orchestrates TrustMeBro startup in the Bukkit lifecycle.
 *
 * This class should read like a sequence of bootstrap steps, not like a bag of
 * construction details. Factories build data/services, registrars perform side
 * effects, and the returned runtime owns shutdown cleanup.
 */
@RequiredArgsConstructor
public final class TrustSysBootstrap {

    private final JavaPlugin plugin;

    public Optional<TrustSysRuntime> start() throws SQLException {
        CoreProtectReadRepository coreProtect = CoreProtectRepositoryFactory.buildFromPlugin(plugin);
        if (!coreProtect.isAvailable()) {
            plugin.getLogger().warning("CoreProtect SQLite database was not found. TrustMeBro disabled.");
            return Optional.empty();
        }

        BridgeServices bridge = BridgeServices.buildFromPlugin(plugin);
        LocalDatabase database = database();
        LocalRepositories repositories = LocalRepositories.buildFromConnection(database.getConnection());
        AppContext app = AppContext.create(plugin, bridge, repositories, coreProtect);

        new MinecraftListenerRegistrar(app).register();
        List<Object> telegramListeners = new TelegramListenerRegistrar(app).register();
        int notificationTaskId = new NotificationJobScheduler(app).start();

        return Optional.of(new TrustSysRuntime(
                plugin,
                database,
                bridge.getEventBus(),
                telegramListeners,
                notificationTaskId));
    }

    private LocalDatabase database() throws SQLException {
        LocalDatabase database = new LocalDatabase(plugin);
        new MigrationRunner(database.getConnection()).migrate();
        return database;
    }
}
