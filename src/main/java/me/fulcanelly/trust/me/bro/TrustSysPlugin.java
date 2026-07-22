package me.fulcanelly.trust.me.bro;

import java.io.File;
import java.sql.SQLException;

import com.google.common.eventbus.EventBus;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import me.fulcanelly.tgbridge.Bridge;
import me.fulcanelly.tgbridge.tapi.TGBot;
import me.fulcanelly.tgbridge.tools.MainConfig;
import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
import me.fulcanelly.trust.me.bro.database.repository.coreprotect.CoreProtectDatabaseResolver;
import me.fulcanelly.trust.me.bro.database.repository.coreprotect.CoreProtectReadRepository;
import me.fulcanelly.trust.me.bro.database.LocalDatabase;
import me.fulcanelly.trust.me.bro.database.MigrationRunner;
import me.fulcanelly.trust.me.bro.listener.minecraft.BlockBreakSuspicionListener;
import me.fulcanelly.trust.me.bro.listener.minecraft.BlockPlaceSuspicionListener;
import me.fulcanelly.trust.me.bro.listener.minecraft.ContainerInteractionSuspicionListener;
import me.fulcanelly.trust.me.bro.listener.minecraft.PlayerJoinNotificationListener;
import me.fulcanelly.trust.me.bro.listener.telegram.TrustCallbackHandler;
import me.fulcanelly.trust.me.bro.database.repository.local.InteractionCountsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.NotificationRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.ReportRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.TrustRepository;
import me.fulcanelly.trust.me.bro.service.LocalizationService;
import me.fulcanelly.trust.me.bro.service.NotificationService;
import me.fulcanelly.trust.me.bro.service.SuspicionDetectionService;
import me.fulcanelly.trust.me.bro.service.TrustCallbackPayloadService;
import me.fulcanelly.trust.me.bro.service.text.MinecraftWarningMessageBuilder;
import me.fulcanelly.trust.me.bro.service.text.TelegramWarningMessageBuilder;

public final class TrustSysPlugin extends JavaPlugin {

    private static final String COREPROTECT_PLUGIN_NAME = "CoreProtect";
    private static final String TG_BRIDGE_PLUGIN_NAME = "tg-bridge";

    private LocalDatabase database;
    private int notificationTaskId = -1;
    private EventBus eventBus;
    private TrustCallbackHandler trustCallbackHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            Plugin coreProtectPlugin = getServer().getPluginManager().getPlugin(COREPROTECT_PLUGIN_NAME);
            File coreProtectDatabase = new CoreProtectDatabaseResolver(coreProtectPlugin).resolve();
            CoreProtectReadRepository coreProtect = new CoreProtectReadRepository(coreProtectDatabase);
            if (!coreProtect.isAvailable()) {
                getLogger().warning("CoreProtect SQLite database was not found. TrustMeBro disabled.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            Bridge bridge = (Bridge) getServer().getPluginManager().getPlugin(TG_BRIDGE_PLUGIN_NAME);
            TGBot bot = bridge.getInjector().getInstance(TGBot.class);
            MainConfig mainConfig = bridge.getInjector().getInstance(MainConfig.class);
            SignupLoginReception reception = bridge.getInjector().getInstance(SignupLoginReception.class);
            eventBus = bridge.getInjector().getInstance(EventBus.class);

            database = new LocalDatabase(this);
            new MigrationRunner(database.getConnection()).migrate();

            TrustRepository trustRepository = new TrustRepository(database.getConnection());
            InteractionCountsRepository interactionCounts = new InteractionCountsRepository(database.getConnection());
            NotificationRepository notificationRepository = new NotificationRepository(database.getConnection());
            ReportRepository reportRepository = new ReportRepository(database.getConnection());
            TrustCallbackPayloadService callbackPayloads = new TrustCallbackPayloadService();
            LocalizationService messages = new LocalizationService(this);

            SuspicionDetectionService detectionService = new SuspicionDetectionService(
                    coreProtect,
                    trustRepository,
                    interactionCounts,
                    getLogger());

            registerMinecraftListeners(interactionCounts, detectionService, messages);
            registerTelegramListeners(callbackPayloads, messages, reception, trustRepository, reportRepository, interactionCounts);
            startNotificationJob(interactionCounts, notificationRepository, bot, mainConfig, reception, callbackPayloads, messages);
        } catch (Exception e) {
            getLogger().warning("TrustMeBro failed to start: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (notificationTaskId != -1) {
            getServer().getScheduler().cancelTask(notificationTaskId);
            notificationTaskId = -1;
        }
        if (eventBus != null && trustCallbackHandler != null) {
            eventBus.unregister(trustCallbackHandler);
            trustCallbackHandler = null;
        }
        if (database != null) {
            try {
                database.close();
            } catch (SQLException e) {
                getLogger().warning("TrustMeBro database close failed: " + e.getMessage());
            }
        }
    }

    private void registerTelegramListeners(
            TrustCallbackPayloadService callbackPayloads,
            LocalizationService messages,
            SignupLoginReception reception,
            TrustRepository trustRepository,
            ReportRepository reportRepository,
            InteractionCountsRepository interactionCounts) {
        trustCallbackHandler = new TrustCallbackHandler(
                callbackPayloads,
                messages,
                reception,
                trustRepository,
                reportRepository,
                interactionCounts,
                getLogger());
        eventBus.register(trustCallbackHandler);
    }

    private void registerMinecraftListeners(
            InteractionCountsRepository interactionCounts,
            SuspicionDetectionService detectionService,
            LocalizationService messages) {
        if (getConfig().getBoolean("detection.include-block-break", true)) {
            getServer().getPluginManager().registerEvents(
                    new BlockBreakSuspicionListener(this, detectionService),
                    this);
        }
        if (getConfig().getBoolean("detection.include-block-place", true)) {
            getServer().getPluginManager().registerEvents(
                    new BlockPlaceSuspicionListener(this, detectionService),
                    this);
        }
        if (getConfig().getBoolean("detection.include-container-interaction", true)) {
            getServer().getPluginManager().registerEvents(
                    new ContainerInteractionSuspicionListener(this, detectionService),
                    this);
        }
        if (getConfig().getBoolean("minecraft.notify-on-join", true)) {
            getServer().getPluginManager().registerEvents(
                    new PlayerJoinNotificationListener(
                            this,
                            interactionCounts,
                            new MinecraftWarningMessageBuilder(messages),
                            getLogger()),
                    this);
        }
    }

    private void startNotificationJob(
            InteractionCountsRepository interactionCounts,
            NotificationRepository notificationRepository,
            TGBot bot,
            MainConfig mainConfig,
            SignupLoginReception reception,
            TrustCallbackPayloadService callbackPayloads,
            LocalizationService messages) {
        long debounceMillis = Math.max(1, getConfig().getLong("detection.debounce-time-sec", 60)) * 1000L;
        long periodTicks = Math.max(20L, (debounceMillis / 2L / 50L));
        NotificationService notificationService = new NotificationService(
                interactionCounts,
                notificationRepository,
                new TelegramWarningMessageBuilder(reception, callbackPayloads, messages),
                bot,
                mainConfig,
                debounceMillis,
                getLogger());
        notificationTaskId = getServer().getScheduler().runTaskTimerAsynchronously(
                this,
                notificationService,
                periodTicks,
                periodTicks).getTaskId();
    }
}
