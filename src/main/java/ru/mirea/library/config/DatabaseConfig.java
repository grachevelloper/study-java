package ru.mirea.library.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public record DatabaseConfig(String url, String user, String password) {
    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/library_db";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASSWORD = "postgres";
    private static final String DEFAULT_ENV_FILE = ".env";

    public DatabaseConfig {
        url = requireNotBlank(url, "DB_URL");
        user = requireNotBlank(user, "DB_USER");
        password = requireNotBlank(password, "DB_PASSWORD");
    }

    public static DatabaseConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static DatabaseConfig fromEnvironment(Map<String, String> environment) {
        String envFile = environment.getOrDefault("DB_ENV_FILE", DEFAULT_ENV_FILE);
        Map<String, String> dotenv = readDotenv(Path.of(envFile));
        return new DatabaseConfig(
            value("DB_URL", environment, dotenv, DEFAULT_URL),
            value("DB_USER", environment, dotenv, DEFAULT_USER),
            value("DB_PASSWORD", environment, dotenv, DEFAULT_PASSWORD)
        );
    }

    private static String value(String name, Map<String, String> environment,
                                Map<String, String> dotenv, String defaultValue) {
        if (environment.containsKey(name)) return environment.get(name);
        return dotenv.getOrDefault(name, defaultValue);
    }

    private static Map<String, String> readDotenv(Path path) {
        if (!Files.exists(path)) return Map.of();
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String sourceLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = sourceLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).trim();
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    throw new IllegalArgumentException("Некорректная строка в " + path + ": " + sourceLine);
                }
                String name = line.substring(0, separator).trim();
                String value = unquote(line.substring(separator + 1).trim());
                values.put(name, value);
            }
            return values;
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось прочитать файл " + path + ".", exception);
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " не может быть пустым.");
        }
        return value;
    }
}
