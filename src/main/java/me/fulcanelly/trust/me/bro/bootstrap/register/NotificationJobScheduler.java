package me.fulcanelly.trust.me.bro.bootstrap.register;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.jobs.NotificationService;

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

    public int start() {
        var plugin = context.getPlugin();

        // Poll interval only; debounce itself is always re-read from config in NotificationService.
        long periodTicks = Math.max(
                20L,
                Math.max(1, plugin.getConfig().getLong("detection.debounce-time-sec", 60)) * 1000L / 2L / 50L);

        NotificationService notificationService = new NotificationService(context);

        return plugin.getServer()
                .getScheduler()
                .runTaskTimerAsynchronously(plugin, notificationService, periodTicks, periodTicks)
                .getTaskId();
    }
}
