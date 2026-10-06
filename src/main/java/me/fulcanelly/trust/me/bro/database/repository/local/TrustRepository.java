package me.fulcanelly.trust.me.bro.database.repository.local;

import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import me.fulcanelly.trust.me.bro.database.repository.model.PeopleAggregateStat;

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

    /** Players with the most trusters (owners who trust them). Counts only — no name concat. */
    public List<PeopleAggregateStat> findTopTrusted(int limit) throws SQLException {
        var result = new ArrayList<PeopleAggregateStat>();
        try (var statement = connection.prepareStatement("""
                SELECT trusted_mc_name,

                       COUNT(*) AS people_count

                FROM trust_edges
                GROUP BY trusted_mc_name
                ORDER BY people_count DESC, trusted_mc_name ASC
                LIMIT ?
                """)) {
            statement.setInt(1, limit);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new PeopleAggregateStat(
                            rows.getString("trusted_mc_name"),
                            rows.getInt("people_count")));
                }
            }
        }
        return result;
    }

    /**
     * Owners who trust {@code trustedPlayer} (trusters), capped by {@code limit}.
     *
     * <p>Not "trustees" — a trustee is the person being trusted; a truster grants trust.
     */
    public List<String> findTrusters(String trustedPlayer, int limit) throws SQLException {
        var result = new ArrayList<String>();
        try (var statement = connection.prepareStatement("""
                SELECT owner_mc_name
                FROM trust_edges
                WHERE trusted_mc_name = ?
                ORDER BY created_at ASC, owner_mc_name ASC
                LIMIT ?
                """)) {
            statement.setString(1, trustedPlayer);
            statement.setInt(2, limit);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getString("owner_mc_name"));
                }
            }
        }
        return result;
    }

    /** How many owners trust {@code trustedPlayer}. */
    public int countTrusters(String trustedPlayer) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM trust_edges WHERE trusted_mc_name = ?")) {
            statement.setString(1, trustedPlayer);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    /** How many players {@code ownerPlayer} trusts. */
    public int countTrusted(String ownerPlayer) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM trust_edges WHERE owner_mc_name = ?")) {
            statement.setString(1, ownerPlayer);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }
}
