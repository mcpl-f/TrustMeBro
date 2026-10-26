-- Reuse the row without an activity box
-- for this player pair so repeated events keep
-- one pending record.
UPDATE interaction_counts
SET count_break_blocks = count_break_blocks + :breakDelta,
    count_placed_blocks = count_placed_blocks + :placeDelta,
    count_interact_containers = count_interact_containers + :containerDelta,
    updated_at = :now
WHERE interactor_player = :interactor
  AND owner = :owner
  AND wid IS NULL
RETURNING id
