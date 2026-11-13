SELECT DISTINCT interaction_counts.interactor_player
FROM interaction_counts
LEFT JOIN trust_edges
    ON interaction_counts.owner = trust_edges.owner_mc_name
    AND interaction_counts.interactor_player = trust_edges.trusted_mc_name
LEFT JOIN reports
    ON interaction_counts.owner = reports.owner
    AND interaction_counts.interactor_player = reports.interactor_player
WHERE interaction_counts.updated_at <= :updatedAtCutoff
    AND trust_edges.owner_mc_name IS NULL
    AND reports.owner IS NULL
    AND NOT EXISTS (
        SELECT 1
        FROM direct_telegram_notification_deliveries
        WHERE interaction_count_id = interaction_counts.id
    )
    AND (
        :thresholdsEnabled = 0
        OR interaction_counts.count_placed_blocks >= :placeCount
        OR interaction_counts.count_break_blocks >= :breakCount
        OR interaction_counts.count_interact_containers >= :containerCount
    )
ORDER BY interaction_counts.updated_at ASC
