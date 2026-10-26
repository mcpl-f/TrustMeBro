package me.fulcanelly.trust.me.bro.database.repository.local.reports;

import java.sql.SQLException;
import java.util.List;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.SqlResources;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;
import me.fulcanelly.trust.me.bro.database.repository.model.PeopleAggregateStat;

@RequiredArgsConstructor
public final class ReportStatsRepository {

    private final SqliteJdbi database;

    /** Players with the most reports. Counts only — no name concat. */
    public List<PeopleAggregateStat> findTopReported(int limit) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(SqlResources.load("reports/find-top-reported"))
                .bind("limit", limit)
                .map((row, context) -> new PeopleAggregateStat(
                        row.getString("interactor_player"),
                        row.getInt("people_count")))
                .list());
    }
}
