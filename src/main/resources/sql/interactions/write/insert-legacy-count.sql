-- Create the interaction row without
-- an activity box when no existing row can be reused.
INSERT INTO interaction_counts(
  interactor_player,
  owner,
  count_break_blocks,
  count_placed_blocks,
  count_interact_containers,
  notification_id,
  created_at,
  updated_at
) VALUES (:interactor, :owner, :breakDelta, :placeDelta, :containerDelta, NULL, :now, :now)
RETURNING id
