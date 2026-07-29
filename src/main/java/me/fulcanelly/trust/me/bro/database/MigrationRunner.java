package me.fulcanelly.trust.me.bro.database;

import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Applies numbered SQLite migrations and records them in {@code schema_migrations}.
 *
 * <p>Empty {@code schema_migrations} on a DB that already has tables means the pre-versioned
 * baseline is already present — it is marked as version {@code 0} without recreating tables.
 */
@RequiredArgsConstructor
public final class MigrationRunner {

    private final Connection connection;

    public void migrate() throws SQLException {
        execute("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                  version INTEGER PRIMARY KEY,
                  applied_at INTEGER NOT NULL
                )
                """);

        Set<Integer> applied = loadAppliedVersions();
        if (applied.isEmpty() && tableExists("interaction_counts")) {
            markApplied(0);
            applied.add(0);
        }

        if (!applied.contains(0)) {
            applyBaseline();
            markApplied(0);
        }
        if (!applied.contains(1)) {
            applyMigration01Regions();
            markApplied(1);
        }
        if (!applied.contains(2)) {
            applyMigration02NamedRegions();
            markApplied(2);
        }
        if (!applied.contains(3)) {
            applyMigration03SkipReason();
            markApplied(3);
        }
        if (!applied.contains(4)) {
            applyMigration04SuggestedRegions();
            markApplied(4);
        }
    }

    private void applyBaseline() throws SQLException {
        execute("""
                CREATE TABLE IF NOT EXISTS trust_edges (

                  owner_mc_name TEXT NOT NULL,
                  trusted_mc_name TEXT NOT NULL,

                  created_at INTEGER NOT NULL,
                  created_by_telegram_user_id INTEGER,

                  PRIMARY KEY (owner_mc_name, trusted_mc_name)
                )
                """);
        execute("""
                CREATE TABLE IF NOT EXISTS sent_notifications (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,

                  sent_type TEXT NOT NULL,

                  interactor_player TEXT NOT NULL,

                  telegram_chat_id INTEGER,
                  telegram_message_id INTEGER,
                  created_at INTEGER NOT NULL
                )
                """);
        execute("""
                CREATE TABLE IF NOT EXISTS interaction_counts (

                  interactor_player TEXT NOT NULL,
                  owner TEXT NOT NULL,

                  count_break_blocks INTEGER NOT NULL DEFAULT 0,
                  count_placed_blocks INTEGER NOT NULL DEFAULT 0,
                  count_interact_containers INTEGER NOT NULL DEFAULT 0,

                  notification_id INTEGER,

                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL,

                  PRIMARY KEY (interactor_player, owner),
                  FOREIGN KEY (notification_id) REFERENCES sent_notifications(id)
                )
                """);
        execute("""
                CREATE TABLE IF NOT EXISTS reports (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,

                  reported_by_mc_name TEXT NOT NULL,
                  reported_by_telegram_user_id INTEGER,

                  interactor_player TEXT NOT NULL,
                  owner TEXT NOT NULL,

                  created_at INTEGER NOT NULL,

                  UNIQUE (interactor_player, owner)
                )
                """);
        execute("""
                CREATE TABLE IF NOT EXISTS telegram_callbacks (
                  token TEXT PRIMARY KEY,
                  action TEXT NOT NULL,

                  interactor_player TEXT NOT NULL,

                  created_at INTEGER NOT NULL
                )
                """);
    }

    /**
     * Adds world/region columns and allows multiple region rows per (interactor, owner).
     * Legacy rows keep {@code wid} NULL and stay unique via a partial index.
     */
    private void applyMigration01Regions() throws SQLException {
        execute("""
                CREATE TABLE interaction_counts_v1 (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,

                  interactor_player TEXT NOT NULL,
                  owner TEXT NOT NULL,

                  count_break_blocks INTEGER NOT NULL DEFAULT 0,
                  count_placed_blocks INTEGER NOT NULL DEFAULT 0,
                  count_interact_containers INTEGER NOT NULL DEFAULT 0,

                  notification_id INTEGER,

                  wid INTEGER,

                  region_corner_a_x INTEGER,
                  region_corner_a_z INTEGER,

                  region_corner_b_x INTEGER,
                  region_corner_b_z INTEGER,

                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL,

                  FOREIGN KEY (notification_id) REFERENCES sent_notifications(id)
                )
                """);
        execute("""
                INSERT INTO interaction_counts_v1 (
                  interactor_player,
                  owner,
                  count_break_blocks,
                  count_placed_blocks,
                  count_interact_containers,
                  notification_id,
                  created_at,
                  updated_at
                )
                SELECT
                  interactor_player,
                  owner,
                  count_break_blocks,
                  count_placed_blocks,
                  count_interact_containers,
                  notification_id,
                  created_at,
                  updated_at
                FROM interaction_counts
                """);
        execute("DROP TABLE interaction_counts");
        execute("ALTER TABLE interaction_counts_v1 RENAME TO interaction_counts");
        execute("""
                CREATE UNIQUE INDEX interaction_counts_legacy_uq
                ON interaction_counts(interactor_player, owner)
                WHERE wid IS NULL
                """);
    }

    /**
     * Named admin-defined trust regions (center + radius), used in Telegram labels.
     */
    private void applyMigration02NamedRegions() throws SQLException {
        execute("""
                CREATE TABLE IF NOT EXISTS regions (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,

                  name TEXT NOT NULL,

                  mutable INTEGER NOT NULL DEFAULT 1,

                  created_by_mc_name TEXT NOT NULL,
                  created_at INTEGER NOT NULL,

                  wid INTEGER NOT NULL,
                  center_x INTEGER NOT NULL,
                  center_z INTEGER NOT NULL,
                  radius INTEGER NOT NULL
                )
                """);
    }

    /**
     * Why a pending interaction was not sent to Telegram (e.g. no linked owner).
     * Rows with skip_reason set are no longer treated as pending for notifications.
     */
    private void applyMigration03SkipReason() throws SQLException {
        execute("""
                ALTER TABLE interaction_counts
                ADD COLUMN skip_reason TEXT
                """);
    }

    /**
     * Review queue for uncovered activity clusters (path toward named regions).
     */
    private void applyMigration04SuggestedRegions() throws SQLException {
        execute("""
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
        execute("""
                CREATE TABLE IF NOT EXISTS suggested_region_sources (
                  suggested_region_id INTEGER NOT NULL,
                  interaction_count_id INTEGER NOT NULL,

                  PRIMARY KEY (suggested_region_id, interaction_count_id),
                  FOREIGN KEY (suggested_region_id) REFERENCES suggested_regions(id),
                  FOREIGN KEY (interaction_count_id) REFERENCES interaction_counts(id)
                )
                """);
        execute("""
                CREATE TABLE IF NOT EXISTS suggested_region_declines (
                  suggested_region_id INTEGER PRIMARY KEY,

                  declined_by_mc_name TEXT NOT NULL,
                  created_at INTEGER NOT NULL,

                  FOREIGN KEY (suggested_region_id) REFERENCES suggested_regions(id)
                )
                """);
        execute("""
                ALTER TABLE regions
                ADD COLUMN based_on_suggested_region_id INTEGER
                  REFERENCES suggested_regions(id)
                """);
    }

    private Set<Integer> loadAppliedVersions() throws SQLException {
        var versions = new HashSet<Integer>();
        try (var statement = connection.prepareStatement("SELECT version FROM schema_migrations");
                var rows = statement.executeQuery()) {
            while (rows.next()) {
                versions.add(rows.getInt("version"));
            }
        }
        return versions;
    }

    private void markApplied(int version) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO schema_migrations(version, applied_at) VALUES (?, ?)
                """)) {
            statement.setInt(1, version);
            statement.setLong(2, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?
                """)) {
            statement.setString(1, tableName);
            try (var rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
