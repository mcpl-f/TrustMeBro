package me.fulcanelly.trust.me.bro.database.repository.local.interactions;

import java.sql.SQLException;
import java.util.Optional;

import org.jdbi.v3.core.Handle;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.SqlResources;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;

@RequiredArgsConstructor
public final class InteractionWriteRepository {

    private final SqliteJdbi database;

    /**
     * @return id of the (legacy) row without an activity box that was incremented or created
     */
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

    /**
     * Activity-box-aware record: merge into a nearby activity box (pending or
     * already notified) or insert a new one. Distance is axis-aligned (no sqrt):
     * point within mergeDistance of the box AABB.
     *
     * @return id of the {@code interaction_counts} row that was incremented or created
     */
    public long incrementInActivityBox(
            String interactorPlayer,
            String ownerPlayer,
            int wid,
            int x,
            int z,
            int mergeDistance,
            SuspiciousActionType actionType) throws SQLException {
        return database.withHandle(handle -> {
            Optional<Long> nearbyId = findNearbyActivityBoxId(
                    handle, interactorPlayer, ownerPlayer, wid, x, z, mergeDistance);
            if (nearbyId.isPresent()) {
                expandActivityBoxAndIncrement(handle, nearbyId.get(), x, z, actionType);
                return nearbyId.get();
            }
            return insertActivityBox(handle, interactorPlayer, ownerPlayer, wid, x, z, actionType);
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

    /*
     * Merge includes already-notified rows so nearby follow-up grief does not
     * spawn a new pending row / Telegram spam. Prefer a still-pending match.
     *
     * WARNING: an activity box is NOT a named region ({@code regions} table,
     * NamedRegion). It is a bounding box stored on an {@code interaction_counts}
     * row, and the returned id is {@code interaction_counts.id}. Its columns are
     * still called {@code region_corner_*}.
     */
    private Optional<Long> findNearbyActivityBoxId(
            Handle handle,
            String interactorPlayer,
            String ownerPlayer,
            int wid,
            int x,
            int z,
            int mergeDistance) {
        return handle.createQuery(SqlResources.load("interactions/write/find-nearby-activity-box"))
                .bind("interactor", interactorPlayer)
                .bind("owner", ownerPlayer)
                .bind("wid", wid)
                .bind("x", x)
                .bind("z", z)
                .bind("mergeDistance", mergeDistance)
                .mapTo(Long.class)
                .findOne();
    }

    /**
     * WARNING: {@code id} is {@code interaction_counts.id}, and the box being
     * expanded is the activity box on that row, not the {@code regions} table.
     */
    private void expandActivityBoxAndIncrement(Handle handle, long id, int x, int z, SuspiciousActionType actionType) {
        int breakDelta = actionType == SuspiciousActionType.BREAK_BLOCK ? 1 : 0;
        int placeDelta = actionType == SuspiciousActionType.PLACE_BLOCK ? 1 : 0;
        int containerDelta = actionType == SuspiciousActionType.INTERACT_CONTAINER ? 1 : 0;

        var bounds = handle.createQuery(SqlResources.load("interactions/write/find-activity-box-bounds"))
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

        int[] box = bounds.get();
        long updatedAt = System.currentTimeMillis();
        handle.createUpdate(SqlResources.load("interactions/write/expand-activity-box"))
                .bind("breakDelta", breakDelta)
                .bind("placeDelta", placeDelta)
                .bind("containerDelta", containerDelta)
                .bind("minX", Math.min(Math.min(box[0], box[2]), x))
                .bind("minZ", Math.min(Math.min(box[1], box[3]), z))
                .bind("maxX", Math.max(Math.max(box[0], box[2]), x))
                .bind("maxZ", Math.max(Math.max(box[1], box[3]), z))
                .bind("now", updatedAt)
                .bind("id", id)
                .execute();
    }

    /**
     * WARNING: inserts a new {@code interaction_counts} row with an activity box,
     * it does NOT create a named region in the {@code regions} table.
     *
     * @return id of the inserted {@code interaction_counts} row
     */
    private long insertActivityBox(
            Handle handle,
            String interactorPlayer,
            String ownerPlayer,
            int wid,
            int x,
            int z,
            SuspiciousActionType actionType) {
        long now = System.currentTimeMillis();
        return handle.createQuery(SqlResources.load("interactions/write/insert-activity-box"))
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
