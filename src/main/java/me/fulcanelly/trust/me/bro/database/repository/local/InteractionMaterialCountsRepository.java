package me.fulcanelly.trust.me.bro.database.repository.local;

import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.fulcanelly.trust.me.bro.database.repository.model.MaterialTotal;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;

/**
 * Which materials were broken/placed inside each {@code interaction_counts} row.
 * Containers are not tracked here: the container count on the row is enough.
 */
@RequiredArgsConstructor
public final class InteractionMaterialCountsRepository {

    private final Connection connection;

    /** No-op for {@link SuspiciousActionType#INTERACT_CONTAINER}. */
    public synchronized void increment(
            long interactionCountId,
            String material,
            SuspiciousActionType actionType //
    ) throws SQLException {
        int breakDelta = 0;
        int placeDelta = 0;
        if (actionType == SuspiciousActionType.BREAK_BLOCK) {
            breakDelta = 1;
        } else if (actionType == SuspiciousActionType.PLACE_BLOCK) {
            placeDelta = 1;
        } else {
            return;
        }

        try (var statement = connection.prepareStatement("""
                INSERT INTO interaction_material_counts(interaction_count_id, material, break_count, place_count)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(interaction_count_id, material) DO UPDATE SET
                  break_count = break_count + excluded.break_count,
                  place_count = place_count + excluded.place_count
                """)) {
            statement.setLong(1, interactionCountId);
            statement.setString(2, material);
            statement.setInt(3, breakDelta);
            statement.setInt(4, placeDelta);
            statement.executeUpdate();
        }
    }

    /** Per-material totals over all given rows, biggest first. */
    public synchronized List<MaterialTotal> sumByInteractionCountIds(List<Long> ids) throws SQLException {
        var result = new ArrayList<MaterialTotal>();
        if (ids.isEmpty()) {
            return result;
        }

        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        try (var statement = connection.prepareStatement("""
                SELECT material,
                       SUM(break_count) AS break_count,
                       SUM(place_count) AS place_count
                FROM interaction_material_counts
                WHERE interaction_count_id IN (%s)
                GROUP BY material
                ORDER BY SUM(break_count) + SUM(place_count) DESC, material ASC
                """.formatted(placeholders))) {
            for (int i = 0; i < ids.size(); i++) {
                statement.setLong(i + 1, ids.get(i));
            }
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new MaterialTotal(
                            rows.getString("material"),
                            rows.getInt("break_count"),
                            rows.getInt("place_count")));
                }
            }
        }
        return result;
    }
}
