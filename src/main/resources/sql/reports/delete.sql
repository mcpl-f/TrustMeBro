-- Remove the owner's report for this interactor.
DELETE FROM reports
WHERE interactor_player = :interactor
  AND owner = :owner
