package me.fulcanelly.trust.me.bro.database.repository.local;

import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;

@RequiredArgsConstructor
public final class InteractionCountsRepository {

    private static final String PENDING_SELECT = """
            SELECT id, interactor_player, owner,
                   count_break_blocks, count_placed_blocks, count_interact_containers,
                   wid, region_corner_a_x, region_corner_a_z, region_corner_b_x, region_corner_b_z
            FROM interaction_counts
            """;

    private final Connection connection;

    public synchronized void increment(String interactorPlayer, String ownerPlayer, SuspiciousActionType actionType)
            throws SQLException {
        long now = System.currentTimeMillis();
        int breakDelta = actionType == SuspiciousActionType.BREAK_BLOCK ? 1 : 0;
        int placeDelta = actionType == SuspiciousActionType.PLACE_BLOCK ? 1 : 0;
        int containerDelta = actionType == SuspiciousActionType.INTERACT_CONTAINER ? 1 : 0;

        try (var statement = connection.prepareStatement("""
                UPDATE interaction_counts
                SET count_break_blocks = count_break_blocks + ?,
                    count_placed_blocks = count_placed_blocks + ?,
                    count_interact_containers = count_interact_containers + ?,
                    updated_at = ?
                WHERE interactor_player = ?
                  AND owner = ?
                  AND wid IS NULL
                """)) {
            statement.setInt(1, breakDelta);
            statement.setInt(2, placeDelta);
            statement.setInt(3, containerDelta);
            statement.setLong(4, now);
            statement.setString(5, interactorPlayer);
            statement.setString(6, ownerPlayer);
            if (statement.executeUpdate() > 0) {
                return;
            }
        }

        try (var statement = connection.prepareStatement("""
                INSERT INTO interaction_counts(
                  interactor_player,
                  owner,
                  count_break_blocks,
                  count_placed_blocks,
                  count_interact_containers,
                  notification_id,
                  created_at,
                  updated_at
                ) VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
                """)) {
            statement.setString(1, interactorPlayer);
            statement.setString(2, ownerPlayer);
            statement.setInt(3, breakDelta);
            statement.setInt(4, placeDelta);
            statement.setInt(5, containerDelta);
            statement.setLong(6, now);
            statement.setLong(7, now);
            statement.executeUpdate();
        }
    }

    /**
     * Region-aware record: merge into a nearby pending region or insert a new one.
     * Distance is axis-aligned (no sqrt): point within mergeDistance of the region
     * AABB.
     */
    public synchronized void incrementInRegion(
            String interactorPlayer,
            String ownerPlayer,
            int wid,
            int x,
            int z,
            int mergeDistance,
            SuspiciousActionType actionType) throws SQLException {
        Optional<Long> nearbyId = findNearbyPendingRegionId(interactorPlayer, ownerPlayer, wid, x, z, mergeDistance);
        if (nearbyId.isPresent()) {
            expandRegionAndIncrement(nearbyId.get(), x, z, actionType);
        } else {
            insertRegion(interactorPlayer, ownerPlayer, wid, x, z, actionType);
        }
    }

