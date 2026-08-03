package me.fulcanelly.trust.me.expr.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.expr.model.ActivityBox;

/**
 * Pages {@link ActivityBox} rows from SQL table {@code interaction_counts}
 * (table name stays; Java name is ActivityBox).
 */
@RequiredArgsConstructor
public final class ActivityBoxRepository {

    private final Connection connection;

    public List<ActivityBox> findPage(int offset, int limit) throws SQLException {
        var result = new ArrayList<ActivityBox>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,
                       wid,
                       region_corner_a_x,
                       region_corner_a_z,
                       region_corner_b_x,
                       region_corner_b_z
                FROM interaction_counts
                WHERE wid IS NOT NULL
                  AND region_corner_a_x IS NOT NULL
                  AND region_corner_a_z IS NOT NULL
                  AND region_corner_b_x IS NOT NULL
                  AND region_corner_b_z IS NOT NULL
                ORDER BY id
                LIMIT ? OFFSET ?
                """)) {
            statement.setInt(1, limit);
            statement.setInt(2, offset);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int ax = rows.getInt("region_corner_a_x");
                    int az = rows.getInt("region_corner_a_z");
                    int bx = rows.getInt("region_corner_b_x");
                    int bz = rows.getInt("region_corner_b_z");
                    result.add(new ActivityBox(
                            rows.getLong("id"),
                            rows.getInt("wid"),
                            Math.min(ax, bx),
                            Math.min(az, bz),
                            Math.max(ax, bx),
                            Math.max(az, bz)));
                }
            }
        }
        return result;
    }
}
