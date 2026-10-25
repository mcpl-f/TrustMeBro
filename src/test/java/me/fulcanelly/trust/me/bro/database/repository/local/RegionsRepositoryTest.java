package me.fulcanelly.trust.me.bro.database.repository.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.fulcanelly.trust.me.bro.database.MigrationRunner;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;

public class RegionsRepositoryTest {

    private Connection connection;
    private RegionsRepository regions;

    @BeforeEach
    void openFreshDatabase() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        new MigrationRunner(connection).migrate();
        regions = new RegionsRepository(new SqliteJdbi(connection));
    }

    @AfterEach
    void closeDatabase() throws Exception {
        connection.close();
    }

    /*
     * Named regions are persisted and loaded as domain rows.
     *
     *   insert -> generated id -> findAll returns the saved region
     */
    @Test
    void insertReturnsGeneratedIdAndFindAllMapsRows() throws Exception {
        long id = regions.insert("Home", true, "Alice", 2, 10, 20, 5);

        assertEquals(1, id);
        var saved = regions.findAll().get(0);
        assertEquals(id, saved.getId());
        assertEquals("Home", saved.getName());
        assertEquals("Alice", saved.getCreatedByMcName());
        assertEquals(2, saved.getWid());
        assertEquals(10, saved.getCenterX());
        assertEquals(20, saved.getCenterZ());
        assertEquals(5, saved.getRadius());
    }
}
