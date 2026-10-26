-- Keep one report per interactor/owner pair; the schema's unique constraint rejects duplicates.
INSERT INTO reports(
  reported_by_mc_name, reported_by_telegram_user_id, interactor_player, owner, created_at
) VALUES (:reporter, :telegramUserId, :interactor, :owner, :createdAt)
