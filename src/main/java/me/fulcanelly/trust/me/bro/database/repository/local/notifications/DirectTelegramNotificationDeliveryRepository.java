package me.fulcanelly.trust.me.bro.database.repository.local.notifications;

import java.sql.SQLException;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;

@RequiredArgsConstructor
public final class DirectTelegramNotificationDeliveryRepository {

    private final SqliteJdbi database;

    public long findLastDeliveredAt(String ownerPlayer, String interactorPlayer) throws SQLException {
        return database.withHandle(handle -> handle.createQuery("""
                SELECT delivered_at
                FROM direct_telegram_notification_deliveries
                WHERE owner_mc_name = :owner AND interactor_player = :interactor
                """)
                .bind("owner", ownerPlayer)
                .bind("interactor", interactorPlayer)
                .mapTo(Long.class)
                .findOne()
                .orElse(0L));
    }

    public void markDelivered(String ownerPlayer, String interactorPlayer, long deliveredThrough) throws SQLException {
        database.withHandle(handle -> handle.createUpdate("""
                INSERT INTO direct_telegram_notification_deliveries(
                    owner_mc_name, interactor_player, delivered_at
                ) VALUES (:owner, :interactor, :deliveredAt)
                ON CONFLICT(owner_mc_name, interactor_player) DO UPDATE SET
                    delivered_at = MAX(delivered_at, excluded.delivered_at)
                """)
                .bind("owner", ownerPlayer)
                .bind("interactor", interactorPlayer)
                .bind("deliveredAt", deliveredThrough)
                .execute());
    }
}
