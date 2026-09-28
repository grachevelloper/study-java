package ru.mirea.store.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.mirea.store.config.ConnectionProvider;

final class TestDatabase {

  private static final PostgreSQLContainer<?> CONTAINER =
    new PostgreSQLContainer<>("postgres:18.4-alpine3.23");
  private static final Map<String, String> DOTENV = readDotenv();
  private static boolean started;
  private static ConnectionProvider provider;

  private TestDatabase() {}

  static ConnectionProvider start() {
    if (provider != null) return provider;
    String url = setting("TEST_DB_URL");
    if (url == null || url.isBlank()) {
      CONTAINER.start();
      started = true;
      provider = () ->
        DriverManager.getConnection(
          CONTAINER.getJdbcUrl(),
          CONTAINER.getUsername(),
          CONTAINER.getPassword()
        );
    } else {
      String user = settingOrDefault("TEST_DB_USER", "postgres");
      String password = settingOrDefault("TEST_DB_PASSWORD", "postgres");
      provider = () -> DriverManager.getConnection(url, user, password);
    }
    return provider;
  }

  private static String setting(String name) {
    if (System.getenv().containsKey(name)) return System.getenv(name);
    return DOTENV.get(name);
  }

  private static String settingOrDefault(String name, String fallback) {
    String value = setting(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static Map<String, String> readDotenv() {
    Path path = Path.of(
      System.getenv().getOrDefault("DB_ENV_FILE", ".env")
    );
    if (!Files.exists(path)) return Map.of();
    Map<String, String> values = new LinkedHashMap<>();
    try {
      for (String source : Files.readAllLines(path, StandardCharsets.UTF_8)) {
        String line = source.trim();
        if (line.isEmpty() || line.startsWith("#")) continue;
        if (line.startsWith("export ")) line = line.substring(7).trim();
        int separator = line.indexOf('=');
        if (separator <= 0) throw new IllegalArgumentException(
          "Некорректная строка в " + path + ": " + source
        );
        values.put(
          line.substring(0, separator).trim(),
          unquote(line.substring(separator + 1).trim())
        );
      }
      return values;
    } catch (IOException exception) {
      throw new IllegalStateException(
        "Не удалось прочитать файл " + path + ".",
        exception
      );
    }
  }

  private static String unquote(String value) {
    if (
      value.length() >= 2 &&
      ((value.startsWith("\"") && value.endsWith("\"")) ||
        (value.startsWith("'") && value.endsWith("'")))
    ) return value.substring(1, value.length() - 1);
    return value;
  }

  static void reset() throws Exception {
    try (
      Connection c = start().getConnection();
      Statement s = c.createStatement()
    ) {
      s.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
      s.execute(Files.readString(Path.of("docker/db/init/01-schema.sql")));
      s.execute(Files.readString(Path.of("docker/db/init/02-seed.sql")));
    }
  }

  static void stop() {
    if (started) {
      CONTAINER.stop();
      started = false;
      provider = null;
    }
  }
}
