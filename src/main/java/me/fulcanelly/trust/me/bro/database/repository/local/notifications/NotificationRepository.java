package me.fulcanelly.trust.me.bro.database.repository.local.notifications;

import java.sql.SQLException;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.database.SqlResources;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;

@RequiredArgsConstructor
public final class NotificationRepository {

    private final SqliteJdbi database;

    public synchronized long insertTelegram(String interactorPlayer, long chatId, Long messageId) throws SQLException {
        return database.withHandle(handle -> handle.createQuery(SqlResources.load("notifications/insert-telegram"))
                .bind("interactor", interactorPlayer)
                .bind("chatId", chatId)
                .bind("messageId", messageId)
                .bind("createdAt", System.currentTimeMillis())
                .mapTo(Long.class)
                .one());
    }
}
