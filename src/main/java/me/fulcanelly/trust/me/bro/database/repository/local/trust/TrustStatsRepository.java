package me.fulcanelly.trust.me.bro.database.repository.local.trust;

import java.sql.SQLException;
import java.util.List;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.SqlResources;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;
import me.fulcanelly.trust.me.bro.database.repository.model.PeopleAggregateStat;

@RequiredArgsConstructor
public final class TrustStatsRepository {

    private final SqliteJdbi database;

    /** Players with the most trusters (owners who trust them). Counts only — no name concat. */
    public List<PeopleAggregateStat> findTopTrusted(int limit) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(SqlResources.load("trust/find-top-trusted"))
                .bind("limit", limit)
                .map((row, context) -> new PeopleAggregateStat(
                        row.getString("trusted_mc_name"),
                        row.getInt("people_count")))
                .list());
    }
}
