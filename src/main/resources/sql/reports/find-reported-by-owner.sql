-- List players reported by this owner, newest report first.
SELECT interactor_player
FROM reports
WHERE owner = :owner
ORDER BY created_at DESC, interactor_player ASC
LIMIT :limit OFFSET :offset
