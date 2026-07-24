package me.fulcanelly.trust.me.bro.database.repository.local;

import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;

@RequiredArgsConstructor
public final class InteractionCountsRepository {

    private final Connection connection;

    public synchronized void increment(String interactorPlayer, String ownerPlayer, SuspiciousActionType actionType)
            throws SQLException {
        long now = System.currentTimeMillis();
        String breakDelta = actionType == SuspiciousActionType.BREAK_BLOCK ? "1" : "0";
        String placeDelta = actionType == SuspiciousActionType.PLACE_BLOCK ? "1" : "0";
        String containerDelta = actionType == SuspiciousActionType.INTERACT_CONTAINER ? "1" : "0";

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
                ON CONFLICT(interactor_player, owner) DO UPDATE SET
                  count_break_blocks = count_break_blocks + %s,
                  count_placed_blocks = count_placed_blocks + %s,
                  count_interact_containers = count_interact_containers + %s,
                  updated_at = excluded.updated_at
                """.formatted(breakDelta, placeDelta, containerDelta))) {
            statement.setString(1, interactorPlayer);
            statement.setString(2, ownerPlayer);
            statement.setInt(3, actionType == SuspiciousActionType.BREAK_BLOCK ? 1 : 0);
            statement.setInt(4, actionType == SuspiciousActionType.PLACE_BLOCK ? 1 : 0);
            statement.setInt(5, actionType == SuspiciousActionType.INTERACT_CONTAINER ? 1 : 0);
            statement.setLong(6, now);
            statement.setLong(7, now);
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
        try (var statement = connection.prepareStatement("""
                SELECT interactor_player, owner, count_break_blocks, count_placed_blocks, count_interact_containers
                FROM interaction_counts
                WHERE interactor_player = ?
                  AND notification_id IS NULL
                ORDER BY owner ASC
                """)) {
            statement.setString(1, interactorPlayer);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new InteractionCount(
                            rows.getString("interactor_player"),
                            rows.getString("owner"),
                            rows.getInt("count_break_blocks"),
                            rows.getInt("count_placed_blocks"),
                            rows.getInt("count_interact_containers")));
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
        try (var statement = connection.prepareStatement("""
                SELECT interactor_player, owner, count_break_blocks, count_placed_blocks, count_interact_containers
                FROM interaction_counts
                WHERE owner = ?
                  AND notification_id IS NULL
                ORDER BY updated_at ASC
                """)) {
            statement.setString(1, ownerPlayer);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new InteractionCount(
                            rows.getString("interactor_player"),
                            rows.getString("owner"),
                            rows.getInt("count_break_blocks"),
                            rows.getInt("count_placed_blocks"),
                            rows.getInt("count_interact_containers")));
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
}
