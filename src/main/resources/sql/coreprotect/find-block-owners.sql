-- Find non-system CoreProtect users who changed a block at this exact location.
SELECT DISTINCT u.user
FROM co_block h
JOIN co_user u ON u.id = h.user
WHERE h.wid = :worldId
  AND h.x = :x
  AND h.y = :y
  AND h.z = :z
  AND h.action IN (0, 1)
  AND u.user IS NOT NULL
  AND u.user NOT LIKE '#%'
