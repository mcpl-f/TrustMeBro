-- Stop selected unnotified rows from being retried after a notification was intentionally skipped.
UPDATE interaction_counts
SET skip_reason = :skipReason
WHERE notification_id IS NULL
  AND skip_reason IS NULL
  AND id IN (<ids>)
