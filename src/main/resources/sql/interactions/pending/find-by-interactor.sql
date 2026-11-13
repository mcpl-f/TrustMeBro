-- Load eligible pending rows for one interactor and notification channel.
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
    AND trust_edges.owner_mc_name IS NULL
    AND reports.owner IS NULL
    AND (
        (:directTelegram = 0
            AND interaction_counts.notification_id IS NULL
            AND interaction_counts.skip_reason IS NULL)
        OR
        (:directTelegram = 1
            AND NOT EXISTS (
                SELECT 1
                FROM direct_telegram_notification_deliveries
                WHERE interaction_count_id = interaction_counts.id
            ))
    )
    AND (
        :thresholdsEnabled = 0
        OR interaction_counts.count_placed_blocks >= :placeCount
        OR interaction_counts.count_break_blocks >= :breakCount
        OR interaction_counts.count_interact_containers >= :containerCount
    )
ORDER BY interaction_counts.owner ASC, interaction_counts.id ASC
