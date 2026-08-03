package me.fulcanelly.trust.me.bro.service.core;

import lombok.RequiredArgsConstructor;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.fulcanelly.trust.me.bro.service.util.MinecraftPlayers;

/**
 * When {@code minecraft.notify-instantly} is on, push an in-game warning to an
 * online owner right after a new interaction is recorded (no join delay).
 *
 * <p>Called from the suspicion path used by block/container listeners.
 */
@RequiredArgsConstructor
public final class InstantMinecraftNotifyService {

    private final Plugin plugin;
    private final MinecraftOwnerNotificationService ownerNotifications;

    public void notifyOwnerIfEnabled(String ownerMcName) {
        if (!plugin.getConfig().getBoolean("minecraft.notify-instantly", false)) {
            return;
        }

        Player owner = MinecraftPlayers.getOnlineExact(plugin.getServer(), ownerMcName);
        if (owner == null) {
            return;
        }

        ownerNotifications.notifyNow(owner);
    }
}
