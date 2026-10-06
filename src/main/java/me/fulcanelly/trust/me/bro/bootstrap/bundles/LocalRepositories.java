package me.fulcanelly.trust.me.bro.bootstrap.bundles;

import java.sql.Connection;

import lombok.Value;
import me.fulcanelly.trust.me.bro.database.repository.local.InteractionCountsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.InteractionMaterialCountsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.NotificationRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.RegionsRepository;
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
public class LocalRepositories {

    TrustRepository trust;
    InteractionCountsRepository interactionCounts;
    InteractionMaterialCountsRepository interactionMaterials;
    NotificationRepository notifications;
    ReportRepository reports;
    RegionsRepository regions;

    public static LocalRepositories buildFromConnection(Connection connection) {
        return new LocalRepositories(
                new TrustRepository(connection),
                new InteractionCountsRepository(connection),
                new InteractionMaterialCountsRepository(connection),
                new NotificationRepository(connection),
                new ReportRepository(connection),
                new RegionsRepository(connection));
    }
}
