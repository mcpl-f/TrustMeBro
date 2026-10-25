package me.fulcanelly.trust.me.bro.database;

import java.sql.Connection;
import java.sql.SQLException;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.JdbiException;
import org.jdbi.v3.core.HandleCallback;

public final class SqliteJdbi {

    private final Connection connection;
    private final Jdbi jdbi;

    public SqliteJdbi(Connection connection) {
        this.connection = connection;
        this.jdbi = Jdbi.create(connection);
    }

    public <T> T withHandle(HandleCallback<T, SQLException> callback) throws SQLException {
        synchronized (connection) {
            try {
                return jdbi.withHandle(callback);
            } catch (JdbiException exception) {
                if (exception.getCause() instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw exception;
            }
        }
    }
}
