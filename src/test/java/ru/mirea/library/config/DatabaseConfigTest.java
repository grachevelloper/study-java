package ru.mirea.library.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsDatabaseSettingsFromDotenvFile() throws Exception {
        Path dotenv = temporaryDirectory.resolve(".env");
        Files.writeString(dotenv, """
            DB_URL=jdbc:postgresql://db.example:5432/catalog
            DB_USER=dotenv-user
            DB_PASSWORD=dotenv-password
            """);

        DatabaseConfig config = DatabaseConfig.fromEnvironment(
            Map.of("DB_ENV_FILE", dotenv.toString()));

        assertEquals("jdbc:postgresql://db.example:5432/catalog", config.url());
        assertEquals("dotenv-user", config.user());
        assertEquals("dotenv-password", config.password());
    }

    @Test
    void processEnvironmentOverridesDotenvFile() throws Exception {
        Path dotenv = temporaryDirectory.resolve(".env");
        Files.writeString(dotenv, """
            DB_URL=jdbc:postgresql://dotenv:5432/library_db
            DB_USER=dotenv-user
            DB_PASSWORD=dotenv-password
            """);
        Map<String, String> environment = new HashMap<>();
        environment.put("DB_ENV_FILE", dotenv.toString());
        environment.put("DB_USER", "process-user");

        DatabaseConfig config = DatabaseConfig.fromEnvironment(environment);

        assertEquals("jdbc:postgresql://dotenv:5432/library_db", config.url());
        assertEquals("process-user", config.user());
        assertEquals("dotenv-password", config.password());
    }

    @Test
    void usesPostgresLocalDefaultsWhenDotenvFileIsMissing() {
        Path missing = temporaryDirectory.resolve("missing.env");

        DatabaseConfig config = DatabaseConfig.fromEnvironment(
            Map.of("DB_ENV_FILE", missing.toString()));

        assertEquals("jdbc:postgresql://localhost:5432/library_db", config.url());
        assertEquals("postgres", config.user());
        assertEquals("postgres", config.password());
    }
}
