package me.fulcanelly.trust.me.bro.bootstrap.register;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.jobs.NotificationService;
import me.fulcanelly.trust.me.bro.jobs.DirectTelegramNotificationService;

/**
 * Starts the repeating async Telegram notification job.
 *
 * Scheduling is a lifecycle side effect, separate from service construction.
 * The class uses the shared app context so the job dependencies and
 * Bukkit scheduling settings are visible in one small place.
 */
@RequiredArgsConstructor
public final class NotificationJobScheduler {

    private final AppContext context;

    public java.util.List<Integer> start() {
        var plugin = context.getPlugin();

        // Poll interval only; debounce itself is always re-read from config in NotificationService.
        long periodTicks = Math.max(
                20L,
                Math.max(1, plugin.getConfig().getLong("detection.debounce-time-sec", 60)) * 1000L / 2L / 50L);

        NotificationService notificationService = new NotificationService(context);

        int notificationTaskId = plugin.getServer()
                .getScheduler()
                .runTaskTimerAsynchronously(plugin, notificationService, periodTicks, periodTicks)
                .getTaskId();

        DirectTelegramNotificationService directTelegramNotificationService =
                new DirectTelegramNotificationService(context);
        int directTelegramNotificationTaskId = plugin.getServer()
                .getScheduler()
                .runTaskTimerAsynchronously(
                        plugin, directTelegramNotificationService, periodTicks, periodTicks)
                .getTaskId();

        return java.util.List.of(notificationTaskId, directTelegramNotificationTaskId);
    }
}
