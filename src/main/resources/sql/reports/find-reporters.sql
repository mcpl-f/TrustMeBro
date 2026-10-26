-- List owners who reported this interactor, newest report first.
SELECT owner
FROM reports
WHERE interactor_player = :interactor
ORDER BY created_at DESC, owner ASC
LIMIT :limit OFFSET :offset
