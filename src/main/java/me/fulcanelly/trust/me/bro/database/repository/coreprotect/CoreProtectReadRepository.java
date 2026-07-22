package me.fulcanelly.trust.me.bro.database.repository.coreprotect;

import lombok.RequiredArgsConstructor;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.Location;
import org.sqlite.SQLiteConfig;

@RequiredArgsConstructor
public final class CoreProtectReadRepository {

    private final File databaseFile;

    public boolean isAvailable() {
        return databaseFile != null;
    }

    public Set<String> findBlockOwners(Location location) throws SQLException {
        return findOwners(location, "co_block", "action IN (0, 1)");
    }

    public Set<String> findContainerOwners(Location location) throws SQLException {
        return findOwners(location, "co_container", "action IN (0, 1)");
    }

    private Set<String> findOwners(Location location, String tableName, String actionFilter) throws SQLException {
        var result = new LinkedHashSet<String>();
        Integer worldId = findWorldId(location.getWorld().getName());
        if (worldId == null) {
            return result;
        }

        String sql = """
                SELECT DISTINCT u.user
                FROM %s h
                JOIN co_user u ON u.id = h.user
                WHERE h.wid = ?
                  AND h.x = ?
                  AND h.y = ?
                  AND h.z = ?
                  AND h.%s
                  AND u.user IS NOT NULL
                  AND u.user NOT LIKE '#%%'
                """.formatted(tableName, actionFilter);

        try (var connection = openReadOnlyConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, worldId);
            statement.setInt(2, location.getBlockX());
            statement.setInt(3, location.getBlockY());
            statement.setInt(4, location.getBlockZ());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    String user = rows.getString("user");
                    if (user != null && !user.isBlank()) {
                        result.add(user);
                    }
                }
            }
        }
        return result;
    }

    private Integer findWorldId(String worldName) throws SQLException {
        try (var connection = openReadOnlyConnection();
                var statement = connection.prepareStatement("SELECT id FROM co_world WHERE world = ?")) {
            statement.setString(1, worldName);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    return rows.getInt("id");
                }
            }
        }
        return null;
    }

    private Connection openReadOnlyConnection() throws SQLException {
        var config = new SQLiteConfig();
        config.setReadOnly(true);
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath(), config.toProperties());
    }
}
