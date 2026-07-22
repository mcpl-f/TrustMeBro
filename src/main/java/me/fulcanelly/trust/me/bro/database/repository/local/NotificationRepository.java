package me.fulcanelly.trust.me.bro.database.repository.local;

import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@RequiredArgsConstructor
public final class NotificationRepository {

    private final Connection connection;

    public synchronized long insertTelegram(String interactorPlayer, long chatId, Long messageId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO sent_notifications(sent_type, interactor_player, telegram_chat_id, telegram_message_id, created_at)
                VALUES ('tg', ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, interactorPlayer);
            statement.setLong(2, chatId);
            if (messageId == null) {
                statement.setObject(3, null);
            } else {
                statement.setLong(3, messageId);
            }
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();

            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("sent notification id was not generated");
    }
}
