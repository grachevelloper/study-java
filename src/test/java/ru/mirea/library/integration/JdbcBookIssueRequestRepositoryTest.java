package ru.mirea.library.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.mirea.library.config.ConnectionProvider;
import ru.mirea.library.exception.BusinessRuleException;
import ru.mirea.library.exception.DataAccessException;
import ru.mirea.library.model.Book;
import ru.mirea.library.model.BookIssueRequest;
import ru.mirea.library.model.BookIssueRequestDetails;
import ru.mirea.library.model.Reader;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.model.SystemStatistics;
import ru.mirea.library.repository.BookIssueRequestRepository;
import ru.mirea.library.repository.BookRepository;
import ru.mirea.library.repository.ReaderRepository;
import ru.mirea.library.repository.jdbc.JdbcBookIssueRequestRepository;
import ru.mirea.library.repository.jdbc.JdbcBookRepository;
import ru.mirea.library.repository.jdbc.JdbcReaderRepository;
import ru.mirea.library.repository.jdbc.JdbcStatisticsRepository;

class JdbcBookIssueRequestRepositoryTest {
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:18.4-alpine3.23");

    private static ConnectionProvider connections;
    private static boolean containerStarted;
    private BookIssueRequestRepository requests;
    private ReaderRepository readers;
    private BookRepository books;

    @BeforeAll
    static void startDatabase() {
        String externalUrl = System.getenv("TEST_DB_URL");
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
    }

    @AfterAll
    static void stopDatabase() {
        if (containerStarted) POSTGRES.stop();
    }

    @BeforeEach
    void resetDatabase() throws Exception {
        try (Connection connection = connections.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
            statement.execute(Files.readString(Path.of("docker/db/init/01-schema.sql")));
            statement.execute(Files.readString(Path.of("docker/db/init/02-seed.sql")));
        }
        requests = new JdbcBookIssueRequestRepository(connections);
        readers = new JdbcReaderRepository(connections);
        books = new JdbcBookRepository(connections);
    }

    @Test
    void supportsDraftCrudSearchAndFilters() {
        BookIssueRequest created = requests.create(new BookIssueRequest(
            0, 1, 8, RequestStatus.CREATED, LocalDateTime.of(2026, 9, 27, 10, 0),
            null, null, null, "draft"));
        assertEquals(created, requests.findById(created.getId()).orElseThrow().getRequest());

        BookIssueRequest updated = requests.updateDraft(new BookIssueRequest(
            created.getId(), 2, 7, RequestStatus.CREATED, created.getRequestedAt(),
            null, null, null, "updated"));
        assertEquals("updated", updated.getNote());

        assertFalse(requests.findByBookTitle("негин").isEmpty());
        assertFalse(requests.findByReaderName("пЕтРов").isEmpty());
        assertTrue(requests.findByStatus(RequestStatus.ISSUED).size() >= 2);
        List<BookIssueRequestDetails> byDate = requests.findByRequestedAt(
            LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 27));
        assertTrue(byDate.stream().anyMatch(item -> item.getRequest().getId() == created.getId()));
        assertTrue(requests.existsActive(2, 7, null));
        assertFalse(requests.existsActive(2, 7, created.getId()));

