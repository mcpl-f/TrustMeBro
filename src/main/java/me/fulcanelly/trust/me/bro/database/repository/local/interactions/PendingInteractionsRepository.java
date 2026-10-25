package me.fulcanelly.trust.me.bro.database.repository.local.interactions;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jdbi.v3.core.statement.Query;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.config.MinInteractionThresholds;
import me.fulcanelly.trust.me.bro.database.SqlResources;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;

@RequiredArgsConstructor
public final class PendingInteractionsRepository {

    private static final InteractionCountMapper INTERACTION_COUNT_MAPPER = new InteractionCountMapper();

    private final SqliteJdbi database;

    public List<String> findInteractorsReadyForNotification(
            long olderThanMillis,
            MinInteractionThresholds minInteractions) throws SQLException {
        long updatedAtCutoff = System.currentTimeMillis() - olderThanMillis;
        return database.withHandle(handle -> bindThresholds(
                        handle.createQuery(SqlResources.load("interactions/pending/find-ready-for-notification"))
                                .bind("updatedAtCutoff", updatedAtCutoff),
                        minInteractions)
                .mapTo(String.class)
                .list());
    }

    public List<InteractionCount> findPendingByInteractor(
            String interactorPlayer,
            MinInteractionThresholds minInteractions) throws SQLException {
        return database.withHandle(handle -> bindThresholds(
                        handle.createQuery(SqlResources.load("interactions/pending/find-by-interactor"))
                                .bind("interactor", interactorPlayer),
                        minInteractions)
                .map(INTERACTION_COUNT_MAPPER)
                .list());
    }

    public List<InteractionCount> findPendingForOwner(String ownerPlayer) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(
                SqlResources.load("interactions/pending/find-for-owner"))
                .bind("owner", ownerPlayer)
                .map(INTERACTION_COUNT_MAPPER)
                .list());
    }

    public Optional<InteractionCount> findTopPendingInteractionForOwner(String ownerPlayer, String strategy)
            throws SQLException {
        boolean biggest = "biggest".equalsIgnoreCase(strategy);
        return database.withHandle(handle -> handle.createQuery(
                SqlResources.load("interactions/pending/find-top-for-owner"))
                .bind("owner", ownerPlayer)
                .bind("biggest", biggest)
                .map(INTERACTION_COUNT_MAPPER)
                .findOne());
    }

    public void markSkippedByIds(List<Long> ids, String skipReason) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }
        database.withHandle(handle -> handle.createUpdate(SqlResources.load("interactions/pending/mark-skipped"))
                .bind("skipReason", skipReason)
                .bindList("ids", ids)
                .execute());
    }

    public void attachNotification(String interactorPlayer, List<String> ownerPlayers, long notificationId)
            throws SQLException {
        if (ownerPlayers.isEmpty()) {
            return;
        }
        database.withHandle(handle -> handle.createUpdate(
                        SqlResources.load("interactions/pending/attach-notification"))
                .bind("notificationId", notificationId)
                .bind("interactor", interactorPlayer)
                .bindList("owners", ownerPlayers)
                .execute());
    }

    public void attachNotificationByIds(List<Long> ids, long notificationId) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }
        database.withHandle(handle -> handle.createUpdate(
                        SqlResources.load("interactions/pending/attach-notification-by-ids"))
                .bind("notificationId", notificationId)
                .bindList("ids", ids)
                .execute());
    }

    private Query bindThresholds(Query query, MinInteractionThresholds thresholds) {
        return query.bind("thresholdsEnabled", thresholds.isEnabled())
                .bind("placeCount", thresholds.getPlaceCount())
                .bind("breakCount", thresholds.getBreakCount())
                .bind("containerCount", thresholds.getChestInterCount());
    }
}
