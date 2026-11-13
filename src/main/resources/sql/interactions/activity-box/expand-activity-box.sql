-- Keep the stored activity box enclosing all recorded points while incrementing its counters.
UPDATE interaction_counts
SET count_break_blocks = count_break_blocks + :breakDelta,
    count_placed_blocks = count_placed_blocks + :placeDelta,
    count_interact_containers = count_interact_containers + :containerDelta,
    region_corner_a_x = :minX,
    region_corner_a_z = :minZ,
    region_corner_b_x = :maxX,
    region_corner_b_z = :maxZ,
    updated_at = :now
WHERE id = :id
