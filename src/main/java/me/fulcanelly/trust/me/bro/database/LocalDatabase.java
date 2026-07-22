package me.fulcanelly.trust.me.bro.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.bukkit.plugin.Plugin;

public final class LocalDatabase implements AutoCloseable {

    private final Connection connection;

    public LocalDatabase(Plugin plugin) throws SQLException {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File databaseFile = new File(plugin.getDataFolder(), "database.sqlite3");
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        this.connection.setAutoCommit(true);
    }

    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
