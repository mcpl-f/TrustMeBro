package me.fulcanelly.trust.me.expr.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.expr.model.NewSuggestedRegion;
import me.fulcanelly.trust.me.expr.model.SuggestedRegion;

/**
 * Load / persist-dirty / insert {@code suggested_regions} + sources.
 * SQL still uses {@code interaction_count_id}; Java talks activity-box ids.
 */
@RequiredArgsConstructor
public final class SuggestedRegionRepository {

    private final Connection connection;

    public List<SuggestedRegion> findAll() throws SQLException {
        Map<Long, Set<Long>> sourcesBySuggested = loadSourcesBySuggestedId();
        var result = new ArrayList<SuggestedRegion>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        SELECT id, wid, min_x, min_z, max_x, max_z, status
                        FROM suggested_regions
                        """)) {
            while (rows.next()) {
                long id = rows.getLong("id");
                result.add(SuggestedRegion.load(
                        id,
                        rows.getInt("wid"),
                        rows.getInt("min_x"),
                        rows.getInt("min_z"),
                        rows.getInt("max_x"),
                        rows.getInt("max_z"),
                        rows.getString("status"),
                        sourcesBySuggested.getOrDefault(id, Set.of())));
            }
        }
        return result;
    }

    private Map<Long, Set<Long>> loadSourcesBySuggestedId() throws SQLException {
        Map<Long, Set<Long>> map = new HashMap<>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        SELECT suggested_region_id, interaction_count_id
                        FROM suggested_region_sources
                        """)) {
            while (rows.next()) {
                map.computeIfAbsent(rows.getLong("suggested_region_id"), id -> new LinkedHashSet<>())
                        .add(rows.getLong("interaction_count_id"));
            }
        }
        return map;
    }

    public int persistDirty(List<SuggestedRegion> dirty) throws SQLException {
        if (dirty.isEmpty()) {
            return 0;
        }
        connection.setAutoCommit(false);
        try {
            long now = System.currentTimeMillis();
            int saved = 0;
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE suggested_regions
                    SET min_x = ?, min_z = ?, max_x = ?, max_z = ?,
                        updated_at = ?
                    WHERE id = ?
                    """);
                    PreparedStatement insertSource = connection.prepareStatement("""
                            INSERT OR IGNORE INTO suggested_region_sources(
                              suggested_region_id, interaction_count_id
                            ) VALUES (?, ?)
                            """)) {
                for (SuggestedRegion region : dirty) {
                    update.setInt(1, region.getMinX());
                    update.setInt(2, region.getMinZ());
                    update.setInt(3, region.getMaxX());
                    update.setInt(4, region.getMaxZ());
                    update.setLong(5, now);
                    update.setLong(6, region.getId());
                    update.executeUpdate();

                    for (long activityBoxId : region.getNewlyAttachedActivityBoxIds()) {
                        insertSource.setLong(1, region.getId());
                        insertSource.setLong(2, activityBoxId);
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

    public int insertNew(List<NewSuggestedRegion> newSuggested) throws SQLException {
        if (newSuggested.isEmpty()) {
            return 0;
        }
        connection.setAutoCommit(false);
        try {
            long now = System.currentTimeMillis();
            int saved = 0;
            try (PreparedStatement insertRegion = connection.prepareStatement(
                    """
                            INSERT INTO suggested_regions(
                              wid, min_x, min_z, max_x, max_z,
                              status, created_at, updated_at
                            ) VALUES (?, ?, ?, ?, ?, 'pending', ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
                    PreparedStatement insertSource = connection.prepareStatement("""
                            INSERT INTO suggested_region_sources(
                              suggested_region_id, interaction_count_id
                            ) VALUES (?, ?)
                            """)) {
                for (NewSuggestedRegion region : newSuggested) {
                    insertRegion.setInt(1, region.getWid());
                    insertRegion.setInt(2, region.getMinX());
                    insertRegion.setInt(3, region.getMinZ());
                    insertRegion.setInt(4, region.getMaxX());
                    insertRegion.setInt(5, region.getMaxZ());
                    insertRegion.setLong(6, now);
                    insertRegion.setLong(7, now);
                    insertRegion.executeUpdate();

                    long suggestedId;
                    try (ResultSet keys = insertRegion.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("suggested_regions id missing");
                        }
                        suggestedId = keys.getLong(1);
                    }

                    for (long activityBoxId : region.getActivityBoxIds()) {
                        insertSource.setLong(1, suggestedId);
                        insertSource.setLong(2, activityBoxId);
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
