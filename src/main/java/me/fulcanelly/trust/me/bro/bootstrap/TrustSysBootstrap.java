package me.fulcanelly.trust.me.bro.bootstrap;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.eventbus.EventBus;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import me.fulcanelly.tgbridge.Bridge;
import me.fulcanelly.tgbridge.tapi.TGBot;
import me.fulcanelly.tgbridge.tools.MainConfig;
import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
import me.fulcanelly.trust.me.bro.database.LocalDatabase;
import me.fulcanelly.trust.me.bro.database.MigrationRunner;
import me.fulcanelly.trust.me.bro.database.repository.coreprotect.CoreProtectDatabaseResolver;
import me.fulcanelly.trust.me.bro.database.repository.coreprotect.CoreProtectReadRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.InteractionCountsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.NotificationRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.ReportRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.TrustRepository;
import me.fulcanelly.trust.me.bro.listener.minecraft.BlockBreakSuspicionListener;
import me.fulcanelly.trust.me.bro.listener.minecraft.BlockPlaceSuspicionListener;
import me.fulcanelly.trust.me.bro.listener.minecraft.ContainerInteractionSuspicionListener;
import me.fulcanelly.trust.me.bro.listener.minecraft.PlayerJoinNotificationListener;
import me.fulcanelly.trust.me.bro.listener.telegram.TrustCallbackHandler;
import me.fulcanelly.trust.me.bro.service.LocalizationService;
import me.fulcanelly.trust.me.bro.service.NotificationService;
import me.fulcanelly.trust.me.bro.service.SuspicionDetectionService;
import me.fulcanelly.trust.me.bro.service.TrustCallbackPayloadService;
import me.fulcanelly.trust.me.bro.service.text.MinecraftWarningMessageBuilder;
import me.fulcanelly.trust.me.bro.service.text.TelegramWarningMessageBuilder;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

@RequiredArgsConstructor
public final class TrustSysBootstrap {

    private static final String COREPROTECT_PLUGIN_NAME = "CoreProtect";
    private static final String TG_BRIDGE_PLUGIN_NAME = "tg-bridge";

    private final JavaPlugin plugin;

    public Optional<TrustSysRuntime> start() throws SQLException {
        CoreProtectReadRepository coreProtect = coreProtect();
        if (!coreProtect.isAvailable()) {
            plugin.getLogger().warning("CoreProtect SQLite database was not found. TrustMeBro disabled.");
            return Optional.empty();
        }

        BridgeServices bridge = bridgeServices();
        LocalDatabase database = database();
        Repositories repositories = repositories(database.getConnection());
        Services services = services(coreProtect, repositories);

        registerMinecraftListeners(repositories, services);
        TrustCallbackHandler callbackHandler = registerTelegramListeners(bridge, repositories, services);
        int notificationTaskId = startNotificationJob(bridge, repositories, services);

        return Optional.of(new TrustSysRuntime(
                plugin,
                database,
                bridge.getEventBus(),
                callbackHandler,
                notificationTaskId));
    }

    private CoreProtectReadRepository coreProtect() {
        Plugin coreProtectPlugin = plugin.getServer().getPluginManager().getPlugin(COREPROTECT_PLUGIN_NAME);
        File coreProtectDatabase = new CoreProtectDatabaseResolver(coreProtectPlugin).resolve();
        return new CoreProtectReadRepository(coreProtectDatabase, bannedOwners());
    }

    private Supplier<Set<String>> bannedOwners() {
        if (!plugin.getConfig().getBoolean("detection.exclude-banned-players", true)) {
            return Set::of;
        }
        return () -> plugin.getServer()
                .getBannedPlayers()
                .stream()
                .map(OfflinePlayer::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private BridgeServices bridgeServices() {
        Bridge bridge = (Bridge) plugin.getServer().getPluginManager().getPlugin(TG_BRIDGE_PLUGIN_NAME);
        return new BridgeServices(
                bridge.getInjector().getInstance(TGBot.class),
                bridge.getInjector().getInstance(MainConfig.class),
                bridge.getInjector().getInstance(SignupLoginReception.class),
                bridge.getInjector().getInstance(EventBus.class));
    }

    private LocalDatabase database() throws SQLException {
        LocalDatabase database = new LocalDatabase(plugin);
        new MigrationRunner(database.getConnection()).migrate();
        return database;
    }

    private Repositories repositories(Connection connection) {
        return new Repositories(
                new TrustRepository(connection),
                new InteractionCountsRepository(connection),
                new NotificationRepository(connection),
                new ReportRepository(connection));
    }

    private Services services(CoreProtectReadRepository coreProtect, Repositories repositories) {
        return new Services(
                new TrustCallbackPayloadService(),
                new LocalizationService(plugin),
                new SuspicionDetectionService(
                        coreProtect,
                        repositories.getTrust(),
                        repositories.getInteractionCounts(),
                        plugin.getLogger()));
    }

    private void registerMinecraftListeners(Repositories repositories, Services services) {
        if (plugin.getConfig().getBoolean("detection.include-block-break", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new BlockBreakSuspicionListener(plugin, services.getSuspicionDetection()),
                    plugin);
        }
        if (plugin.getConfig().getBoolean("detection.include-block-place", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new BlockPlaceSuspicionListener(plugin, services.getSuspicionDetection()),
                    plugin);
        }
        if (plugin.getConfig().getBoolean("detection.include-container-interaction", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new ContainerInteractionSuspicionListener(plugin, services.getSuspicionDetection()),
                    plugin);
        }
        plugin.getServer().getPluginManager().registerEvents(
                new PlayerJoinNotificationListener(
                        plugin,
                        repositories.getInteractionCounts(),
                        new MinecraftWarningMessageBuilder(services.getMessages()),
                        plugin.getLogger()),
                plugin);
    }

    private TrustCallbackHandler registerTelegramListeners(
            BridgeServices bridge,
            Repositories repositories,
            Services services) {
        TrustCallbackHandler callbackHandler = new TrustCallbackHandler(
                services.getCallbackPayloads(),
                services.getMessages(),
                bridge.getReception(),
                repositories.getTrust(),
                repositories.getReports(),
                repositories.getInteractionCounts(),
                plugin.getLogger());
        bridge.getEventBus().register(callbackHandler);
        return callbackHandler;
    }

    private int startNotificationJob(BridgeServices bridge, Repositories repositories, Services services) {
        long debounceMillis = Math.max(1, plugin.getConfig().getLong("detection.debounce-time-sec", 60)) * 1000L;
        long periodTicks = Math.max(20L, (debounceMillis / 2L / 50L));
        NotificationService notificationService = new NotificationService(
                repositories.getInteractionCounts(),
                repositories.getNotifications(),
                new TelegramWarningMessageBuilder(
                        bridge.getReception(),
                        services.getCallbackPayloads(),
                        services.getMessages()),
                bridge.getBot(),
                plugin,
                bridge.getMainConfig(),
                debounceMillis,
                plugin.getLogger());
        return plugin.getServer()
                .getScheduler()
                .runTaskTimerAsynchronously(plugin, notificationService, periodTicks, periodTicks)
                .getTaskId();
    }

    @Value
    private static class BridgeServices {

        TGBot bot;
        MainConfig mainConfig;
        SignupLoginReception reception;
        EventBus eventBus;
    }

    @Value
    private static class Repositories {

        TrustRepository trust;
        InteractionCountsRepository interactionCounts;
        NotificationRepository notifications;
        ReportRepository reports;
    }

    @Value
    private static class Services {

        TrustCallbackPayloadService callbackPayloads;
        LocalizationService messages;
        SuspicionDetectionService suspicionDetection;
    }
}
