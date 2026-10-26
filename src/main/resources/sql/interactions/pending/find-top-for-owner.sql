-- Find the next owner warning, excluding decisions already made; choose by configured strategy, then recency.
SELECT interaction_counts.id, interaction_counts.interactor_player, interaction_counts.owner,
       interaction_counts.count_break_blocks, interaction_counts.count_placed_blocks,
       interaction_counts.count_interact_containers,
       interaction_counts.wid, interaction_counts.region_corner_a_x,
       interaction_counts.region_corner_a_z, interaction_counts.region_corner_b_x,
       interaction_counts.region_corner_b_z
FROM interaction_counts
LEFT JOIN trust_edges
    ON interaction_counts.owner = trust_edges.owner_mc_name
    AND interaction_counts.interactor_player = trust_edges.trusted_mc_name
LEFT JOIN reports
    ON interaction_counts.owner = reports.owner
    AND interaction_counts.interactor_player = reports.interactor_player
WHERE interaction_counts.owner = :owner
  AND interaction_counts.skip_reason IS NULL
  AND trust_edges.owner_mc_name IS NULL
  AND reports.owner IS NULL
ORDER BY
  CASE WHEN :biggest = 1 THEN
    interaction_counts.count_break_blocks
      + interaction_counts.count_placed_blocks
      + interaction_counts.count_interact_containers
  END DESC,
  interaction_counts.updated_at DESC,
  interaction_counts.id DESC
LIMIT 1
