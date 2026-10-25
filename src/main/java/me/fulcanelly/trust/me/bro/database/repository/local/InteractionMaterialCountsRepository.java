package me.fulcanelly.trust.me.bro.database.repository.local;

import java.sql.SQLException;
import java.util.List;

import me.fulcanelly.trust.me.bro.database.SqlResources;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;
import me.fulcanelly.trust.me.bro.database.repository.model.MaterialTotal;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;

/**
 * Which materials were broken/placed inside each {@code interaction_counts} row.
 * Containers are not tracked here: the container count on the row is enough.
 */
public final class InteractionMaterialCountsRepository {

    private final SqliteJdbi database;

    public InteractionMaterialCountsRepository(SqliteJdbi database) {
        this.database = database;
    }

    /** No-op for {@link SuspiciousActionType#INTERACT_CONTAINER}. */
    public synchronized void increment(
            long interactionCountId,
            String material,
            SuspiciousActionType actionType //
    ) throws SQLException {
        if (actionType == SuspiciousActionType.INTERACT_CONTAINER) {
            return;
        }
        int breakDelta = actionType == SuspiciousActionType.BREAK_BLOCK ? 1 : 0;
        int placeDelta = actionType == SuspiciousActionType.PLACE_BLOCK ? 1 : 0;

        database.withHandle(handle -> handle.createUpdate(SqlResources.load("interactions/materials/increment"))
                .bind("interactionCountId", interactionCountId)
                .bind("material", material)
                .bind("breakDelta", breakDelta)
                .bind("placeDelta", placeDelta)
                .execute());
    }

    /** Per-material totals over all given rows, biggest first. */
    public synchronized List<MaterialTotal> sumByInteractionCountIds(List<Long> ids) throws SQLException {
        if (ids.isEmpty()) {
            return List.of();
        }
        return database.withHandle(handle -> handle.createQuery(
                        SqlResources.load("interactions/materials/sum-by-interaction-ids"))
                .bindList("ids", ids)
                .map((row, context) -> new MaterialTotal(
                        row.getString("material"),
                        row.getInt("break_count"),
                        row.getInt("place_count")))
                .list());
    }
}
