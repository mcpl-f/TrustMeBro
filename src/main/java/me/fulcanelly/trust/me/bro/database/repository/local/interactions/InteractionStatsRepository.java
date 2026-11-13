package me.fulcanelly.trust.me.bro.database.repository.local.interactions;

import java.sql.SQLException;
import java.util.List;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.SqlResources;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousPlayerStat;

@RequiredArgsConstructor
public final class InteractionStatsRepository {

    private final SqliteJdbi database;

    public int countUndecidedInteractors(String ownerPlayer) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(SqlResources.load("interactions/stats/count-undecided"))
                .bind("owner", ownerPlayer)
                .mapTo(Integer.class)
                .one());
    }

    /**
     * @return how many {@code interaction_counts} rows the pair has (one per legacy
     *         row and per activity box), not the sum of their counters
     */
    public int countInteractions(String interactorPlayer, String ownerPlayer) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(
                "SELECT COUNT(*) FROM interaction_counts WHERE interactor_player = :interactor AND owner = :owner")
                .bind("interactor", interactorPlayer)
                .bind("owner", ownerPlayer)
                .mapTo(Integer.class)
                .one());
    }

    public List<String> findUndecidedInteractors(String ownerPlayer, int limit, int offset) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(SqlResources.load("interactions/stats/find-undecided"))
                .bind("owner", ownerPlayer)
                .bind("limit", limit)
                .bind("offset", offset)
                .mapTo(String.class)
                .list());
    }

    public List<SuspiciousPlayerStat> findTopSuspicious(int limit) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(
                SqlResources.load("interactions/stats/find-top-suspicious"))
                .bind("limit", limit)
                .map((row, context) -> new SuspiciousPlayerStat(
                        row.getString("interactor_player"),
                        row.getLong("interactions_sum"),
                        row.getInt("owners_count")))
                .list());
    }
}
