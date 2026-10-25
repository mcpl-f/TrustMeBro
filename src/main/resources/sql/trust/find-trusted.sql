-- List players this owner trusts, newest trust decision first.
SELECT trusted_mc_name
FROM trust_edges
WHERE owner_mc_name = :ownerPlayer
ORDER BY created_at DESC, trusted_mc_name ASC
LIMIT :limit OFFSET :offset
