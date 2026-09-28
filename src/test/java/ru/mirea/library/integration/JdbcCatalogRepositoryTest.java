package ru.mirea.library.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.mirea.library.config.ConnectionProvider;
import ru.mirea.library.exception.DataAccessException;
import ru.mirea.library.model.Book;
import ru.mirea.library.model.Reader;
import ru.mirea.library.repository.BookRepository;
import ru.mirea.library.repository.ReaderRepository;
import ru.mirea.library.repository.jdbc.JdbcBookRepository;
import ru.mirea.library.repository.jdbc.JdbcReaderRepository;

class JdbcCatalogRepositoryTest {
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:18.4-alpine3.23");

    private static ReaderRepository readerRepository;
    private static BookRepository bookRepository;
    private static boolean containerStarted;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        String externalUrl = System.getenv("TEST_DB_URL");
        ConnectionProvider connections;
        if (externalUrl == null || externalUrl.isBlank()) {
            POSTGRES.start();
            containerStarted = true;
            connections = () -> DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        } else {
            String user = System.getenv().getOrDefault("TEST_DB_USER", "postgres");
            String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "postgres");
            connections = () -> DriverManager.getConnection(externalUrl, user, password);
        }
        try (Connection connection = connections.getConnection();
             Statement statement = connection.createStatement()) {
            if (!containerStarted) {
                statement.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
            }
            statement.execute(Files.readString(Path.of("docker/db/init/01-schema.sql")));
            statement.execute(Files.readString(Path.of("docker/db/init/02-seed.sql")));
        }
        readerRepository = new JdbcReaderRepository(connections);
        bookRepository = new JdbcBookRepository(connections);
    }

    @AfterAll
    static void stopDatabase() {
        if (containerStarted) POSTGRES.stop();
    }

    @Test
    void savesAndReadsReader() {
        Reader saved = readerRepository.save(new Reader(
            0, "CARD-006", "Елена Кузнецова", "elena@example.com", true, LocalDate.of(2026, 9, 27)));

        assertEquals(saved, readerRepository.findById(saved.getId()).orElseThrow());
        assertEquals(6, readerRepository.findAll().size());
    }

    @Test
    void translatesDuplicateReaderEmailToDataAccessException() {
        Reader duplicate = new Reader(
            0, "CARD-UNIQUE", "Другой читатель", "anna.petrova@example.com", true, LocalDate.now());

        assertThrows(DataAccessException.class, () -> readerRepository.save(duplicate));
    }

    @Test
    void savesAndReadsBook() {
        Book saved = bookRepository.save(new Book(
            0, "978-0-00-000000-1", "Тестовая книга", "Тестовый автор", 2026, 2, 2));

        assertEquals(saved, bookRepository.findById(saved.getId()).orElseThrow());
        assertEquals(9, bookRepository.findAll().size());
    }

    @Test
    void translatesDuplicateIsbnToDataAccessException() {
        Book duplicate = new Book(
            0, "978-5-17-118366-9", "Дубль", "Автор", 2026, 1, 1);

        assertThrows(DataAccessException.class, () -> bookRepository.save(duplicate));
    }

    @Test
    void seedContainsAvailableAndUnavailableBooks() {
        assertTrue(bookRepository.findAll().stream().anyMatch(book -> book.getAvailableCopies() > 0));
        assertTrue(bookRepository.findAll().stream().anyMatch(book -> book.getAvailableCopies() == 0));
    }
}
