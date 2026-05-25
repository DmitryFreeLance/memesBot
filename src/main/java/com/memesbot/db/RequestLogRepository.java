package com.memesbot.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class RequestLogRepository {
    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                chat_id INTEGER NOT NULL,
                user_id INTEGER,
                username TEXT,
                user_text TEXT NOT NULL,
                bot_text TEXT NOT NULL,
                supported_topic INTEGER NOT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            """;

    private static final String INSERT_SQL = """
            INSERT INTO requests (chat_id, user_id, username, user_text, bot_text, supported_topic)
            VALUES (?, ?, ?, ?, ?, ?);
            """;

    private final String jdbcUrl;

    public RequestLogRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public void init() {
        ensureSqliteFolderExists();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(CREATE_TABLE_SQL)) {
            statement.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize SQLite schema", e);
        }
    }

    public void log(long chatId, long userId, String username, String userText, String botText, boolean supportedTopic) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setLong(1, chatId);
            statement.setLong(2, userId);
            statement.setString(3, username);
            statement.setString(4, userText);
            statement.setString(5, botText);
            statement.setInt(6, supportedTopic ? 1 : 0);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save request log", e);
        }
    }

    private void ensureSqliteFolderExists() {
        if (!jdbcUrl.startsWith("jdbc:sqlite:")) {
            return;
        }

        String rawPath = jdbcUrl.substring("jdbc:sqlite:".length());
        Path dbPath = Path.of(rawPath).toAbsolutePath();
        Path parent = dbPath.getParent();
        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create SQLite directory: " + parent, e);
        }
    }
}
