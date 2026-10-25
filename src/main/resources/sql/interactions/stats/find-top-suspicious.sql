-- Rank players by total recorded activity and include how many distinct owners they affected.
SELECT interactor_player,
       SUM(count_break_blocks + count_placed_blocks + count_interact_containers)
         AS interactions_sum,
       COUNT(DISTINCT owner) AS owners_count
FROM interaction_counts
GROUP BY interactor_player
ORDER BY interactions_sum DESC, interactor_player ASC
LIMIT :limit
