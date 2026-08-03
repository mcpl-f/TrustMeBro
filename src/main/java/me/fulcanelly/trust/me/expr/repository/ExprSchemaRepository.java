package me.fulcanelly.trust.me.expr.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import lombok.RequiredArgsConstructor;

/**
 * DDL for the offline experiment on a DB copy: create suggested tables, drop
 * legacy center/radius/weight cols, optional {@code regions.based_on_*}.
 * Declines / based_on unused by discovery — defer when shrinking ({@code expr.md}).
 */
@RequiredArgsConstructor
public final class ExprSchemaRepository {

    private final Connection connection;

    public void ensureSuggestedRegionTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS suggested_regions (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,

                      wid INTEGER NOT NULL,

                      min_x INTEGER NOT NULL,
                      min_z INTEGER NOT NULL,
                      max_x INTEGER NOT NULL,
                      max_z INTEGER NOT NULL,

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
        dropColumnIfExists("suggested_regions", "center_x");
        dropColumnIfExists("suggested_regions", "center_z");
        dropColumnIfExists("suggested_regions", "radius");
        dropColumnIfExists("suggested_regions", "weight");
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

    private void dropColumnIfExists(String table, String column) throws SQLException {
        if (!columnExists(table, column)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
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
