-- Rank touched named regions by the player's total activity within overlapping boxes.
SELECT regions.name AS region_name,
       SUM(interaction_counts.count_break_blocks
           + interaction_counts.count_placed_blocks
           + interaction_counts.count_interact_containers) AS weight
FROM regions
INNER JOIN interaction_counts
    ON interaction_counts.interactor_player = :interactor
    AND interaction_counts.wid IS NOT NULL
    AND interaction_counts.region_corner_a_x IS NOT NULL
    AND interaction_counts.region_corner_a_z IS NOT NULL
    AND interaction_counts.region_corner_b_x IS NOT NULL
    AND interaction_counts.region_corner_b_z IS NOT NULL
    AND interaction_counts.wid = regions.wid
    AND MIN(interaction_counts.region_corner_a_x, interaction_counts.region_corner_b_x) - :pad
        <= regions.center_x + regions.radius
    AND regions.center_x - regions.radius - :pad
        <= MAX(interaction_counts.region_corner_a_x, interaction_counts.region_corner_b_x)
    AND MIN(interaction_counts.region_corner_a_z, interaction_counts.region_corner_b_z) - :pad
        <= regions.center_z + regions.radius
    AND regions.center_z - regions.radius - :pad
        <= MAX(interaction_counts.region_corner_a_z, interaction_counts.region_corner_b_z)
GROUP BY regions.id, regions.name
ORDER BY weight DESC, regions.name ASC
LIMIT :limit
