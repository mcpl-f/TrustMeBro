package me.fulcanelly.trust.me.bro.database.repository.local;

import java.sql.SQLException;
import java.util.Optional;

import org.jdbi.v3.core.Handle;

import me.fulcanelly.trust.me.bro.database.SqlResources;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;

public final class InteractionWriteRepository {

    private final SqliteJdbi database;

    public InteractionWriteRepository(SqliteJdbi database) {
        this.database = database;
    }

    public long increment(String interactorPlayer, String ownerPlayer, SuspiciousActionType actionType)
            throws SQLException {
        long now = System.currentTimeMillis();
        int breakDelta = actionType == SuspiciousActionType.BREAK_BLOCK ? 1 : 0;
        int placeDelta = actionType == SuspiciousActionType.PLACE_BLOCK ? 1 : 0;
        int containerDelta = actionType == SuspiciousActionType.INTERACT_CONTAINER ? 1 : 0;

        return database.withHandle(handle -> {
            var updated = handle.createQuery(SqlResources.load("interactions/write/update-legacy-count"))
                    .bind("breakDelta", breakDelta)
                    .bind("placeDelta", placeDelta)
                    .bind("containerDelta", containerDelta)
                    .bind("now", now)
                    .bind("interactor", interactorPlayer)
                    .bind("owner", ownerPlayer)
                    .mapTo(Long.class)
                    .findOne();
            if (updated.isPresent()) {
                return updated.get();
            }

            return handle.createQuery(SqlResources.load("interactions/write/insert-legacy-count"))
                    .bind("interactor", interactorPlayer)
                    .bind("owner", ownerPlayer)
                    .bind("breakDelta", breakDelta)
                    .bind("placeDelta", placeDelta)
                    .bind("containerDelta", containerDelta)
                    .bind("now", now)
                    .mapTo(Long.class)
                    .one();
        });
    }

    public long incrementInRegion(
            String interactorPlayer,
            String ownerPlayer,
            int wid,
            int x,
            int z,
            int mergeDistance,
            SuspiciousActionType actionType) throws SQLException {
        return database.withHandle(handle -> {
            Optional<Long> nearbyId = findNearbyRegionId(
                    handle, interactorPlayer, ownerPlayer, wid, x, z, mergeDistance);
            if (nearbyId.isPresent()) {
                expandRegionAndIncrement(handle, nearbyId.get(), x, z, actionType);
                return nearbyId.get();
            }
            return insertRegion(handle, interactorPlayer, ownerPlayer, wid, x, z, actionType);
        });
    }

    public boolean exists(String interactorPlayer, String ownerPlayer) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(
                "SELECT 1 FROM interaction_counts WHERE interactor_player = :interactor AND owner = :owner")
                .bind("interactor", interactorPlayer)
                .bind("owner", ownerPlayer)
                .mapTo(Integer.class)
                .findOne()
                .isPresent());
    }

    private Optional<Long> findNearbyRegionId(
            Handle handle,
            String interactorPlayer,
            String ownerPlayer,
            int wid,
            int x,
            int z,
            int mergeDistance) {
        return handle.createQuery(SqlResources.load("interactions/write/find-nearby-region"))
                .bind("interactor", interactorPlayer)
                .bind("owner", ownerPlayer)
                .bind("wid", wid)
                .bind("x", x)
                .bind("z", z)
                .bind("mergeDistance", mergeDistance)
                .mapTo(Long.class)
                .findOne();
    }

    private void expandRegionAndIncrement(Handle handle, long id, int x, int z, SuspiciousActionType actionType) {
        int breakDelta = actionType == SuspiciousActionType.BREAK_BLOCK ? 1 : 0;
        int placeDelta = actionType == SuspiciousActionType.PLACE_BLOCK ? 1 : 0;
        int containerDelta = actionType == SuspiciousActionType.INTERACT_CONTAINER ? 1 : 0;

        var bounds = handle.createQuery(SqlResources.load("interactions/write/find-region-bounds"))
                .bind("id", id)
                .map((row, context) -> new int[] {
                        row.getInt("region_corner_a_x"),
                        row.getInt("region_corner_a_z"),
                        row.getInt("region_corner_b_x"),
                        row.getInt("region_corner_b_z")
                })
                .findOne();
        if (bounds.isEmpty()) {
            return;
        }

        int[] region = bounds.get();
        long updatedAt = System.currentTimeMillis();
        handle.createUpdate(SqlResources.load("interactions/write/expand-region"))
                .bind("breakDelta", breakDelta)
                .bind("placeDelta", placeDelta)
                .bind("containerDelta", containerDelta)
                .bind("minX", Math.min(Math.min(region[0], region[2]), x))
                .bind("minZ", Math.min(Math.min(region[1], region[3]), z))
                .bind("maxX", Math.max(Math.max(region[0], region[2]), x))
                .bind("maxZ", Math.max(Math.max(region[1], region[3]), z))
                .bind("now", updatedAt)
                .bind("id", id)
                .execute();
    }

    private long insertRegion(
            Handle handle,
            String interactorPlayer,
            String ownerPlayer,
            int wid,
            int x,
            int z,
            SuspiciousActionType actionType) {
        long now = System.currentTimeMillis();
        return handle.createQuery(SqlResources.load("interactions/write/insert-region"))
                .bind("interactor", interactorPlayer)
                .bind("owner", ownerPlayer)
                .bind("breakDelta", actionType == SuspiciousActionType.BREAK_BLOCK ? 1 : 0)
                .bind("placeDelta", actionType == SuspiciousActionType.PLACE_BLOCK ? 1 : 0)
                .bind("containerDelta", actionType == SuspiciousActionType.INTERACT_CONTAINER ? 1 : 0)
                .bind("wid", wid)
                .bind("x", x)
                .bind("z", z)
                .bind("now", now)
                .mapTo(Long.class)
                .one();
    }
}
