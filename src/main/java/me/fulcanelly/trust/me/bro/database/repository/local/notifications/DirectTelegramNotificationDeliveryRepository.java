package me.fulcanelly.trust.me.bro.database.repository.local.notifications;

import java.sql.SQLException;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;

@RequiredArgsConstructor
public final class DirectTelegramNotificationDeliveryRepository {

    private final SqliteJdbi database;

    public boolean hasDelivery(long interactionCountId) throws SQLException {
        return database.withHandle(handle -> handle.createQuery("""
                SELECT 1 FROM direct_telegram_notification_deliveries
                WHERE interaction_count_id = :interactionCountId
                """)
                .bind("interactionCountId", interactionCountId)
                .mapTo(Integer.class)
                .findOne()
                .isPresent());
    }

    public void markDelivered(
            long interactionCountId,
            String ownerPlayer,
            String interactorPlayer //
    ) throws SQLException {
        database.withHandle(handle -> handle.createUpdate("""
                INSERT OR IGNORE INTO direct_telegram_notification_deliveries(
                    interaction_count_id, owner_mc_name, interactor_player, delivered_at
                ) VALUES (:interactionCountId, :owner, :interactor, :deliveredAt)
                """)
                .bind("interactionCountId", interactionCountId)
                .bind("owner", ownerPlayer)
                .bind("interactor", interactorPlayer)
                .bind("deliveredAt", System.currentTimeMillis())
                .execute());
    }
}
