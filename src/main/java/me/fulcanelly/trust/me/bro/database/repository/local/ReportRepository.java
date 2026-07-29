package me.fulcanelly.trust.me.bro.database.repository.local;

import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import me.fulcanelly.trust.me.bro.database.repository.model.PeopleAggregateStat;

@RequiredArgsConstructor
public final class ReportRepository {

    private final Connection connection;

    public boolean exists(String interactorPlayer, String ownerPlayer) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1
                FROM reports
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

    public void report(String reporterPlayer, Long reporterTelegramUserId, String interactorPlayer, String ownerPlayer)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO reports(reported_by_mc_name, reported_by_telegram_user_id, interactor_player, owner, created_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, reporterPlayer);
            if (reporterTelegramUserId == null) {
                statement.setObject(2, null);
            } else {
                statement.setLong(2, reporterTelegramUserId);
            }
            statement.setString(3, interactorPlayer);
            statement.setString(4, ownerPlayer);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public void delete(String interactorPlayer, String ownerPlayer) throws SQLException {
        try (var statement = connection.prepareStatement("""
                DELETE FROM reports
                WHERE interactor_player = ?
                  AND owner = ?
                """)) {
            statement.setString(1, interactorPlayer);
            statement.setString(2, ownerPlayer);
            statement.executeUpdate();
        }
    }

    /** Players with the most reports. Counts only — no name concat. */
    public List<PeopleAggregateStat> findTopReported(int limit) throws SQLException {
        var result = new ArrayList<PeopleAggregateStat>();
        try (var statement = connection.prepareStatement("""
                SELECT interactor_player,

                       COUNT(*) AS people_count

                FROM reports
                GROUP BY interactor_player
                ORDER BY people_count DESC, interactor_player ASC
                LIMIT ?
                """)) {
            statement.setInt(1, limit);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new PeopleAggregateStat(
                            rows.getString("interactor_player"),
                            rows.getInt("people_count")));
                }
            }
        }
        return result;
    }

    /** Owners who reported {@code interactorPlayer}, capped by {@code limit}. */
    public List<String> findReporters(String interactorPlayer, int limit) throws SQLException {
        var result = new ArrayList<String>();
        try (var statement = connection.prepareStatement("""
                SELECT owner
                FROM reports
                WHERE interactor_player = ?
                ORDER BY created_at ASC, owner ASC
                LIMIT ?
                """)) {
            statement.setString(1, interactorPlayer);
            statement.setInt(2, limit);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getString("owner"));
                }
            }
        }
        return result;
    }
}
