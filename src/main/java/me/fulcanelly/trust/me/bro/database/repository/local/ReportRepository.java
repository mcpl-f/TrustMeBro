package me.fulcanelly.trust.me.bro.database.repository.local;

import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.SQLException;

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
}