        assertTrue(requests.deleteById(created.getId()));
        assertTrue(requests.findById(created.getId()).isEmpty());
    }

    @Test
    void issuesAndReturnsAtomically() {
        long requestId = 2;
        long bookId = 2;
        int before = books.findById(bookId).orElseThrow().getAvailableCopies();
        LocalDate issuedOn = LocalDate.of(2026, 9, 27);

        BookIssueRequest issued = requests.issue(requestId, issuedOn, issuedOn.plusDays(30));
        assertEquals(RequestStatus.ISSUED, issued.getStatus());
        assertEquals(before - 1, books.findById(bookId).orElseThrow().getAvailableCopies());

        BookIssueRequest returned = requests.returnBook(requestId, issuedOn.plusDays(5));
        assertEquals(RequestStatus.RETURNED, returned.getStatus());
        assertEquals(before, books.findById(bookId).orElseThrow().getAvailableCopies());
    }

    @Test
    void issuingWithoutStockChangesNeitherTable() {
        Reader reader = readers.save(new Reader(0, "CARD-X", "Reader X", "x@example.com", true, LocalDate.now()));
        Book book = books.save(new Book(0, "ISBN-X", "Book X", "Author X", 2026, 1, 0));
        BookIssueRequest request = requests.create(new BookIssueRequest(
            0, reader.getId(), book.getId(), RequestStatus.CREATED, LocalDateTime.now(), null, null, null, null));
        requests.transition(request.getId(), RequestStatus.CREATED, RequestStatus.APPROVED);

        assertThrows(BusinessRuleException.class,
            () -> requests.issue(request.getId(), LocalDate.now(), LocalDate.now().plusDays(30)));
        assertEquals(0, books.findById(book.getId()).orElseThrow().getAvailableCopies());
        assertEquals(RequestStatus.APPROVED,
            requests.findById(request.getId()).orElseThrow().getRequest().getStatus());
    }

    @Test
    void onlyOneConcurrentIssueGetsTheLastCopy() throws Exception {
        Book book = books.save(new Book(0, "ISBN-LAST", "Last copy", "Author", 2026, 1, 1));
        Reader firstReader = readers.save(new Reader(0, "CARD-A", "Reader A", "a@example.com", true, LocalDate.now()));
        Reader secondReader = readers.save(new Reader(0, "CARD-B", "Reader B", "b@example.com", true, LocalDate.now()));
        long first = approvedRequest(firstReader.getId(), book.getId());
        long second = approvedRequest(secondReader.getId(), book.getId());
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstResult = executor.submit(() -> attemptIssue(first, start));
            Future<Boolean> secondResult = executor.submit(() -> attemptIssue(second, start));
            start.countDown();
            long successCount = List.of(firstResult.get(), secondResult.get()).stream()
                .filter(Boolean::booleanValue).count();

            assertEquals(1, successCount);
        }
        assertEquals(0, books.findById(book.getId()).orElseThrow().getAvailableCopies());
        long issuedCount = List.of(first, second).stream()
            .map(requests::findById).map(OptionalValue::status).filter(status -> status == RequestStatus.ISSUED).count();
        assertEquals(1, issuedCount);
    }

    @Test
    void rollsBackStockWhenRequestUpdateFails() throws Exception {
        int before = books.findById(2).orElseThrow().getAvailableCopies();
        try (Connection connection = connections.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE FUNCTION fail_issue() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.status = 'ISSUED' THEN RAISE EXCEPTION 'forced failure'; END IF;
                    RETURN NEW;
                END $$;
                CREATE TRIGGER fail_issue_trigger BEFORE UPDATE ON book_issue_requests
                FOR EACH ROW EXECUTE FUNCTION fail_issue();
                """);
        }
        try {
            assertThrows(DataAccessException.class,
                () -> requests.issue(2, LocalDate.now(), LocalDate.now().plusDays(30)));
            assertEquals(before, books.findById(2).orElseThrow().getAvailableCopies());
            assertEquals(RequestStatus.APPROVED,
                requests.findById(2).orElseThrow().getRequest().getStatus());
        } finally {
            try (Connection connection = connections.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER IF EXISTS fail_issue_trigger ON book_issue_requests");
                statement.execute("DROP FUNCTION IF EXISTS fail_issue()");
            }
        }
    }

    @Test
    void rollsBackReturnedStockWhenRequestUpdateFails() throws Exception {
        int before = books.findById(1).orElseThrow().getAvailableCopies();
        try (Connection connection = connections.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE FUNCTION fail_return() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.status = 'RETURNED' THEN RAISE EXCEPTION 'forced failure'; END IF;
                    RETURN NEW;
                END $$;
                CREATE TRIGGER fail_return_trigger BEFORE UPDATE ON book_issue_requests
                FOR EACH ROW EXECUTE FUNCTION fail_return();
                """);
        }
        try {
            assertThrows(DataAccessException.class,
                () -> requests.returnBook(3, LocalDate.now()));
            assertEquals(before, books.findById(1).orElseThrow().getAvailableCopies());
            assertEquals(RequestStatus.ISSUED,
                requests.findById(3).orElseThrow().getRequest().getStatus());
        } finally {
            try (Connection connection = connections.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER IF EXISTS fail_return_trigger ON book_issue_requests");
                statement.execute("DROP FUNCTION IF EXISTS fail_return()");
            }
        }
    }

    @Test
    void databaseRejectsDuplicateActiveReaderBookPair() {
        BookIssueRequest duplicate = new BookIssueRequest(
            0, 1, 1, RequestStatus.CREATED, LocalDateTime.now(), null, null, null, null);

        assertThrows(DataAccessException.class, () -> requests.create(duplicate));
        assertEquals(1, requests.findAll().stream()
            .filter(details -> details.getRequest().getReaderId() == 1)
            .filter(details -> details.getRequest().getBookId() == 1)
            .filter(details -> details.getRequest().getStatus() == RequestStatus.CREATED)
            .count());
    }

    @Test
    void loadsAllStatisticsFromPostgres() {
        SystemStatistics statistics = new JdbcStatisticsRepository(connections)
            .load(LocalDate.now());

        assertEquals(5, statistics.getReaderCount());
        assertEquals(8, statistics.getBookTitleCount());
        assertEquals(18, statistics.getTotalCopyCount());
        assertEquals(15, statistics.getAvailableCopyCount());
        assertEquals(10, statistics.getRequestCount());
        assertEquals(3, statistics.getRequestsByStatus().get(RequestStatus.ISSUED));
        assertEquals(2, statistics.getOverdueRequestCount());
    }

    private long approvedRequest(long readerId, long bookId) {
        BookIssueRequest created = requests.create(new BookIssueRequest(
            0, readerId, bookId, RequestStatus.CREATED, LocalDateTime.now(), null, null, null, null));
        return requests.transition(created.getId(), RequestStatus.CREATED, RequestStatus.APPROVED).getId();
    }

    private boolean attemptIssue(long requestId, CountDownLatch start) throws Exception {
        start.await();
        try {
            requests.issue(requestId, LocalDate.now(), LocalDate.now().plusDays(30));
            return true;
        } catch (BusinessRuleException exception) {
            return false;
        }
    }

    private static final class OptionalValue {
        private OptionalValue() { }

        static RequestStatus status(java.util.Optional<BookIssueRequestDetails> value) {
            return value.orElseThrow().getRequest().getStatus();
        }
    }
}
