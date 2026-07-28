package me.fulcanelly.trust.me.bro.database.repository.local;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.repository.model.NamedRegion;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public final class RegionsRepository {

    private final Connection connection;

    public synchronized long insert(
            String name,
            boolean mutable,
            String createdByMcName,
            int wid,
            int centerX,
            int centerZ,
            int radius) throws SQLException {
        long now = System.currentTimeMillis();
        try (var statement = connection.prepareStatement("""
                INSERT INTO regions(
                  name,

                  mutable,

                  created_by_mc_name,
                  created_at,

                  wid,
                  center_x,
                  center_z,
                  radius
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setInt(2, mutable ? 1 : 0);
            statement.setString(3, createdByMcName);
            statement.setLong(4, now);
            statement.setInt(5, wid);
            statement.setInt(6, centerX);
            statement.setInt(7, centerZ);
            statement.setInt(8, radius);
            statement.executeUpdate();

            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("regions id was not generated");
    }

    public synchronized List<NamedRegion> findAll() throws SQLException {
        var result = new ArrayList<NamedRegion>();
        try (var statement = connection.prepareStatement("""
                SELECT
                  id,

                  name,

                  mutable,

                  created_by_mc_name,
                  created_at,

                  wid,
                  center_x,
                  center_z,
                  radius
                FROM regions
                ORDER BY id ASC
                """);
                var rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(mapRow(rows));
            }
        }
        return result;
    }

    private NamedRegion mapRow(ResultSet rows) throws SQLException {
        return new NamedRegion(
                rows.getLong("id"),
                rows.getString("name"),
                rows.getInt("mutable") != 0,
                rows.getString("created_by_mc_name"),
                rows.getLong("created_at"),
                rows.getInt("wid"),
                rows.getInt("center_x"),
                rows.getInt("center_z"),
                rows.getInt("radius"));
    }
}
