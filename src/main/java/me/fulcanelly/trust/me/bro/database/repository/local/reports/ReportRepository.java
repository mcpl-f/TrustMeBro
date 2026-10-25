package me.fulcanelly.trust.me.bro.database.repository.local;

import java.sql.SQLException;
import java.util.List;

import me.fulcanelly.trust.me.bro.database.SqlResources;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;
import me.fulcanelly.trust.me.bro.database.repository.model.PeopleAggregateStat;

public final class ReportRepository {

    private final SqliteJdbi database;

    public ReportRepository(SqliteJdbi database) {
        this.database = database;
    }

    public boolean exists(String interactorPlayer, String ownerPlayer) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(
                        "SELECT 1 FROM reports WHERE interactor_player = :interactor AND owner = :owner")
                .bind("interactor", interactorPlayer)
                .bind("owner", ownerPlayer)
                .mapTo(Integer.class)
                .findOne()
                .isPresent());
    }

    public void report(String reporterPlayer, Long reporterTelegramUserId, String interactorPlayer, String ownerPlayer)
            throws SQLException {
        database.withHandle(handle -> handle.createUpdate(SqlResources.load("reports/insert"))
                .bind("reporter", reporterPlayer)
                .bind("telegramUserId", reporterTelegramUserId)
                .bind("interactor", interactorPlayer)
                .bind("owner", ownerPlayer)
                .bind("createdAt", System.currentTimeMillis())
                .execute());
    }

    public void delete(String interactorPlayer, String ownerPlayer) throws SQLException {
        database.withHandle(handle -> handle.createUpdate(
                        "DELETE FROM reports WHERE interactor_player = :interactor AND owner = :owner")
                .bind("interactor", interactorPlayer)
                .bind("owner", ownerPlayer)
                .execute());
    }

    /** Players with the most reports. Counts only — no name concat. */
    public List<PeopleAggregateStat> findTopReported(int limit) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(SqlResources.load("reports/find-top-reported"))
                .bind("limit", limit)
                .map((row, context) -> new PeopleAggregateStat(
                        row.getString("interactor_player"),
                        row.getInt("people_count")))
                .list());
    }

    /** Owners who reported {@code interactorPlayer}, newest reports first. */
    public List<String> findReporters(String interactorPlayer, int limit, int offset) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(SqlResources.load("reports/find-reporters"))
                .bind("interactor", interactorPlayer)
                .bind("limit", limit)
                .bind("offset", offset)
                .mapTo(String.class)
                .list());
    }

    /** Players {@code ownerPlayer} reported, newest report first. */
    public List<String> findReportedByOwner(String ownerPlayer, int limit, int offset) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(SqlResources.load("reports/find-reported-by-owner"))
                .bind("owner", ownerPlayer)
                .bind("limit", limit)
                .bind("offset", offset)
                .mapTo(String.class)
                .list());
    }

    /** How many owners reported {@code interactorPlayer}. */
    public int countReporters(String interactorPlayer) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(
                        "SELECT COUNT(*) FROM reports WHERE interactor_player = :interactor")
                .bind("interactor", interactorPlayer)
                .mapTo(Integer.class)
                .one());
    }

    /** How many players {@code ownerPlayer} reported. */
    public int countReportedByOwner(String ownerPlayer) throws SQLException {
        return database.withHandle(handle -> handle.createQuery("SELECT COUNT(*) FROM reports WHERE owner = :owner")
                .bind("owner", ownerPlayer)
                .mapTo(Integer.class)
                .one());
    }
}
