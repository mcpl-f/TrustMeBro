package me.fulcanelly.trust.me.bro.database.repository.local;

import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.SQLException;

@RequiredArgsConstructor
public final class TrustRepository {

    private final Connection connection;

    public boolean isTrusted(String ownerPlayer, String trustedPlayer) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM trust_edges WHERE owner_mc_name = ? AND trusted_mc_name = ?")) {
            statement.setString(1, ownerPlayer);
            statement.setString(2, trustedPlayer);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public void trust(String ownerPlayer, String trustedPlayer, Long telegramUserId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO trust_edges(
                  owner_mc_name, trusted_mc_name, created_at, created_by_telegram_user_id
                ) VALUES (?, ?, ?, ?)
                """)) {
            statement.setString(1, ownerPlayer);
            statement.setString(2, trustedPlayer);
            statement.setLong(3, System.currentTimeMillis());
            if (telegramUserId == null) {
                statement.setObject(4, null);
            } else {
                statement.setLong(4, telegramUserId);
            }
            statement.executeUpdate();
        }
    }

    public void untrust(String ownerPlayer, String trustedPlayer) throws SQLException {
        try (var statement = connection.prepareStatement("""
                DELETE FROM trust_edges
                WHERE owner_mc_name = ?
                  AND trusted_mc_name = ?
                """)) {
            statement.setString(1, ownerPlayer);
            statement.setString(2, trustedPlayer);
            statement.executeUpdate();
        }
    }
}
