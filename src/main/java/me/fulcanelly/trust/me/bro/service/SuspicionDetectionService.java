package me.fulcanelly.trust.me.bro.service;

import java.sql.SQLException;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;
import me.fulcanelly.trust.me.bro.service.core.InstantMinecraftNotifyService;

public final class SuspicionDetectionService {

    private final AppContext context;
    private final InstantMinecraftNotifyService instantNotify;

    public SuspicionDetectionService(AppContext context) {
        this.context = context;
        this.instantNotify = new InstantMinecraftNotifyService(context);
    }

    /**
     * @param material block type captured on the main thread; it may already be AIR
     *                 by the time this runs
     */
    public void recordBlockAction(
            Player interactor,
            Location location,
            SuspiciousActionType actionType,
            Material material //
    ) {
        try {
            record(
                    interactor.getName(),
                    context.getCoreProtect().findBlockOwners(location),
                    location,
                    actionType,
                    material);
        } catch (SQLException e) {
            context.getLogger().warning("CoreProtect block lookup failed: " + e.getMessage());
        }
    }

    public void recordContainerAction(Player interactor, Location location) {
        try {
            record(
                    interactor.getName(),
                    context.getCoreProtect().findContainerOwners(location),
                    location,
                    SuspiciousActionType.INTERACT_CONTAINER,
                    // no material: the container count on the row is enough, see
                    // InteractionMaterialCountsRepository
                    null);
        } catch (SQLException e) {
            context.getLogger().warning("CoreProtect container lookup failed: " + e.getMessage());
        }
    }

    private void record(
            String interactorPlayer,
            Set<String> owners,
            Location location,
            SuspiciousActionType actionType,
            Material material //
    ) throws SQLException {
        var plugin = context.getPlugin();
        var coreProtect = context.getCoreProtect();
        var interactionCounts = context.getRepositories().getInteractionCounts();
        var interactionMaterials = context.getRepositories().getInteractionMaterials();

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

            long interactionCountId;

            if (splitByRegions && wid != null) {
                interactionCountId = interactionCounts.incrementInRegion(
                        interactorPlayer,
                        owner,
                        wid,
                        location.getBlockX(),
                        location.getBlockZ(),
                        mergeDistance,
                        actionType);
            } else {
                interactionCountId = interactionCounts.increment(interactorPlayer, owner, actionType);
            }

            if (material != null) {
                // Namespaced key (minecraft:stone), same format as CoreProtect's
                // co_material_map.
                interactionMaterials.increment(interactionCountId, material.getKey().toString(), actionType);
            }

            instantNotify.notifyOwnerIfEnabled(owner);
        }
    }
}
