-- List distinct undecided players newest-first for profile paging; one player may have multiple activity rows.
SELECT interaction_counts.interactor_player
FROM interaction_counts
LEFT JOIN trust_edges
    ON interaction_counts.owner = trust_edges.owner_mc_name
    AND interaction_counts.interactor_player = trust_edges.trusted_mc_name
LEFT JOIN reports
    ON interaction_counts.owner = reports.owner
    AND interaction_counts.interactor_player = reports.interactor_player
WHERE interaction_counts.owner = :owner
  AND trust_edges.owner_mc_name IS NULL
  AND reports.owner IS NULL
GROUP BY interaction_counts.interactor_player
ORDER BY MAX(interaction_counts.created_at) DESC,
         interaction_counts.interactor_player ASC
LIMIT :limit OFFSET :offset
