-- GROUP-CHAT NOTIFICATION: counterpart of find-by-interactor.sql.
-- Flow: find distinct interactors ready for the debounce window -> load their pending rows.
-- Kept separate from the DM queries because group delivery uses interaction_counts.notification_id
-- and skip_reason as its delivery/skip state; DM delivery is tracked in its own table.
-- A row is eligible only if it is still unnotified, not skipped, old enough, and meets the thresholds.
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
    -- LEFT JOIN + IS NULL is an anti-join: exclude interactors already trusted or reported by this owner.
    AND trust_edges.owner_mc_name IS NULL
    AND reports.owner IS NULL
    -- Thresholds are ORed: meeting any one configured action count is enough.
    AND (
        :thresholdsEnabled = 0
        OR interaction_counts.count_placed_blocks >= :placeCount
        OR interaction_counts.count_break_blocks >= :breakCount
        OR interaction_counts.count_interact_containers >= :containerCount
    )
-- One interactor can have many rows; DISTINCT above gives the job one batch per interactor.
ORDER BY interaction_counts.updated_at ASC
