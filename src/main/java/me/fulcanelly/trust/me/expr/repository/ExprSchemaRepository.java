package me.fulcanelly.trust.me.expr.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import lombok.RequiredArgsConstructor;

/** Ensures suggested-region tables exist on a raw prod DB copy. */
@RequiredArgsConstructor
public final class ExprSchemaRepository {

    private final Connection connection;

    public void ensureSuggestedRegionTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS suggested_regions (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,

                      wid INTEGER NOT NULL,
                      center_x INTEGER NOT NULL,
                      center_z INTEGER NOT NULL,
                      radius INTEGER NOT NULL,

                      min_x INTEGER NOT NULL,
                      min_z INTEGER NOT NULL,
                      max_x INTEGER NOT NULL,
                      max_z INTEGER NOT NULL,

                      weight INTEGER NOT NULL DEFAULT 0,

                      status TEXT NOT NULL DEFAULT 'pending',

                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS suggested_region_sources (
                      suggested_region_id INTEGER NOT NULL,
                      interaction_count_id INTEGER NOT NULL,

                      PRIMARY KEY (suggested_region_id, interaction_count_id),
                      FOREIGN KEY (suggested_region_id) REFERENCES suggested_regions(id),
                      FOREIGN KEY (interaction_count_id) REFERENCES interaction_counts(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS suggested_region_declines (
                      suggested_region_id INTEGER PRIMARY KEY,

                      declined_by_mc_name TEXT NOT NULL,
                      created_at INTEGER NOT NULL,

                      FOREIGN KEY (suggested_region_id) REFERENCES suggested_regions(id)
                    )
                    """);
        }
        if (!columnExists("regions", "based_on_suggested_region_id")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE regions
                        ADD COLUMN based_on_suggested_region_id INTEGER
                          REFERENCES suggested_regions(id)
                        """);
            }
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pragma_table_info(?) WHERE name = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }
}
