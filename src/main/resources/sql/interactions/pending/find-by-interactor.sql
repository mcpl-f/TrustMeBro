-- GROUP-CHAT NOTIFICATION: counterpart of find-ready-for-notification.sql.
-- Called after that query, once per interactor, to fetch the rows that will form the group message.
-- This query uses notification_id / skip_reason; DM rows are selected by their separate delivery ledger.
SELECT id, interactor_player, owner,
       count_break_blocks, count_placed_blocks, count_interact_containers,
       wid, region_corner_a_x, region_corner_a_z, region_corner_b_x, region_corner_b_z
FROM interaction_counts
WHERE interactor_player = :interactor
  AND notification_id IS NULL
  AND skip_reason IS NULL
  -- Keep this predicate aligned with the discovery query: any one threshold can qualify a row.
  AND (
      :thresholdsEnabled = 0
      OR count_placed_blocks >= :placeCount
      OR count_break_blocks >= :breakCount
      OR count_interact_containers >= :containerCount
  )
-- Stable owner/id order keeps the batch deterministic.
ORDER BY owner ASC, id ASC
