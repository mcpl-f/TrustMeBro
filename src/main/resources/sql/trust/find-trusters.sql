-- List owners who trust this player, newest trust first for stable profile paging.
SELECT owner_mc_name
FROM trust_edges
WHERE trusted_mc_name = :trustedPlayer
ORDER BY created_at DESC, owner_mc_name ASC
LIMIT :limit OFFSET :offset