    /*
     * If we always persist corner_a as min and corner_b as max (expand already
     * does),
     * the MIN/MAX below are redundant — compare against a/b directly.
     * Keep that invariant and this query can drop the extra math.
     */
    private Optional<Long> findNearbyPendingRegionId(
            String interactorPlayer,
            String ownerPlayer,
            int wid,
            int x,
            int z,
            int mergeDistance) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT id
                FROM interaction_counts
                WHERE interactor_player = ?
                  AND owner = ?
                  AND wid = ?
                  AND notification_id IS NULL
                  AND region_corner_a_x IS NOT NULL
                  AND region_corner_a_z IS NOT NULL
                  AND region_corner_b_x IS NOT NULL
                  AND region_corner_b_z IS NOT NULL
                  AND ? BETWEEN MIN(region_corner_a_x, region_corner_b_x) - ?
                            AND MAX(region_corner_a_x, region_corner_b_x) + ?
                  AND ? BETWEEN MIN(region_corner_a_z, region_corner_b_z) - ?
                            AND MAX(region_corner_a_z, region_corner_b_z) + ?
                ORDER BY updated_at DESC
                LIMIT 1
                """)) {
            statement.setString(1, interactorPlayer);
            statement.setString(2, ownerPlayer);
            statement.setInt(3, wid);
            statement.setInt(4, x);
            statement.setInt(5, mergeDistance);
            statement.setInt(6, mergeDistance);
            statement.setInt(7, z);
            statement.setInt(8, mergeDistance);
            statement.setInt(9, mergeDistance);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    return Optional.of(rows.getLong("id"));
                }
            }
        }
        return Optional.empty();
    }

    private void expandRegionAndIncrement(long id, int x, int z, SuspiciousActionType actionType) throws SQLException {
        long now = System.currentTimeMillis();
        int breakDelta = actionType == SuspiciousActionType.BREAK_BLOCK ? 1 : 0;
        int placeDelta = actionType == SuspiciousActionType.PLACE_BLOCK ? 1 : 0;
        int containerDelta = actionType == SuspiciousActionType.INTERACT_CONTAINER ? 1 : 0;

        int minX;
        int minZ;
        int maxX;
        int maxZ;
        try (var statement = connection.prepareStatement("""
                SELECT region_corner_a_x, region_corner_a_z, region_corner_b_x, region_corner_b_z
                FROM interaction_counts
                WHERE id = ?
                """)) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return;
                }
                minX = Math.min(
                        rows.getInt("region_corner_a_x"),
                        rows.getInt("region_corner_b_x"));
                maxX = Math.max(
                        rows.getInt("region_corner_a_x"),
                        rows.getInt("region_corner_b_x"));

                minZ = Math.min(
                        rows.getInt("region_corner_a_z"),
                        rows.getInt("region_corner_b_z"));

                maxZ = Math.max(
                        rows.getInt("region_corner_a_z"),
                        rows.getInt("region_corner_b_z"));
            }
        }

        minX = Math.min(minX, x);
        maxX = Math.max(maxX, x);

        minZ = Math.min(minZ, z);
        maxZ = Math.max(maxZ, z);

        try (var statement = connection.prepareStatement("""
                UPDATE interaction_counts
                SET count_break_blocks = count_break_blocks + ?,
                    count_placed_blocks = count_placed_blocks + ?,
                    count_interact_containers = count_interact_containers + ?,
                    region_corner_a_x = ?,
                    region_corner_a_z = ?,
                    region_corner_b_x = ?,
                    region_corner_b_z = ?,
                    updated_at = ?
                WHERE id = ?
                """)) {
            statement.setInt(1, breakDelta);
            statement.setInt(2, placeDelta);
            statement.setInt(3, containerDelta);
            statement.setInt(4, minX);
            statement.setInt(5, minZ);
            statement.setInt(6, maxX);
            statement.setInt(7, maxZ);
            statement.setLong(8, now);
            statement.setLong(9, id);
            statement.executeUpdate();
        }
    }

    private void insertRegion(
            String interactorPlayer,
            String ownerPlayer,
            int wid,
            int x,
            int z,
            SuspiciousActionType actionType) throws SQLException {
        long now = System.currentTimeMillis();
        try (var statement = connection.prepareStatement("""
                INSERT INTO interaction_counts(
                  interactor_player,
                  owner,
                  count_break_blocks,
                  count_placed_blocks,
                  count_interact_containers,
                  notification_id,
                  wid,
                  region_corner_a_x,
                  region_corner_a_z,
                  region_corner_b_x,
                  region_corner_b_z,
                  created_at,
                  updated_at
                ) VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, interactorPlayer);
            statement.setString(2, ownerPlayer);
            statement.setInt(3, actionType == SuspiciousActionType.BREAK_BLOCK ? 1 : 0);
            statement.setInt(4, actionType == SuspiciousActionType.PLACE_BLOCK ? 1 : 0);
            statement.setInt(5, actionType == SuspiciousActionType.INTERACT_CONTAINER ? 1 : 0);
            statement.setInt(6, wid);
            statement.setInt(7, x);
            statement.setInt(8, z);
            statement.setInt(9, x);
            statement.setInt(10, z);
            statement.setLong(11, now);
            statement.setLong(12, now);
            statement.executeUpdate();
        }
    }

    public synchronized List<String> findInteractorsReadyForNotification(long olderThanMillis) throws SQLException {
        long threshold = System.currentTimeMillis() - olderThanMillis;
        var result = new ArrayList<String>();
        try (var statement = connection.prepareStatement("""
                SELECT DISTINCT interactor_player
                FROM interaction_counts
                WHERE notification_id IS NULL
                  AND updated_at <= ?
                ORDER BY updated_at ASC
                """)) {
            statement.setLong(1, threshold);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getString("interactor_player"));
                }
            }
        }
        return result;
    }

    public synchronized List<InteractionCount> findPendingByInteractor(String interactorPlayer) throws SQLException {
        var result = new ArrayList<InteractionCount>();
        try (var statement = connection.prepareStatement(PENDING_SELECT + """
                WHERE interactor_player = ?
                  AND notification_id IS NULL
                ORDER BY owner ASC, id ASC
                """)) {
            statement.setString(1, interactorPlayer);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(mapRow(rows));
                }
            }
        }
        return result;
    }

    public synchronized boolean exists(String interactorPlayer, String ownerPlayer) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1
                FROM interaction_counts
                WHERE interactor_player = ?
                  AND owner = ?
                """)) {
            statement.setString(1, interactorPlayer);
            statement.setString(2, ownerPlayer);
            try (var rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    public synchronized List<InteractionCount> findPendingForOwner(String ownerPlayer) throws SQLException {
        var result = new ArrayList<InteractionCount>();
        try (var statement = connection.prepareStatement(PENDING_SELECT + """
                WHERE owner = ?
                  AND notification_id IS NULL
                ORDER BY updated_at ASC, id ASC
                """)) {
            statement.setString(1, ownerPlayer);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(mapRow(rows));
                }
            }
        }
        return result;
    }

    public synchronized void attachNotification(
            String interactorPlayer,
            List<String> ownerPlayers,
            long notificationId) throws SQLException {
        if (ownerPlayers.isEmpty()) {
            return;
        }

        String placeholders = String.join(", ", Collections.nCopies(ownerPlayers.size(), "?"));
        try (var statement = connection.prepareStatement("""
                UPDATE interaction_counts
                SET notification_id = ?
                WHERE interactor_player = ?
                  AND notification_id IS NULL
                  AND owner IN (%s)
                """.formatted(placeholders))) {
            statement.setLong(1, notificationId);
            statement.setString(2, interactorPlayer);
            for (int i = 0; i < ownerPlayers.size(); i++) {
                statement.setString(i + 3, ownerPlayers.get(i));
            }
            statement.executeUpdate();
        }
    }

    public synchronized void attachNotificationByIds(List<Long> ids, long notificationId) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }

        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        try (var statement = connection.prepareStatement("""
                UPDATE interaction_counts
                SET notification_id = ?
                WHERE notification_id IS NULL
                  AND id IN (%s)
                """.formatted(placeholders))) {
            statement.setLong(1, notificationId);
            for (int i = 0; i < ids.size(); i++) {
                statement.setLong(i + 2, ids.get(i));
            }
            statement.executeUpdate();
        }
    }

    private InteractionCount mapRow(ResultSet rows) throws SQLException {
        return new InteractionCount(
                rows.getLong("id"),
                rows.getString("interactor_player"),
                rows.getString("owner"),
                rows.getInt("count_break_blocks"),
                rows.getInt("count_placed_blocks"),
                rows.getInt("count_interact_containers"),
                getNullableInt(rows, "wid"),
                getNullableInt(rows, "region_corner_a_x"),
                getNullableInt(rows, "region_corner_a_z"),
                getNullableInt(rows, "region_corner_b_x"),
                getNullableInt(rows, "region_corner_b_z"));
    }

    private Integer getNullableInt(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }
}
