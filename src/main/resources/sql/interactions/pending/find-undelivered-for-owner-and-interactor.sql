-- Load this pair's activity since its previous DM, once the whole pair is idle.
-- Debounce is pair-wide so activity in another activity box resets the same timer.
SELECT id, interactor_player, owner,
       count_break_blocks, count_placed_blocks, count_interact_containers,
       wid, region_corner_a_x, region_corner_a_z, region_corner_b_x, region_corner_b_z
FROM interaction_counts
WHERE owner = :owner
  AND interactor_player = :interactor
  AND updated_at > :lastDeliveredAt
  AND NOT EXISTS (
      SELECT 1
      FROM interaction_counts recent
      WHERE recent.owner = interaction_counts.owner
        AND recent.interactor_player = interaction_counts.interactor_player
        AND recent.updated_at > :updatedAtCutoff
  )
ORDER BY id ASC
