-- Insert a trust decision once; repeated requests leave the original decision unchanged.
INSERT OR IGNORE INTO trust_edges(
  owner_mc_name, trusted_mc_name, created_at, created_by_telegram_user_id
) VALUES (:owner, :trusted, :createdAt, :telegramUserId)
