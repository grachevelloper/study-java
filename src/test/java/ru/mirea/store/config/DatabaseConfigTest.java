package ru.mirea.store.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseConfigTest {

  @TempDir
  Path directory;

  @Test
  void usesStoreDefaultsWhenNoEnvironmentOrDotenvExists() {
    DatabaseConfig value = DatabaseConfig.fromEnvironment(
        Map.of("DB_ENV_FILE", directory.resolve("missing").toString()));
    assertEquals(
        "jdbc:postgresql://localhost:5432/online_store_db",
        value.url());
    assertEquals("postgres", value.user());
  }
}
