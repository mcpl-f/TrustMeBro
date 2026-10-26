-- Return notification-pending rows for one owner in the same age order used by notification processing.
SELECT id, interactor_player, owner,
       count_break_blocks, count_placed_blocks, count_interact_containers,
       wid, region_corner_a_x, region_corner_a_z, region_corner_b_x, region_corner_b_z
FROM interaction_counts
WHERE owner = :owner
  AND notification_id IS NULL
  AND skip_reason IS NULL
ORDER BY updated_at ASC, id ASC
