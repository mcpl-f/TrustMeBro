package me.fulcanelly.trust.me.bro.database.repository.coreprotect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

public class CoreProtectReadRepositoryTest {

    /*
     * CoreProtect lookup reads exact-position records and filters system and banned owners.
     *
     *   location -> world id -> block/container owners -> remove system and banned names
     */
    @Test
    void findsOwnersFromCoreProtectSqliteDatabase() throws Exception {
        var databaseFile = Files.createTempFile("coreprotect-test", ".sqlite");
        try {
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
                    var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE co_world(id INTEGER, world TEXT)");
                statement.executeUpdate("CREATE TABLE co_user(id INTEGER, user TEXT)");
                statement.executeUpdate("""
                        CREATE TABLE co_block(
                            wid INTEGER, user INTEGER, x INTEGER, y INTEGER, z INTEGER, action INTEGER
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE co_container(
                            wid INTEGER, user INTEGER, x INTEGER, y INTEGER, z INTEGER, action INTEGER
                        )
                        """);
                statement.executeUpdate("INSERT INTO co_world VALUES (1, 'world')");
                statement.executeUpdate("INSERT INTO co_user VALUES (1, 'Alice'), (2, 'Blocked'), (3, '#system')");
                statement.executeUpdate("""
                        INSERT INTO co_block VALUES
                          (1, 1, 10, 20, 30, 0),
                          (1, 2, 10, 20, 30, 0),
                          (1, 3, 10, 20, 30, 0)
                        """);
                statement.executeUpdate("INSERT INTO co_container VALUES (1, 1, 10, 20, 30, 1)");
            }

            var repository = new CoreProtectReadRepository(databaseFile.toFile(), () -> Set.of("Blocked"));
            Location location = new Location(world("world"), 10, 20, 30);

            assertEquals(1, repository.findWorldId("world"));
            assertEquals("world", repository.findWorldName(1));
            assertEquals(Set.of("Alice"), repository.findBlockOwners(location));
            assertEquals(Set.of("Alice"), repository.findContainerOwners(location));
        } finally {
            Files.deleteIfExists(databaseFile);
        }
    }

    private World world(String name) {
        return (World) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { World.class },
                (proxy, method, args) -> method.getName().equals("getName") ? name : null);
    }
}
