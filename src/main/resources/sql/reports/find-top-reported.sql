-- Rank reported players by report count; names are returned separately from the aggregate.
SELECT interactor_player, COUNT(*) AS people_count
FROM reports
GROUP BY interactor_player
ORDER BY people_count DESC, interactor_player ASC
LIMIT :limit
