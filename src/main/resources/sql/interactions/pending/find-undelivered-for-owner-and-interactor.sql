-- Load one owner's undelivered activity for one interactor after the pair becomes idle.
-- The NOT EXISTS check applies debounce to the whole pair, including activity in other boxes.
SELECT id, interactor_player, owner,
       count_break_blocks, count_placed_blocks, count_interact_containers,
       wid, region_corner_a_x, region_corner_a_z, region_corner_b_x, region_corner_b_z
FROM interaction_counts
WHERE owner = :owner
  AND interactor_player = :interactor
  AND NOT EXISTS (
      SELECT 1
      FROM interaction_counts recent
      WHERE recent.owner = interaction_counts.owner
        AND recent.interactor_player = interaction_counts.interactor_player
        AND recent.updated_at > :updatedAtCutoff
  )
  AND NOT EXISTS (
      SELECT 1
      FROM direct_telegram_notification_deliveries
      WHERE interaction_count_id = interaction_counts.id
  )
ORDER BY id ASC
