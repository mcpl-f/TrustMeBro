package me.fulcanelly.trust.me.bro.database.repository.local.notifications;

import java.sql.SQLException;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;

@RequiredArgsConstructor
public final class TelegramNotificationPreferencesRepository {

    private final SqliteJdbi database;

    /** DMs are enabled by default; only explicit opt-outs are persisted. */
    public boolean isEnabled(String ownerPlayer) throws SQLException {
        return database.withHandle(handle -> handle.createQuery("""
                SELECT enabled
                FROM telegram_notification_preferences
                WHERE owner_mc_name = :owner
                """)
                .bind("owner", ownerPlayer)
                .mapTo(Integer.class)
                .findOne()
                .orElse(1) != 0);
    }

    public void setEnabled(String ownerPlayer, boolean enabled) throws SQLException {
        int enabledValue = 0;
        if (enabled) {
            enabledValue = 1;
        }
        database.withHandle(handle -> handle.createUpdate("""
                INSERT INTO telegram_notification_preferences(owner_mc_name, enabled, updated_at)
                VALUES (:owner, :enabled, :updatedAt)
                ON CONFLICT(owner_mc_name) DO UPDATE SET
                    enabled = excluded.enabled,
                    updated_at = excluded.updated_at
                """)
                .bind("owner", ownerPlayer)
                .bind("enabled", enabledValue)
                .bind("updatedAt", System.currentTimeMillis())
                .execute());
    }
}
