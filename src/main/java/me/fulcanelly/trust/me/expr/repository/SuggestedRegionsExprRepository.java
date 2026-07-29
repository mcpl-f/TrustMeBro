package me.fulcanelly.trust.me.expr.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.expr.model.RegionBox;
import me.fulcanelly.trust.me.expr.model.SuggestedCandidate;

@RequiredArgsConstructor
public final class SuggestedRegionsExprRepository {

    private final Connection connection;

    public List<RegionBox> findAllAsCoverageBoxes() throws SQLException {
        var result = new ArrayList<RegionBox>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        SELECT id, wid, min_x, min_z, max_x, max_z, status
                        FROM suggested_regions
                        """)) {
            while (rows.next()) {
                result.add(new RegionBox(
                        "suggested#" + rows.getLong("id") + "(" + rows.getString("status") + ")",
                        rows.getInt("wid"),
                        rows.getInt("min_x"),
                        rows.getInt("min_z"),
                        rows.getInt("max_x"),
                        rows.getInt("max_z")));
            }
        }
        return result;
    }

    /** Append only; previous suggestions stay for coverage on later runs. */
    public int insertNew(List<SuggestedCandidate> candidates) throws SQLException {
        if (candidates.isEmpty()) {
            return 0;
        }
        connection.setAutoCommit(false);
        try {
            long now = System.currentTimeMillis();
            int saved = 0;
            try (PreparedStatement insertRegion = connection.prepareStatement(
                    """
                            INSERT INTO suggested_regions(
                              wid, center_x, center_z, radius,
                              min_x, min_z, max_x, max_z,
                              weight, status, created_at, updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
                    PreparedStatement insertSource = connection.prepareStatement("""
                            INSERT INTO suggested_region_sources(
                              suggested_region_id, interaction_count_id
                            ) VALUES (?, ?)
                            """)) {
                for (SuggestedCandidate candidate : candidates) {
                    insertRegion.setInt(1, candidate.getWid());
                    insertRegion.setInt(2, candidate.centerX());
                    insertRegion.setInt(3, candidate.centerZ());
                    insertRegion.setInt(4, candidate.displayRadius());
                    insertRegion.setInt(5, candidate.getMinX());
                    insertRegion.setInt(6, candidate.getMinZ());
                    insertRegion.setInt(7, candidate.getMaxX());
                    insertRegion.setInt(8, candidate.getMaxZ());
                    insertRegion.setLong(9, candidate.getWeight());
                    insertRegion.setLong(10, now);
                    insertRegion.setLong(11, now);
                    insertRegion.executeUpdate();

                    long suggestedId;
                    try (ResultSet keys = insertRegion.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("suggested_regions id missing");
                        }
                        suggestedId = keys.getLong(1);
                    }

                    for (long interactionId : candidate.getInteractionIds()) {
                        insertSource.setLong(1, suggestedId);
                        insertSource.setLong(2, interactionId);
                        insertSource.addBatch();
                    }
                    insertSource.executeBatch();
                    saved++;
                }
            }
            connection.commit();
            return saved;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }
}
