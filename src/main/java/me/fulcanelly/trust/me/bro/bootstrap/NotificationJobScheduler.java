package me.fulcanelly.trust.me.bro.bootstrap;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.jobs.NotificationService;
import me.fulcanelly.trust.me.bro.service.text.TelegramWarningMessageBuilder;

/**
 * Starts the repeating async Telegram notification job.
 *
 * Scheduling is a lifecycle side effect, separate from service construction.
 * The class uses the shared bootstrap context so the job dependencies and
 * Bukkit scheduling settings are visible in one small place.
 */
@RequiredArgsConstructor
final class NotificationJobScheduler {

    private final BootstrapContext context;

    int start() {
        var plugin = context.getPlugin();
        var bridge = context.getBridge();
        var repositories = context.getRepositories();
        var services = context.getServices();

        // Poll interval only; debounce itself is always re-read from config in NotificationService.
        long periodTicks = Math.max(
                20L,
                Math.max(1, plugin.getConfig().getLong("detection.debounce-time-sec", 60)) * 1000L / 2L / 50L);

        NotificationService notificationService = new NotificationService(
                repositories.getInteractionCounts(),
                repositories.getNotifications(),
                new TelegramWarningMessageBuilder(
                        bridge.getReception(),
                        services.getCallbackPayloads(),
                        services.getMessages(),
                        context.getCoreProtect(),
                        repositories.getRegions()),
                bridge.getBot(),
                plugin,
                bridge.getReception(),
                bridge.getMainConfig(),
                plugin.getLogger());

        return plugin.getServer()
                .getScheduler()
                .runTaskTimerAsynchronously(plugin, notificationService, periodTicks, periodTicks)
                .getTaskId();
    }
}
