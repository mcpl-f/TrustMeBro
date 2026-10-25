DELETE FROM trust_edges
WHERE owner_mc_name = :owner
  AND trusted_mc_name = :trusted
