-- Record the Telegram message linked to the interactor's notification.
INSERT INTO sent_notifications(
  sent_type, interactor_player, telegram_chat_id, telegram_message_id, created_at
) VALUES ('tg', :interactor, :chatId, :messageId, :createdAt)
RETURNING id
