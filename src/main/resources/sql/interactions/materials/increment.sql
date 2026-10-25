-- Add a material action to the interaction row, creating or accumulating its counters.
INSERT INTO interaction_material_counts(interaction_count_id, material, break_count, place_count)
VALUES (:interactionCountId, :material, :breakDelta, :placeDelta)
ON CONFLICT(interaction_count_id, material) DO UPDATE SET
  break_count = break_count + excluded.break_count,
  place_count = place_count + excluded.place_count
