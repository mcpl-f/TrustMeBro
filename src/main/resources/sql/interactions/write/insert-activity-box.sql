-- Create a new activity-box row; this is not a named region from the regions table.
INSERT INTO interaction_counts(
  interactor_player,
  owner,
  count_break_blocks,
  count_placed_blocks,
  count_interact_containers,
  notification_id,
  wid,
  region_corner_a_x,
  region_corner_a_z,
  region_corner_b_x,
  region_corner_b_z,
  created_at,
  updated_at
) VALUES (
  :interactor, :owner, :breakDelta, :placeDelta, :containerDelta, NULL,
  :wid, :x, :z, :x, :z, :now, :now
)
RETURNING id
