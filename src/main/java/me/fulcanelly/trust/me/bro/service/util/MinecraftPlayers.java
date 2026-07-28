package me.fulcanelly.trust.me.bro.service.util;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Player identity helpers. Never case-folds Minecraft usernames.
 */
public final class MinecraftPlayers {

    private MinecraftPlayers() {
    }

    /** Online with exact name, or has joined before under that exact name. */
    @SuppressWarnings("deprecation")
    public static boolean isKnownPlayer(Server server, String name) {
        Player online = server.getPlayerExact(name);
        if (online != null) {
            return true;
        }
        OfflinePlayer offline = server.getOfflinePlayer(name);
        return offline.hasPlayedBefore()
                && offline.getName() != null
                && offline.getName().equals(name);
    }
}
