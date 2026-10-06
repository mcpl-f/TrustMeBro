package me.fulcanelly.trust.me.bro.database.repository.local;

import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import me.fulcanelly.trust.me.bro.config.MinInteractionThresholds;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.database.repository.model.NamedRegionHits;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousPlayerStat;

@RequiredArgsConstructor
public final class InteractionCountsRepository {

    private static final String PENDING_SELECT = """
            SELECT id, interactor_player, owner,
                   count_break_blocks, count_placed_blocks, count_interact_containers,
                   wid, region_corner_a_x, region_corner_a_z, region_corner_b_x, region_corner_b_z
            FROM interaction_counts
            """;

    private final Connection connection;

    /**
     * @return id of the (legacy, region-less) row that was incremented or created
     */
    public synchronized long increment(String interactorPlayer, String ownerPlayer, SuspiciousActionType actionType)
            throws SQLException {
        long now = System.currentTimeMillis();
        int breakDelta = actionType == SuspiciousActionType.BREAK_BLOCK ? 1 : 0;
        int placeDelta = actionType == SuspiciousActionType.PLACE_BLOCK ? 1 : 0;
        int containerDelta = actionType == SuspiciousActionType.INTERACT_CONTAINER ? 1 : 0;

        try (var statement = connection.prepareStatement("""
                UPDATE interaction_counts
                SET count_break_blocks = count_break_blocks + ?,
                    count_placed_blocks = count_placed_blocks + ?,
                    count_interact_containers = count_interact_containers + ?,
                    updated_at = ?
                WHERE interactor_player = ?
                  AND owner = ?
                  AND wid IS NULL
                RETURNING id
                """)) {
            statement.setInt(1, breakDelta);
            statement.setInt(2, placeDelta);
            statement.setInt(3, containerDelta);
            statement.setLong(4, now);
            statement.setString(5, interactorPlayer);
            statement.setString(6, ownerPlayer);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    return rows.getLong("id");
                }
            }
        }

        try (var statement = connection.prepareStatement("""
                INSERT INTO interaction_counts(
                  interactor_player,
                  owner,
                  count_break_blocks,
                  count_placed_blocks,
                  count_interact_containers,
                  notification_id,
                  created_at,
                  updated_at
                ) VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
                RETURNING id
                """)) {
            statement.setString(1, interactorPlayer);
            statement.setString(2, ownerPlayer);
            statement.setInt(3, breakDelta);
            statement.setInt(4, placeDelta);
            statement.setInt(5, containerDelta);
            statement.setLong(6, now);
            statement.setLong(7, now);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong("id");
            }
        }
    }

    /**
     * Region-aware record: merge into a nearby region (pending or already notified)
     * or insert a new one. Distance is axis-aligned (no sqrt): point within
     * mergeDistance of the region AABB.
     *
     * @return id of the region row that was incremented or created
     */
    public synchronized long incrementInRegion(
            String interactorPlayer,
            String ownerPlayer,
            int wid,
            int x,
            int z,
            int mergeDistance,
            SuspiciousActionType actionType) throws SQLException {
        Optional<Long> nearbyId = findNearbyRegionId(interactorPlayer, ownerPlayer, wid, x, z, mergeDistance);
        if (nearbyId.isPresent()) {
            expandRegionAndIncrement(nearbyId.get(), x, z, actionType);
            return nearbyId.get();
        }
        return insertRegion(interactorPlayer, ownerPlayer, wid, x, z, actionType);
    }

    /*
     * If we always persist corner_a as min and corner_b as max (expand already
     * does),
     * the MIN/MAX below are redundant — compare against a/b directly.
     * Keep that invariant and this query can drop the extra math.
     *
     * Merge includes already-notified rows so nearby follow-up grief does not
     * spawn a new pending region / Telegram spam. Prefer a still-pending match.
     *
     * WARNING: "region" here is NOT the {@code regions} table (NamedRegion). It is
     * an
     * activity box stored on an {@code interaction_counts} row, and the returned id
     * is
     * {@code interaction_counts.id}.
     * TODO: rename (e.g. activity box / interaction area) or move the region-row
     * logic
     * into its own repository / aggregator, the name is misleading.
     */
    private Optional<Long> findNearbyRegionId(
            String interactorPlayer,
            String ownerPlayer,
            int wid,
            int x,
            int z,
            int mergeDistance) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT id
                FROM interaction_counts
                WHERE interactor_player = ?
                  AND owner = ?
                  AND wid = ?
                  AND region_corner_a_x IS NOT NULL
                  AND region_corner_a_z IS NOT NULL
                  AND region_corner_b_x IS NOT NULL
                  AND region_corner_b_z IS NOT NULL
                  AND ? BETWEEN MIN(region_corner_a_x, region_corner_b_x) - ?
                            AND MAX(region_corner_a_x, region_corner_b_x) + ?
                  AND ? BETWEEN MIN(region_corner_a_z, region_corner_b_z) - ?
                            AND MAX(region_corner_a_z, region_corner_b_z) + ?
                ORDER BY CASE WHEN notification_id IS NULL THEN 0 ELSE 1 END,
                         updated_at DESC
                LIMIT 1
                """)) {
            statement.setString(1, interactorPlayer);
            statement.setString(2, ownerPlayer);
            statement.setInt(3, wid);
            statement.setInt(4, x);
            statement.setInt(5, mergeDistance);
            statement.setInt(6, mergeDistance);
            statement.setInt(7, z);
            statement.setInt(8, mergeDistance);
            statement.setInt(9, mergeDistance);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    return Optional.of(rows.getLong("id"));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * WARNING: {@code id} is {@code interaction_counts.id}, and "region" is the
     * activity box on
     * that row, not the {@code regions} table.
     * TODO: rename or move into its own repository / aggregator (see
     * findNearbyRegionId).
     */
    private void expandRegionAndIncrement(long id, int x, int z, SuspiciousActionType actionType) throws SQLException {
        long now = System.currentTimeMillis();
        int breakDelta = actionType == SuspiciousActionType.BREAK_BLOCK ? 1 : 0;
        int placeDelta = actionType == SuspiciousActionType.PLACE_BLOCK ? 1 : 0;
        int containerDelta = actionType == SuspiciousActionType.INTERACT_CONTAINER ? 1 : 0;

        int minX;
        int minZ;
        int maxX;
        int maxZ;
        try (var statement = connection.prepareStatement("""
                SELECT region_corner_a_x, region_corner_a_z, region_corner_b_x, region_corner_b_z
                FROM interaction_counts
                WHERE id = ?
                """)) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return;
                }
                minX = Math.min(
                        rows.getInt("region_corner_a_x"),
                        rows.getInt("region_corner_b_x"));
                maxX = Math.max(
                        rows.getInt("region_corner_a_x"),
                        rows.getInt("region_corner_b_x"));

                minZ = Math.min(
                        rows.getInt("region_corner_a_z"),
                        rows.getInt("region_corner_b_z"));

                maxZ = Math.max(
                        rows.getInt("region_corner_a_z"),
                        rows.getInt("region_corner_b_z"));
            }
        }

        minX = Math.min(minX, x);
        maxX = Math.max(maxX, x);

        minZ = Math.min(minZ, z);
        maxZ = Math.max(maxZ, z);

        try (var statement = connection.prepareStatement("""
                UPDATE interaction_counts
                SET count_break_blocks = count_break_blocks + ?,
                    count_placed_blocks = count_placed_blocks + ?,
                    count_interact_containers = count_interact_containers + ?,
                    region_corner_a_x = ?,
                    region_corner_a_z = ?,
                    region_corner_b_x = ?,
                    region_corner_b_z = ?,
                    updated_at = ?
                WHERE id = ?
                """)) {
            statement.setInt(1, breakDelta);
            statement.setInt(2, placeDelta);
            statement.setInt(3, containerDelta);
            statement.setInt(4, minX);
            statement.setInt(5, minZ);
            statement.setInt(6, maxX);
            statement.setInt(7, maxZ);
            statement.setLong(8, now);
            statement.setLong(9, id);
            statement.executeUpdate();
        }
    }

    /**
     * WARNING: inserts a new {@code interaction_counts} row with an activity box,
     * it does NOT
     * create a named region in the {@code regions} table.
     * TODO: rename or move into its own repository / aggregator (see
     * findNearbyRegionId).
     *
     * @return id of the inserted {@code interaction_counts} row
     */
    private long insertRegion(
            String interactorPlayer,
            String ownerPlayer,
            int wid,
            int x,
            int z,
            SuspiciousActionType actionType) throws SQLException {
        long now = System.currentTimeMillis();
        try (var statement = connection.prepareStatement("""
                INSERT INTO interaction_counts(
                  interactor_player,
                  owner,
                  count_break_blocks,
                  count_placed_blocks,
                  count_interact_containers,
                  notification_id,
                  wid,
                  region_corner_a_x,
                  region_corner_a_z,
                  region_corner_b_x,
                  region_corner_b_z,
                  created_at,
                  updated_at
                ) VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """)) {
            statement.setString(1, interactorPlayer);
            statement.setString(2, ownerPlayer);
            statement.setInt(3, actionType == SuspiciousActionType.BREAK_BLOCK ? 1 : 0);
            statement.setInt(4, actionType == SuspiciousActionType.PLACE_BLOCK ? 1 : 0);
            statement.setInt(5, actionType == SuspiciousActionType.INTERACT_CONTAINER ? 1 : 0);
            statement.setInt(6, wid);
            statement.setInt(7, x);
            statement.setInt(8, z);
            statement.setInt(9, x);
            statement.setInt(10, z);
            statement.setLong(11, now);
            statement.setLong(12, now);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong("id");
            }
        }
    }

    public synchronized List<String> findInteractorsReadyForNotification(
            long olderThanMillis,
            MinInteractionThresholds minInteractions //
    ) throws SQLException {
        long updatedAtCutoff = System.currentTimeMillis() - olderThanMillis;
        var result = new ArrayList<String>();

        // Optional OR-thresholds stay in the WHERE so sub-min rows keep waiting (not
        // skipped).
        String minInteractionsSql = minInteractionsSql("interaction_counts.", minInteractions);

        try (var statement = connection.prepareStatement("""
                SELECT DISTINCT interaction_counts.interactor_player
                FROM interaction_counts
                LEFT JOIN trust_edges
                    ON interaction_counts.owner = trust_edges.owner_mc_name
                    AND interaction_counts.interactor_player = trust_edges.trusted_mc_name
                LEFT JOIN reports
                    ON interaction_counts.owner = reports.owner
                    AND interaction_counts.interactor_player = reports.interactor_player
                WHERE interaction_counts.notification_id IS NULL
                    AND interaction_counts.skip_reason IS NULL
                    AND interaction_counts.updated_at <= ?
                    AND trust_edges.owner_mc_name IS NULL -- means no trust edge exists
                    AND reports.owner IS NULL -- means no report exists
                    %s
                ORDER BY interaction_counts.updated_at ASC
                """.formatted(minInteractionsSql))) {
            int i = 1;
            statement.setLong(i++, updatedAtCutoff);
            bindMinInteractions(statement, i, minInteractions);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getString("interactor_player"));
                }
            }
        }
        return result;
    }

    public synchronized List<InteractionCount> findPendingByInteractor(
            String interactorPlayer,
            MinInteractionThresholds minInteractions //
    ) throws SQLException {
        var result = new ArrayList<InteractionCount>();
        String minInteractionsSql = minInteractionsSql("", minInteractions);

        try (var statement = connection.prepareStatement(PENDING_SELECT + """
                WHERE interactor_player = ?
                  AND notification_id IS NULL
                  AND skip_reason IS NULL
                  %s
                ORDER BY owner ASC, id ASC
                """.formatted(minInteractionsSql))) {
            int i = 1;
            statement.setString(i++, interactorPlayer);
            bindMinInteractions(statement, i, minInteractions);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(mapRow(rows));
                }
            }
        }
        return result;
    }

    /**
     * Empty when disabled. When enabled: place OR break OR chest meets its min.
     *
     * @param columnPrefix {@code "interaction_counts."} or {@code ""}
     */
    private static String minInteractionsSql(String columnPrefix, MinInteractionThresholds min) {
        if (!min.isEnabled()) {
            return "";
        }
        return """
                AND (
                    %scount_placed_blocks >= ?
                    OR %scount_break_blocks >= ?
                    OR %scount_interact_containers >= ?
                )
                """.formatted(columnPrefix, columnPrefix, columnPrefix);
    }

    private static int bindMinInteractions(
            PreparedStatement statement,
            int index,
            MinInteractionThresholds min //
    ) throws SQLException {
        if (!min.isEnabled()) {
            return index;
        }
        statement.setInt(index++, min.getPlaceCount());
        statement.setInt(index++, min.getBreakCount());
        statement.setInt(index++, min.getChestInterCount());
        return index;
    }

    public synchronized boolean exists(String interactorPlayer, String ownerPlayer) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1
                FROM interaction_counts
                WHERE interactor_player = ?
                  AND owner = ?
                """)) {
            statement.setString(1, interactorPlayer);
            statement.setString(2, ownerPlayer);
            try (var rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    /**
     * Players who interacted with {@code ownerPlayer}'s stuff while the owner has
     * neither
     * trusted nor reported them. Counts players, not rows. Ignores notification
     * state,
     * skip reasons and min-interaction thresholds: "no decision yet" is all that
     * matters.
     */
    public synchronized int countUndecidedInteractors(String ownerPlayer) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT COUNT(DISTINCT interaction_counts.interactor_player)
                FROM interaction_counts
                
                LEFT JOIN trust_edges
                    ON interaction_counts.owner = trust_edges.owner_mc_name
                    AND interaction_counts.interactor_player = trust_edges.trusted_mc_name
                LEFT JOIN reports
                    ON interaction_counts.owner = reports.owner
                    AND interaction_counts.interactor_player = reports.interactor_player

                WHERE interaction_counts.owner = ?
                  AND trust_edges.owner_mc_name IS NULL
                  AND reports.owner IS NULL
                """)) {
            statement.setString(1, ownerPlayer);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    public synchronized List<InteractionCount> findPendingForOwner(String ownerPlayer) throws SQLException {
        var result = new ArrayList<InteractionCount>();
        try (var statement = connection.prepareStatement(PENDING_SELECT + """
                WHERE owner = ?
                  AND notification_id IS NULL
                  AND skip_reason IS NULL
                ORDER BY updated_at ASC, id ASC
                """)) {
            statement.setString(1, ownerPlayer);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(mapRow(rows));
                }
            }
        }
        return result;
    }

    /**
     * One pending row for join notification, chosen by strategy.
     *
     * <p>
     * {@code recent} — newest {@code updated_at}; {@code biggest} — highest
     * break+place+container sum (ties broken by newest).
     *
     * TODO: bad idea to dispatch strategy at repository level, it should be done in
     * the service layer
     */
    public synchronized Optional<InteractionCount> findTopPendingInteractionForOwner(
            String ownerPlayer,
            String strategy //
    ) throws SQLException {
        boolean biggest = "biggest".equalsIgnoreCase(strategy);
        String orderBy = biggest
                ? """
                        (interaction_counts.count_break_blocks
                          + interaction_counts.count_placed_blocks
                          + interaction_counts.count_interact_containers) DESC,
                        interaction_counts.updated_at DESC,
                        interaction_counts.id DESC
                        """
                : """
                        interaction_counts.updated_at DESC,
                        interaction_counts.id DESC
                        """;
        // Exclude already trusted / reported so /ttrust|/treport can advance to the
        // next warning.
        try (var statement = connection.prepareStatement("""
                SELECT interaction_counts.id, interaction_counts.interactor_player, interaction_counts.owner,
                       interaction_counts.count_break_blocks, interaction_counts.count_placed_blocks,
                       interaction_counts.count_interact_containers,
                       interaction_counts.wid, interaction_counts.region_corner_a_x,
                       interaction_counts.region_corner_a_z, interaction_counts.region_corner_b_x,
                       interaction_counts.region_corner_b_z
                FROM interaction_counts
                LEFT JOIN trust_edges
                    ON interaction_counts.owner = trust_edges.owner_mc_name
                    AND interaction_counts.interactor_player = trust_edges.trusted_mc_name
                LEFT JOIN reports
                    ON interaction_counts.owner = reports.owner
                    AND interaction_counts.interactor_player = reports.interactor_player
                WHERE interaction_counts.owner = ?
                  -- AND interaction_counts.notification_id IS NULL -- TODO: duplicate vs TG; maybe is_mc_notified
                  AND interaction_counts.skip_reason IS NULL
                  AND trust_edges.owner_mc_name IS NULL
                  AND reports.owner IS NULL
                ORDER BY %s
                LIMIT 1
                """.formatted(orderBy))) {
            statement.setString(1, ownerPlayer);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rows));
            }
        }
    }

    public synchronized void markSkippedByIds(List<Long> ids, String skipReason) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }

        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        try (var statement = connection.prepareStatement("""
                UPDATE interaction_counts
                SET skip_reason = ?
                WHERE notification_id IS NULL
                  AND skip_reason IS NULL
                  AND id IN (%s)
                """.formatted(placeholders))) {
            statement.setString(1, skipReason);
            for (int i = 0; i < ids.size(); i++) {
                statement.setLong(i + 2, ids.get(i));
            }
            statement.executeUpdate();
        }
    }

    public synchronized void attachNotification(
            String interactorPlayer,
            List<String> ownerPlayers,
            long notificationId) throws SQLException {
        if (ownerPlayers.isEmpty()) {
            return;
        }

        String placeholders = String.join(", ", Collections.nCopies(ownerPlayers.size(), "?"));
        try (var statement = connection.prepareStatement("""
                UPDATE interaction_counts
                SET notification_id = ?
                WHERE interactor_player = ?
                  AND notification_id IS NULL
                  AND owner IN (%s)
                """.formatted(placeholders))) {
            statement.setLong(1, notificationId);
            statement.setString(2, interactorPlayer);
            for (int i = 0; i < ownerPlayers.size(); i++) {
                statement.setString(i + 3, ownerPlayers.get(i));
            }
            statement.executeUpdate();
        }
    }

    public synchronized void attachNotificationByIds(List<Long> ids, long notificationId) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }

        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        try (var statement = connection.prepareStatement("""
                UPDATE interaction_counts
                SET notification_id = ?
                WHERE notification_id IS NULL
                  AND id IN (%s)
                """.formatted(placeholders))) {
            statement.setLong(1, notificationId);
            for (int i = 0; i < ids.size(); i++) {
                statement.setLong(i + 2, ids.get(i));
            }
            statement.executeUpdate();
        }
    }

    /** Interactors with the highest total block/container interaction counts. */
    public synchronized List<SuspiciousPlayerStat> findTopSuspicious(int limit) throws SQLException {
        var result = new ArrayList<SuspiciousPlayerStat>();
        try (var statement = connection.prepareStatement("""
                SELECT interactor_player,

                       SUM(count_break_blocks + count_placed_blocks + count_interact_containers)
                         AS interactions_sum,
                       COUNT(DISTINCT owner) AS owners_count

                FROM interaction_counts
                GROUP BY interactor_player
                ORDER BY interactions_sum DESC, interactor_player ASC
                LIMIT ?
                """)) {
            statement.setInt(1, limit);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new SuspiciousPlayerStat(
                            rows.getString("interactor_player"),
                            rows.getLong("interactions_sum"),
                            rows.getInt("owners_count")));
                }
            }
        }
        return result;
    }

    /**
     * Named admin regions this interactor touched (AABB + mergeDistance), without
     * loading every interaction row into Java.
     *
     * <p>
     * {@code nameLimit} caps returned names;
     * {@link NamedRegionHits#getTotalCount()}
     * is the full distinct count for “and N others”.
     */
    public synchronized NamedRegionHits findNamedRegionsTouchedByInteractor(
            String interactorPlayer,
            int mergeDistance,
            int nameLimit //
    ) throws SQLException {
        int pad = Math.max(0, mergeDistance);
        int total = countNamedRegionsTouchedByInteractor(interactorPlayer, pad);
        if (total == 0 || nameLimit <= 0) {
            return new NamedRegionHits(List.of(), total);
        }

        var names = new ArrayList<String>();
        try (var statement = connection.prepareStatement("""
                SELECT regions.name AS region_name,

                       SUM(interaction_counts.count_break_blocks
                           + interaction_counts.count_placed_blocks
                           + interaction_counts.count_interact_containers) AS weight

                FROM regions
                INNER JOIN interaction_counts
                    ON interaction_counts.interactor_player = ?

                    AND interaction_counts.wid IS NOT NULL
                    AND interaction_counts.region_corner_a_x IS NOT NULL
                    AND interaction_counts.region_corner_a_z IS NOT NULL

                    AND interaction_counts.region_corner_b_x IS NOT NULL
                    AND interaction_counts.region_corner_b_z IS NOT NULL

                    AND interaction_counts.wid = regions.wid


                    AND MIN(interaction_counts.region_corner_a_x, interaction_counts.region_corner_b_x) - ?
                        <= regions.center_x + regions.radius

                    AND regions.center_x - regions.radius - ?
                        <= MAX(interaction_counts.region_corner_a_x, interaction_counts.region_corner_b_x)

                    AND MIN(interaction_counts.region_corner_a_z, interaction_counts.region_corner_b_z) - ?
                        <= regions.center_z + regions.radius

                    AND regions.center_z - regions.radius - ?
                        <= MAX(interaction_counts.region_corner_a_z, interaction_counts.region_corner_b_z)

                GROUP BY regions.id, regions.name
                ORDER BY weight DESC, regions.name ASC
                LIMIT ?
                """)) {
            statement.setString(1, interactorPlayer);
            statement.setInt(2, pad);
            statement.setInt(3, pad);
            statement.setInt(4, pad);
            statement.setInt(5, pad);
            statement.setInt(6, nameLimit);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    names.add(rows.getString("region_name"));
                }
            }
        }
        return new NamedRegionHits(names, total);
    }

    private int countNamedRegionsTouchedByInteractor(String interactorPlayer, int pad)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT COUNT(DISTINCT regions.id) AS region_count
                FROM regions
                INNER JOIN interaction_counts
                    ON interaction_counts.interactor_player = ?

                    AND interaction_counts.wid IS NOT NULL

                    AND interaction_counts.region_corner_a_x IS NOT NULL
                    AND interaction_counts.region_corner_a_z IS NOT NULL

                    AND interaction_counts.region_corner_b_x IS NOT NULL
                    AND interaction_counts.region_corner_b_z IS NOT NULL
                    AND interaction_counts.wid = regions.wid
                    AND MIN(interaction_counts.region_corner_a_x, interaction_counts.region_corner_b_x) - ?
                        <= regions.center_x + regions.radius
                    AND regions.center_x - regions.radius - ?
                        <= MAX(interaction_counts.region_corner_a_x, interaction_counts.region_corner_b_x)
                    AND MIN(interaction_counts.region_corner_a_z, interaction_counts.region_corner_b_z) - ?
                        <= regions.center_z + regions.radius
                    AND regions.center_z - regions.radius - ?
                        <= MAX(interaction_counts.region_corner_a_z, interaction_counts.region_corner_b_z)
                """)) {
            statement.setString(1, interactorPlayer);
            statement.setInt(2, pad);
            statement.setInt(3, pad);
            statement.setInt(4, pad);
            statement.setInt(5, pad);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return 0;
                }
                return rows.getInt("region_count");
            }
        }
    }

    private InteractionCount mapRow(ResultSet rows) throws SQLException {
        return new InteractionCount(
                rows.getLong("id"),
                rows.getString("interactor_player"),
                rows.getString("owner"),
                rows.getInt("count_break_blocks"),
                rows.getInt("count_placed_blocks"),
                rows.getInt("count_interact_containers"),
                getNullableInt(rows, "wid"),
                getNullableInt(rows, "region_corner_a_x"),
                getNullableInt(rows, "region_corner_a_z"),
                getNullableInt(rows, "region_corner_b_x"),
                getNullableInt(rows, "region_corner_b_z"));
    }

    private Integer getNullableInt(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }
}
