-- Check whether this owner has already reported the interactor.
SELECT 1
FROM reports
WHERE interactor_player = :interactor
  AND owner = :owner
