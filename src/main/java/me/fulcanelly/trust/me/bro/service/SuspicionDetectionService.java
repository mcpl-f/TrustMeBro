package me.fulcanelly.trust.me.bro.service;

import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.fulcanelly.trust.me.bro.database.repository.coreprotect.CoreProtectReadRepository;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;
import me.fulcanelly.trust.me.bro.database.repository.local.InteractionCountsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.TrustRepository;

@RequiredArgsConstructor
public final class SuspicionDetectionService {

    private final Plugin plugin;
    private final CoreProtectReadRepository coreProtect;
    private final TrustRepository trustRepository;
    private final InteractionCountsRepository interactionCounts;
    private final Logger logger;

    public void recordBlockAction(Player interactor, Location location, SuspiciousActionType actionType) {
        try {
            record(interactor.getName(), coreProtect.findBlockOwners(location), location, actionType);
        } catch (SQLException e) {
            logger.warning("CoreProtect block lookup failed: " + e.getMessage());
        }
    }

    public void recordContainerAction(Player interactor, Location location) {
        try {
            record(
                    interactor.getName(),
                    coreProtect.findContainerOwners(location),
                    location,
                    SuspiciousActionType.INTERACT_CONTAINER);
        } catch (SQLException e) {
            logger.warning("CoreProtect container lookup failed: " + e.getMessage());
        }
    }

    private void record(
            String interactorPlayer,
            Set<String> owners,
            Location location,
            SuspiciousActionType actionType //
    ) throws SQLException {
        // TODO: consider to cache these values
        boolean splitByRegions = plugin.getConfig().getBoolean("detection.split-by-regions.enabled", false);
        int mergeDistance = Math.max(0, plugin.getConfig().getInt("detection.split-by-regions.merge-distance", 500));

        Integer wid = splitByRegions
                ? coreProtect.findWorldId(location.getWorld().getName())
                : null;

        for (String owner : owners) {
            if (owner.equals(interactorPlayer)) {
                continue;
            }

            if (splitByRegions && wid != null) {
                interactionCounts.incrementInRegion(
                        interactorPlayer,
                        owner,
                        wid,
                        location.getBlockX(),
                        location.getBlockZ(),
                        mergeDistance,
                        actionType);
            } else {
                interactionCounts.increment(interactorPlayer, owner, actionType);
            }
        }
    }
}
