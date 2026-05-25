package com.memesbot.config;

import java.util.Map;

public record BotConfig(
        String telegramToken,
        String telegramUsername,
        String kieApiKey,
        String kieApiUrl,
        String sqliteJdbcUrl
) {
    private static final String DEFAULT_KIE_API_URL = "https://api.kie.ai/gemini-3-flash/v1/chat/completions";
    private static final String DEFAULT_SQLITE_URL = "jdbc:sqlite:data/memesbot.db";

    public static BotConfig fromEnv() {
        Map<String, String> env = System.getenv();

        String telegramToken = require(env, "TELEGRAM_BOT_TOKEN");
        String telegramUsername = require(env, "TELEGRAM_BOT_USERNAME");
        String kieApiKey = require(env, "KIE_API_KEY");
        String kieApiUrl = env.getOrDefault("KIE_API_URL", DEFAULT_KIE_API_URL);
        String sqliteJdbcUrl = env.getOrDefault("SQLITE_JDBC_URL", DEFAULT_SQLITE_URL);

        return new BotConfig(telegramToken, telegramUsername, kieApiKey, kieApiUrl, sqliteJdbcUrl);
    }

    private static String require(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Environment variable is required: " + key);
        }
        return value;
    }
}
