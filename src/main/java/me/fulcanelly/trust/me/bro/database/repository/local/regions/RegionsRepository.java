package me.fulcanelly.trust.me.bro.database.repository.local;

import java.sql.SQLException;
import java.util.List;

import me.fulcanelly.trust.me.bro.database.SqlResources;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;
import me.fulcanelly.trust.me.bro.database.repository.model.NamedRegion;

public final class RegionsRepository {

    private final SqliteJdbi database;

    public RegionsRepository(SqliteJdbi database) {
        this.database = database;
    }

    public long insert(
            String name,
            boolean mutable,
            String createdByMcName,
            int wid,
            int centerX,
            int centerZ,
            int radius) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(SqlResources.load("regions/insert"))
                .bind("name", name)
                .bind("mutable", mutable ? 1 : 0)
                .bind("createdBy", createdByMcName)
                .bind("createdAt", System.currentTimeMillis())
                .bind("wid", wid)
                .bind("centerX", centerX)
                .bind("centerZ", centerZ)
                .bind("radius", radius)
                .mapTo(Long.class)
                .one());
    }

    public List<NamedRegion> findAll() throws SQLException {
        return database.withHandle(handle -> handle.createQuery(SqlResources.load("regions/find-all"))
                .map((row, context) -> new NamedRegion(
                        row.getLong("id"),
                        row.getString("name"),
                        row.getInt("mutable") != 0,
                        row.getString("created_by_mc_name"),
                        row.getLong("created_at"),
                        row.getInt("wid"),
                        row.getInt("center_x"),
                        row.getInt("center_z"),
                        row.getInt("radius")))
                .list());
    }
}
