-- Load all activity for one owner/interactor pair after the pair becomes idle.
-- One pair produces at most one DM; activity boxes are combined into that message.
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
      WHERE owner_mc_name = interaction_counts.owner
        AND interactor_player = interaction_counts.interactor_player
  )
ORDER BY id ASC
