-- Associate all still-unnotified rows for these owners and this interactor with the sent message.
UPDATE interaction_counts
SET notification_id = :notificationId
WHERE interactor_player = :interactor
  AND notification_id IS NULL
  AND owner IN (<owners>)
