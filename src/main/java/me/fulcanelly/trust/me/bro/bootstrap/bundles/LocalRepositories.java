package me.fulcanelly.trust.me.bro.bootstrap.bundles;

import java.sql.Connection;

import lombok.Value;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;
import me.fulcanelly.trust.me.bro.database.repository.local.interactions.InteractionMaterialCountsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.interactions.InteractionStatsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.interactions.InteractionWriteRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.interactions.PendingInteractionsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.notifications.NotificationRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.regions.RegionsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.reports.ReportRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.reports.ReportStatsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.trust.TrustRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.trust.TrustStatsRepository;

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
    TrustStatsRepository trustStats;
    InteractionWriteRepository interactionWrites;
    PendingInteractionsRepository pendingInteractions;
    InteractionStatsRepository interactionStats;
    InteractionMaterialCountsRepository interactionMaterials;
    NotificationRepository notifications;
    ReportRepository reports;
    ReportStatsRepository reportStats;
    RegionsRepository regions;

    public static LocalRepositories buildFromConnection(Connection connection) {
        SqliteJdbi database = new SqliteJdbi(connection);
        return new LocalRepositories(
                new TrustRepository(database),
                new TrustStatsRepository(database),
                new InteractionWriteRepository(database),
                new PendingInteractionsRepository(database),
                new InteractionStatsRepository(database),
                new InteractionMaterialCountsRepository(database),
                new NotificationRepository(database),
                new ReportRepository(database),
                new ReportStatsRepository(database),
                new RegionsRepository(database));
    }
}
