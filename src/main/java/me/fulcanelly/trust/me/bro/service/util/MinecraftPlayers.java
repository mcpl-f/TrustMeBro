package me.fulcanelly.trust.me.bro.service.util;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Player identity helpers. Never case-folds Minecraft usernames.
 *
 * <p>Spigot {@code getPlayerExact}/{@code getPlayer}/{@code getOfflinePlayer(String)}
 * are case-<b>in</b>sensitive — always re-check with {@code equals} on the real name.
 */
public final class MinecraftPlayers {

    /**
     * Online player whose {@link Player#getName()} equals {@code name} (case-sensitive).
     * Spigot lookup alone is not enough.
     */
    public static Player getOnlineExact(Server server, String name) {
        Player online = server.getPlayerExact(name);
        if (online == null || !online.getName().equals(name)) {
            return null;
        }
        return online;
    }

    /** Online with exact name, or has joined before under that exact name. */
    @SuppressWarnings("deprecation")
    public static boolean isKnownPlayer(Server server, String name) {
        if (getOnlineExact(server, name) != null) {
            return true;
        }
        OfflinePlayer offline = server.getOfflinePlayer(name);
        return offline.hasPlayedBefore()
                && offline.getName() != null
                && offline.getName().equals(name);
    }
}
