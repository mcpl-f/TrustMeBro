package me.fulcanelly.trust.me.bro.bootstrap;

import java.sql.Connection;

import lombok.Value;
import me.fulcanelly.trust.me.bro.database.repository.local.InteractionCountsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.NotificationRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.ReportRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.TrustRepository;

/**
 * Groups repositories that share the local TrustMeBro SQLite connection.
 *
 * The bootstrap should pass one named bundle instead of four separate
 * repositories through every registrar and service factory.
 * This keeps local persistence wiring explicit and easy to scan.
 */
@Value
final class LocalRepositories {

    TrustRepository trust;
    InteractionCountsRepository interactionCounts;
    NotificationRepository notifications;
    ReportRepository reports;

    static LocalRepositories buildFromConnection(Connection connection) {
        return new LocalRepositories(
                new TrustRepository(connection),
                new InteractionCountsRepository(connection),
                new NotificationRepository(connection),
                new ReportRepository(connection));
    }
}
