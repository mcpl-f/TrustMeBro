package me.fulcanelly.trust.me.bro.database;

import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.SQLException;

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

                  created_at INTEGER NOT NULL
                )
                """);
        execute("""
                CREATE TABLE IF NOT EXISTS telegram_callbacks (
                  token TEXT PRIMARY KEY,
                  action TEXT NOT NULL,

                  interactor_player TEXT NOT NULL,
                  owner TEXT NOT NULL,

                  created_at INTEGER NOT NULL
                )
                """);
    }

    private void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
