-- Load all eligible pending rows for one interactor; thresholds are ORed so any qualifying action is enough.
SELECT id, interactor_player, owner,
       count_break_blocks, count_placed_blocks, count_interact_containers,
       wid, region_corner_a_x, region_corner_a_z, region_corner_b_x, region_corner_b_z
FROM interaction_counts
WHERE interactor_player = :interactor
  AND notification_id IS NULL
  AND skip_reason IS NULL
  AND (
      :thresholdsEnabled = 0
      OR count_placed_blocks >= :placeCount
      OR count_break_blocks >= :breakCount
      OR count_interact_containers >= :containerCount
  )
ORDER BY owner ASC, id ASC
