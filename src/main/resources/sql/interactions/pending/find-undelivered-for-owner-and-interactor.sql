-- Load only one owner's undelivered activity rows for one interactor.
-- The debounce window is applied here; trust/report and thresholds stay in Java.
SELECT id, interactor_player, owner,
       count_break_blocks, count_placed_blocks, count_interact_containers,
       wid, region_corner_a_x, region_corner_a_z, region_corner_b_x, region_corner_b_z
FROM interaction_counts
WHERE owner = :owner
  AND interactor_player = :interactor
  AND updated_at <= :updatedAtCutoff
  AND NOT EXISTS (
      SELECT 1
      FROM direct_telegram_notification_deliveries
      WHERE interaction_count_id = interaction_counts.id
  )
ORDER BY id ASC
