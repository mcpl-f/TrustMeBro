-- Rank trusted players by number of owners who trust them; avoid concatenating names in SQL.
SELECT trusted_mc_name, COUNT(*) AS people_count
FROM trust_edges
GROUP BY trusted_mc_name
ORDER BY people_count DESC, trusted_mc_name ASC
LIMIT :limit
