package ru.mirea.store.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.mirea.store.config.ConnectionProvider;

final class TestDatabase {

  private static final PostgreSQLContainer<?> CONTAINER =
    new PostgreSQLContainer<>("postgres:18.4-alpine3.23");
  private static boolean started;
  private static ConnectionProvider provider;

  private TestDatabase() {}

  static ConnectionProvider start() {
    if (provider != null) return provider;
    String url = System.getenv("TEST_DB_URL");
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
      String user = System.getenv().getOrDefault("TEST_DB_USER", "postgres");
      String password = System.getenv().getOrDefault(
        "TEST_DB_PASSWORD",
        "postgres"
      );
      provider = () -> DriverManager.getConnection(url, user, password);
    }
    return provider;
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
