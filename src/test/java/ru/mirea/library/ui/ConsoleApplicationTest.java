package ru.mirea.library.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.mirea.library.export.DataExporter;
import ru.mirea.library.model.Book;
import ru.mirea.library.model.BookIssueRequest;
import ru.mirea.library.model.BookIssueRequestDetails;
import ru.mirea.library.model.Reader;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.model.SystemStatistics;
import ru.mirea.library.repository.BookIssueRequestRepository;
import ru.mirea.library.repository.BookRepository;
import ru.mirea.library.repository.ReaderRepository;
import ru.mirea.library.service.BookIssueRequestService;
import ru.mirea.library.service.BookService;
import ru.mirea.library.service.ReaderService;
import ru.mirea.library.service.StatisticsService;

class ConsoleApplicationTest {
    @Test
    void remainsInSearchFilterAndSortMenusAfterAnOperation() {
        String commands = "4\n1\nкнига\n0\n5\n1\n1\n0\n6\n1\n1\n0\n0\n";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        Clock clock = Clock.systemUTC();
        EmptyReaders readers = new EmptyReaders();
        EmptyBooks books = new EmptyBooks();
        EmptyRequests requestRepository = new EmptyRequests();
        DataExporter exporter = (data, directory) -> Path.of("unused.xlsx");
        SystemStatistics emptyStatistics = new SystemStatistics(0, 0, 0, 0, 0, Map.of(), 0);
        ConsoleApplication application = new ConsoleApplication(
            new ReaderService(readers, clock), new BookService(books),
            new BookIssueRequestService(requestRepository, readers, books, clock),
            new StatisticsService(today -> emptyStatistics, clock), exporter,
            new ConsoleInput(new ByteArrayInputStream(commands.getBytes(StandardCharsets.UTF_8)), output),
            new ConsolePrinter(output));

        application.run();

        String transcript = bytes.toString(StandardCharsets.UTF_8);
        assertEquals(2, occurrences(transcript, "1. По названию книги"));
        assertEquals(2, occurrences(transcript, "1. По статусу"));
        assertEquals(2, occurrences(transcript, "1. По дате заявки"));
    }

    private static int occurrences(String value, String marker) {
        return (value.length() - value.replace(marker, "").length()) / marker.length();
    }

    private static final class EmptyReaders implements ReaderRepository {
        @Override public Reader save(Reader reader) { return reader; }
        @Override public List<Reader> findAll() { return List.of(); }
        @Override public Optional<Reader> findById(long id) { return Optional.empty(); }
    }

    private static final class EmptyBooks implements BookRepository {
        @Override public Book save(Book book) { return book; }
        @Override public List<Book> findAll() { return List.of(); }
        @Override public Optional<Book> findById(long id) { return Optional.empty(); }
    }

    private static final class EmptyRequests implements BookIssueRequestRepository {
        @Override public BookIssueRequest create(BookIssueRequest request) { return request; }
        @Override public List<BookIssueRequestDetails> findAll() { return List.of(); }
        @Override public Optional<BookIssueRequestDetails> findById(long id) { return Optional.empty(); }
        @Override public BookIssueRequest updateDraft(BookIssueRequest request) { return request; }
        @Override public boolean deleteById(long id) { return false; }
        @Override public List<BookIssueRequestDetails> findByBookTitle(String query) { return List.of(); }
        @Override public List<BookIssueRequestDetails> findByReaderName(String query) { return List.of(); }
        @Override public List<BookIssueRequestDetails> findByStatus(RequestStatus status) { return List.of(); }
        @Override public List<BookIssueRequestDetails> findByRequestedAt(LocalDate from, LocalDate to) { return List.of(); }
        @Override public boolean existsActive(long readerId, long bookId, Long excludedRequestId) { return false; }
        @Override public BookIssueRequest transition(long id, RequestStatus expected, RequestStatus target) { throw new UnsupportedOperationException(); }
        @Override public BookIssueRequest issue(long id, LocalDate issuedOn, LocalDate dueOn) { throw new UnsupportedOperationException(); }
        @Override public BookIssueRequest returnBook(long id, LocalDate returnedOn) { throw new UnsupportedOperationException(); }
    }
}
