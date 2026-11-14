-- OWNER-DM NOTIFICATION: counterpart of find-for-direct-telegram-notification.sql.
-- Flow: find interactors with rows ready for DM delivery -> load their undelivered rows.
-- This is intentionally separate from the group-chat query: DM delivery has its own per-row
-- delivery ledger, so notification_id / skip_reason must not be used as its delivery state.
-- Debounce and trust/report exclusions are shared concepts, but the delivery eligibility differs.
SELECT DISTINCT interaction_counts.interactor_player
FROM interaction_counts
LEFT JOIN trust_edges
    ON interaction_counts.owner = trust_edges.owner_mc_name
    AND interaction_counts.interactor_player = trust_edges.trusted_mc_name
LEFT JOIN reports
    ON interaction_counts.owner = reports.owner
    AND interaction_counts.interactor_player = reports.interactor_player
WHERE interaction_counts.updated_at <= :updatedAtCutoff
    -- Exclude interactors already trusted or reported by this specific owner.
    AND trust_edges.owner_mc_name IS NULL
    AND reports.owner IS NULL
    -- A DM delivery is recorded separately; only rows absent from that ledger are eligible.
    AND NOT EXISTS (
        SELECT 1
        FROM direct_telegram_notification_deliveries
        WHERE interaction_count_id = interaction_counts.id
    )
    -- Thresholds are ORed: meeting any one configured action count is enough.
    AND (
        :thresholdsEnabled = 0
        OR interaction_counts.count_placed_blocks >= :placeCount
        OR interaction_counts.count_break_blocks >= :breakCount
        OR interaction_counts.count_interact_containers >= :containerCount
    )
-- One interactor can have many rows; DISTINCT above gives the job one batch per interactor.
ORDER BY interaction_counts.updated_at ASC
