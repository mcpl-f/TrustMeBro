-- Count distinct named regions whose padded bounds overlap this player's recorded activity boxes.
SELECT COUNT(DISTINCT regions.id) AS region_count
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
