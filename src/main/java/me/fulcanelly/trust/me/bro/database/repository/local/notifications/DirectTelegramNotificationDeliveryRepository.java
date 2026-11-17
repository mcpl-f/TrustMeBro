package me.fulcanelly.trust.me.bro.database.repository.local.notifications;

import java.sql.SQLException;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;

@RequiredArgsConstructor
public final class DirectTelegramNotificationDeliveryRepository {

    private final SqliteJdbi database;

    public void markDelivered(String ownerPlayer, String interactorPlayer) throws SQLException {
        database.withHandle(handle -> handle.createUpdate("""
                INSERT OR IGNORE INTO direct_telegram_notification_deliveries(
                    owner_mc_name, interactor_player, delivered_at
                ) VALUES (:owner, :interactor, :deliveredAt)
                """)
                .bind("owner", ownerPlayer)
                .bind("interactor", interactorPlayer)
                .bind("deliveredAt", System.currentTimeMillis())
                .execute());
    }
}
