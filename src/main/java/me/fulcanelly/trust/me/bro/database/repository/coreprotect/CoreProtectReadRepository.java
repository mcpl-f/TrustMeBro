package me.fulcanelly.trust.me.bro.database.repository.coreprotect;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.sqlite.SQLiteConfig;

import me.fulcanelly.trust.me.bro.database.SqlResources;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;

public final class CoreProtectReadRepository {

    private final File databaseFile;
    private final Supplier<Set<String>> bannedOwners;

    public CoreProtectReadRepository(File databaseFile, Supplier<Set<String>> bannedOwners) {
        this.databaseFile = databaseFile;
        this.bannedOwners = bannedOwners;
    }

    public boolean isAvailable() {
        return databaseFile != null;
    }

    public Set<String> findBlockOwners(Location location) throws SQLException {
        return findOwners(location, "coreprotect/find-block-owners");
    }

    public Set<String> findContainerOwners(Location location) throws SQLException {
        return findOwners(location, "coreprotect/find-container-owners");
    }

    private Set<String> findOwners(Location location, String sqlResource) throws SQLException {
        var result = new LinkedHashSet<String>();
        Integer worldId = findWorldId(location.getWorld().getName());
        if (worldId == null) {
            return result;
        }

        try (var connection = openReadOnlyConnection()) {
            var users = new SqliteJdbi(connection).withHandle(handle -> handle
                    .createQuery(SqlResources.load(sqlResource))
                    .bind("worldId", worldId)
                    .bind("x", location.getBlockX())
                    .bind("y", location.getBlockY())
                    .bind("z", location.getBlockZ())
                    .mapTo(String.class)
                    .list());
            for (String user : users) {
                if (!user.isBlank() && !isBanned(user)) {
                    result.add(user);
                }
            }
        }
        return result;
    }

    private boolean isBanned(String user) {
        return bannedOwners.get().contains(user);
    }

    public Integer findWorldId(String worldName) throws SQLException {
        if (!isAvailable()) {
            return null;
        }
        try (var connection = openReadOnlyConnection()) {
            return new SqliteJdbi(connection).withHandle(handle -> handle
                    .createQuery("SELECT id FROM co_world WHERE world = :world")
                    .bind("world", worldName)
                    .mapTo(Integer.class)
                    .findOne()
                    .orElse(null));
        }
    }

    public String findWorldName(int wid) throws SQLException {
        if (!isAvailable()) {
            return null;
        }
        try (var connection = openReadOnlyConnection()) {
            return new SqliteJdbi(connection).withHandle(handle -> handle
                    .createQuery("SELECT world FROM co_world WHERE id = :worldId")
                    .bind("worldId", wid)
                    .mapTo(String.class)
                    .findOne()
                    .orElse(null));
        }
    }

    private Connection openReadOnlyConnection() throws SQLException {
        var config = new SQLiteConfig();
        config.setReadOnly(true);
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath(), config.toProperties());
    }
}
