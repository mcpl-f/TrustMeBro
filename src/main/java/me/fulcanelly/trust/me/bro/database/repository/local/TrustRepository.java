package me.fulcanelly.trust.me.bro.database.repository.local;

import java.sql.SQLException;
import java.util.List;

import me.fulcanelly.trust.me.bro.database.SqlResources;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;
import me.fulcanelly.trust.me.bro.database.repository.model.PeopleAggregateStat;

public final class TrustRepository {

    private final SqliteJdbi database;

    public TrustRepository(SqliteJdbi database) {
        this.database = database;
    }

    public boolean isTrusted(String ownerPlayer, String trustedPlayer) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(
                        "SELECT 1 FROM trust_edges WHERE owner_mc_name = :owner AND trusted_mc_name = :trusted")
                .bind("owner", ownerPlayer)
                .bind("trusted", trustedPlayer)
                .mapTo(Integer.class)
                .findOne()
                .isPresent());
    }

    public void trust(String ownerPlayer, String trustedPlayer, Long telegramUserId) throws SQLException {
        database.withHandle(handle -> handle.createUpdate(SqlResources.load("trust/insert"))
                .bind("owner", ownerPlayer)
                .bind("trusted", trustedPlayer)
                .bind("createdAt", System.currentTimeMillis())
                .bind("telegramUserId", telegramUserId)
                .execute());
    }

    public void untrust(String ownerPlayer, String trustedPlayer) throws SQLException {
        database.withHandle(handle -> handle.createUpdate(SqlResources.load("trust/delete"))
                .bind("owner", ownerPlayer)
                .bind("trusted", trustedPlayer)
                .execute());
    }

    /** Players with the most trusters (owners who trust them). Counts only — no name concat. */
    public List<PeopleAggregateStat> findTopTrusted(int limit) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(SqlResources.load("trust/find-top-trusted"))
                .bind("limit", limit)
                .map((row, context) -> new PeopleAggregateStat(
                        row.getString("trusted_mc_name"),
                        row.getInt("people_count")))
                .list());
    }

    /**
     * Owners who trust {@code trustedPlayer} (trusters), a page of {@code limit} rows after {@code offset}.
     *
     * <p>Not "trustees" — a trustee is the person being trusted; a truster grants trust.
     */
    public List<String> findTrusters(String trustedPlayer, int limit, int offset) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(SqlResources.load("trust/find-trusters"))
                .bind("trustedPlayer", trustedPlayer)
                .bind("limit", limit)
                .bind("offset", offset)
                .mapTo(String.class)
                .list());
    }

    /** Players {@code ownerPlayer} trusts, newest decision first, a page of {@code limit} rows after {@code offset}. */
    public List<String> findTrusted(String ownerPlayer, int limit, int offset) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(SqlResources.load("trust/find-trusted"))
                .bind("ownerPlayer", ownerPlayer)
                .bind("limit", limit)
                .bind("offset", offset)
                .mapTo(String.class)
                .list());
    }

    /** How many owners trust {@code trustedPlayer}. */
    public int countTrusters(String trustedPlayer) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(
                        "SELECT COUNT(*) FROM trust_edges WHERE trusted_mc_name = :trusted")
                .bind("trusted", trustedPlayer)
                .mapTo(Integer.class)
                .one());
    }

    /** How many players {@code ownerPlayer} trusts. */
    public int countTrusted(String ownerPlayer) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(
                        "SELECT COUNT(*) FROM trust_edges WHERE owner_mc_name = :owner")
                .bind("owner", ownerPlayer)
                .mapTo(Integer.class)
                .one());
    }
}
