-- Merge nearby activity into one bounding box, preferring pending rows to avoid extra notifications.
SELECT id
FROM interaction_counts
WHERE interactor_player = :interactor
  AND owner = :owner
  AND wid = :wid
  AND region_corner_a_x IS NOT NULL
  AND region_corner_a_z IS NOT NULL
  AND region_corner_b_x IS NOT NULL
  AND region_corner_b_z IS NOT NULL
  AND :x BETWEEN MIN(region_corner_a_x, region_corner_b_x) - :mergeDistance
             AND MAX(region_corner_a_x, region_corner_b_x) + :mergeDistance
  AND :z BETWEEN MIN(region_corner_a_z, region_corner_b_z) - :mergeDistance
             AND MAX(region_corner_a_z, region_corner_b_z) + :mergeDistance
ORDER BY CASE WHEN notification_id IS NULL THEN 0 ELSE 1 END,
         updated_at DESC
LIMIT 1
