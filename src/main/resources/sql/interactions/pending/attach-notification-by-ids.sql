-- Mark only the exact rows included in a sent notification.
UPDATE interaction_counts
SET notification_id = :notificationId
WHERE notification_id IS NULL
  AND id IN (<ids>)
