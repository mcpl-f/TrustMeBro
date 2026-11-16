-- Count all undecided interaction rows for this owner.
SELECT COUNT(*)
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
