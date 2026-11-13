package me.fulcanelly.trust.me.bro.bootstrap.register;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.jobs.DirectTelegramNotificationService;

@RequiredArgsConstructor
public final class DirectTelegramNotificationJobScheduler {

    private final AppContext context;

    public int start() {
        var plugin = context.getPlugin();
        long periodTicks = Math.max(
                20L,
                Math.max(1, plugin.getConfig().getLong("detection.debounce-time-sec", 60)) * 1000L / 2L / 50L);

        DirectTelegramNotificationService service = new DirectTelegramNotificationService(context);
        return plugin.getServer()
                .getScheduler()
                .runTaskTimerAsynchronously(plugin, service, periodTicks, periodTicks)
                .getTaskId();
    }
}
