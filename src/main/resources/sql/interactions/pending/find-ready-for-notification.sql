-- Select debounced interactors with eligible rows, excluding players the owner already trusted or reported.
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
    AND interaction_counts.updated_at <= :updatedAtCutoff
    AND trust_edges.owner_mc_name IS NULL
    AND reports.owner IS NULL
    AND (
        :thresholdsEnabled = 0
        OR interaction_counts.count_placed_blocks >= :placeCount
        OR interaction_counts.count_break_blocks >= :breakCount
        OR interaction_counts.count_interact_containers >= :containerCount
    )
ORDER BY interaction_counts.updated_at ASC
