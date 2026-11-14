-- OWNER-DM NOTIFICATION: counterpart of find-ready-for-direct-telegram-notification.sql.
-- Called after that query, once per interactor, to fetch rows grouped and delivered to each owner.
-- Unlike group notifications, DM delivery is not marked on interaction_counts: the separate
-- direct_telegram_notification_deliveries ledger prevents the same interaction row being DM'd again.
SELECT interaction_counts.id, interaction_counts.interactor_player, interaction_counts.owner,
       interaction_counts.count_break_blocks, interaction_counts.count_placed_blocks,
       interaction_counts.count_interact_containers, interaction_counts.wid,
       interaction_counts.region_corner_a_x, interaction_counts.region_corner_a_z,
       interaction_counts.region_corner_b_x, interaction_counts.region_corner_b_z
FROM interaction_counts
LEFT JOIN trust_edges
    ON interaction_counts.owner = trust_edges.owner_mc_name
    AND interaction_counts.interactor_player = trust_edges.trusted_mc_name
LEFT JOIN reports
    ON interaction_counts.owner = reports.owner
    AND interaction_counts.interactor_player = reports.interactor_player
WHERE interaction_counts.interactor_player = :interactor
    -- Do not DM an owner about an interactor they have already trusted or reported.
    AND trust_edges.owner_mc_name IS NULL
    AND reports.owner IS NULL
    -- NOT EXISTS checks the delivery ledger per interaction row, independently of group delivery.
    AND NOT EXISTS (
        SELECT 1
        FROM direct_telegram_notification_deliveries
        WHERE interaction_count_id = interaction_counts.id
    )
    -- Keep this predicate aligned with the discovery query: any one threshold can qualify a row.
    AND (
        :thresholdsEnabled = 0
        OR interaction_counts.count_placed_blocks >= :placeCount
        OR interaction_counts.count_break_blocks >= :breakCount
        OR interaction_counts.count_interact_containers >= :containerCount
    )
-- Stable owner/id order makes grouping and message composition deterministic.
ORDER BY interaction_counts.owner ASC, interaction_counts.id ASC
