-- Record a private Telegram delivery to one linked owner.
INSERT INTO sent_notifications(
  sent_type, interactor_player, telegram_chat_id, telegram_message_id, created_at
) VALUES ('tg_dm', :interactor, :chatId, :messageId, :createdAt)
RETURNING id
