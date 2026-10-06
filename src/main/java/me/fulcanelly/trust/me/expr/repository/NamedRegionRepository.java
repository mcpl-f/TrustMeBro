package me.fulcanelly.trust.me.expr.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.expr.model.NamedRegionBox;

/** Loads named {@code regions} as {@link NamedRegionBox} AABBs. */
@RequiredArgsConstructor
public final class NamedRegionRepository {

    private final Connection connection;

    public List<NamedRegionBox> findAll() throws SQLException {
        var result = new ArrayList<NamedRegionBox>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        SELECT name, wid, center_x, center_z, radius
                        FROM regions
                        """)) {
            while (rows.next()) {
                int cx = rows.getInt("center_x");
                int cz = rows.getInt("center_z");
                int r = Math.max(0, rows.getInt("radius"));
                result.add(new NamedRegionBox(
                        rows.getString("name"),
                        rows.getInt("wid"),
                        cx - r,
                        cz - r,
                        cx + r,
                        cz + r));
            }
        }
        return result;
    }
}
