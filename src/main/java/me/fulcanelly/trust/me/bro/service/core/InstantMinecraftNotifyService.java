package me.fulcanelly.trust.me.bro.service.core;

import lombok.RequiredArgsConstructor;

import org.bukkit.entity.Player;

import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.service.util.MinecraftPlayers;

/**
 * When {@code minecraft.notify-instantly} is on, push an in-game warning to an
 * online owner right after a new interaction is recorded (no join delay).
 *
 * <p>Called from the suspicion path used by block/container listeners.
 * Resolves {@link MinecraftOwnerNotificationService} via AppContext at call time
 * so construction can finish before PluginServices is assigned.
 */
@RequiredArgsConstructor
public final class InstantMinecraftNotifyService {

    private final AppContext context;

    public void notifyOwnerIfEnabled(String ownerMcName) {
        if (!context.getPlugin().getConfig().getBoolean("minecraft.notify-instantly", false)) {
            return;
        }

        Player owner = MinecraftPlayers.getOnlineExact(context.getPlugin().getServer(), ownerMcName);
        if (owner == null) {
            return;
        }

        context.getServices().getOwnerNotifications().notifyNow(owner);
    }
}
