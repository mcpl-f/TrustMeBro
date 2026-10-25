-- Aggregate material counts across notification rows and return the most frequent materials first.
SELECT material,
       SUM(break_count) AS break_count,
       SUM(place_count) AS place_count
FROM interaction_material_counts
WHERE interaction_count_id IN (<ids>)
GROUP BY material
ORDER BY SUM(break_count) + SUM(place_count) DESC, material ASC
