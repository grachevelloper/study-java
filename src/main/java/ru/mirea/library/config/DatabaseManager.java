package ru.mirea.library.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public final class DatabaseManager implements ConnectionProvider {
    private final DatabaseConfig config;

    public DatabaseManager(DatabaseConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }
}
