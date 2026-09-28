package ru.mirea.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mirea.library.exception.BusinessRuleException;
import ru.mirea.library.exception.EntityNotFoundException;
import ru.mirea.library.model.Book;
import ru.mirea.library.model.BookIssueRequest;
import ru.mirea.library.model.BookIssueRequestDetails;
import ru.mirea.library.model.Reader;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.repository.BookIssueRequestRepository;
import ru.mirea.library.repository.BookRepository;
import ru.mirea.library.repository.ReaderRepository;

class BookIssueRequestServiceTest {
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-09-27T09:00:00Z"), ZoneOffset.UTC);

    private FakeReaderRepository readers;
    private FakeBookRepository books;
    private FakeRequestRepository requests;
    private BookIssueRequestService service;
    private long readerId;
    private long bookId;

    @BeforeEach
    void setUp() {
        readers = new FakeReaderRepository();
        books = new FakeBookRepository();
        requests = new FakeRequestRepository();
        readerId = readers.save(new Reader(
            0, "CARD-1", "Анна Петрова", "anna@example.com", true, LocalDate.of(2025, 1, 1))).getId();
        bookId = books.save(new Book(
            0, "ISBN-1", "Книга", "Автор", 2020, 2, 2)).getId();
        service = new BookIssueRequestService(requests, readers, books, CLOCK);
    }

    @Test
    void rejectsRequestFromInactiveReader() {
        long inactiveReaderId = readers.save(new Reader(
            0, "CARD-2", "Борис", "boris@example.com", false, LocalDate.of(2025, 1, 1))).getId();

        assertThrows(BusinessRuleException.class,
            () -> service.createRequest(inactiveReaderId, bookId, "note"));
    }

    @Test
    void rejectsRequestForMissingReader() {
        assertThrows(EntityNotFoundException.class,
            () -> service.createRequest(9999L, bookId, "note"));
    }

    @Test
    void rejectsTooLongNote() {
        assertThrows(BusinessRuleException.class,
            () -> service.createRequest(readerId, bookId, "x".repeat(501)));
    }

    @Test
    void createsNormalizedDraft() {
        BookIssueRequest created = service.createRequest(readerId, bookId, "  note  ");

        assertEquals(RequestStatus.CREATED, created.getStatus());
        assertEquals("note", created.getNote());
        assertEquals(LocalDateTime.of(2026, 9, 27, 9, 0), created.getRequestedAt());
    }

    @Test
    void normalizesBlankNoteToNull() {
        assertNull(service.createRequest(readerId, bookId, "   ").getNote());
    }

    @Test
    void rejectsDuplicateActiveRequest() {
        service.createRequest(readerId, bookId, null);

        assertThrows(BusinessRuleException.class,
            () -> service.createRequest(readerId, bookId, null));
    }

    @Test
    void editsOnlyCreatedRequests() {
        BookIssueRequest approved = requests.put(new BookIssueRequest(
            0, readerId, bookId, RequestStatus.APPROVED,
            LocalDateTime.now(CLOCK), null, null, null, null));

        assertThrows(BusinessRuleException.class,
            () -> service.updateDraft(approved.getId(), readerId, bookId, "new"));
    }

    @Test
    void rejectsDeletingIssuedHistory() {
        BookIssueRequest issued = requests.put(new BookIssueRequest(
            0, readerId, bookId, RequestStatus.ISSUED,
            LocalDateTime.now(CLOCK), LocalDate.now(CLOCK),
            LocalDate.now(CLOCK).plusDays(30), null, null));

        assertThrows(BusinessRuleException.class, () -> service.delete(issued.getId()));
    }

    @Test
    void validatesReaderRequiredStrings() {
        ReaderService readerService = new ReaderService(readers, CLOCK);

        assertThrows(BusinessRuleException.class,
            () -> readerService.create("CARD", "   ", "reader@example.com"));
    }

    @Test
    void validatesBookTitleAndCopyCount() {
        BookService bookService = new BookService(books);

        assertThrows(BusinessRuleException.class,
            () -> bookService.create("ISBN", " ", "Author", 2020, 1));
        assertThrows(BusinessRuleException.class,
            () -> bookService.create("ISBN", "Title", "Author", 2020, 0));
    }

    @Test
    void changesStatusAndUsesClockForIssueDates() {
        BookIssueRequest created = service.createRequest(readerId, bookId, null);
        service.changeStatus(created.getId(), RequestStatus.APPROVED);

        BookIssueRequest issued = service.changeStatus(created.getId(), RequestStatus.ISSUED);

        assertEquals(LocalDate.of(2026, 9, 27), issued.getIssuedOn());
        assertEquals(LocalDate.of(2026, 10, 27), issued.getDueOn());
    }

    @Test
    void rejectsForbiddenStatusTransition() {
        BookIssueRequest created = service.createRequest(readerId, bookId, null);

        assertThrows(BusinessRuleException.class,
            () -> service.changeStatus(created.getId(), RequestStatus.RETURNED));
    }

    @Test
    void rejectsDateRangeOutsideSupportedDatabaseBounds() {
        assertThrows(BusinessRuleException.class,
            () -> service.filterByRequestedAt(LocalDate.of(2026, 1, 1), LocalDate.MAX));
    }

    private static final class FakeReaderRepository implements ReaderRepository {
        private final Map<Long, Reader> values = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public Reader save(Reader reader) {
            Reader saved = new Reader(nextId++, reader.getLibraryCardNumber(), reader.getFullName(),
                reader.getEmail(), reader.isActive(), reader.getRegisteredAt());
            values.put(saved.getId(), saved);
            return saved;
        }

        @Override public List<Reader> findAll() { return new ArrayList<>(values.values()); }
        @Override public Optional<Reader> findById(long id) { return Optional.ofNullable(values.get(id)); }
    }

    private static final class FakeBookRepository implements BookRepository {
        private final Map<Long, Book> values = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public Book save(Book book) {
            Book saved = new Book(nextId++, book.getIsbn(), book.getTitle(), book.getAuthor(),
                book.getPublicationYear(), book.getTotalCopies(), book.getAvailableCopies());
            values.put(saved.getId(), saved);
            return saved;
        }

        @Override public List<Book> findAll() { return new ArrayList<>(values.values()); }
        @Override public Optional<Book> findById(long id) { return Optional.ofNullable(values.get(id)); }
    }

    private static final class FakeRequestRepository implements BookIssueRequestRepository {
        private final Map<Long, BookIssueRequest> values = new LinkedHashMap<>();
        private long nextId = 1;

        BookIssueRequest put(BookIssueRequest request) {
            BookIssueRequest saved = new BookIssueRequest(nextId++, request.getReaderId(), request.getBookId(),
                request.getStatus(), request.getRequestedAt(), request.getIssuedOn(), request.getDueOn(),
                request.getReturnedOn(), request.getNote());
            values.put(saved.getId(), saved);
            return saved;
        }

        @Override public BookIssueRequest create(BookIssueRequest request) { return put(request); }
        @Override public List<BookIssueRequestDetails> findAll() { return values.values().stream().map(this::details).toList(); }
        @Override public Optional<BookIssueRequestDetails> findById(long id) {
            return Optional.ofNullable(values.get(id)).map(this::details);
        }
        @Override public BookIssueRequest updateDraft(BookIssueRequest request) {
            values.put(request.getId(), request);
            return request;
        }
        @Override public boolean deleteById(long id) { return values.remove(id) != null; }
        @Override public boolean existsActive(long readerId, long bookId, Long excludedId) {
            return values.values().stream().anyMatch(request -> request.getReaderId() == readerId
                && request.getBookId() == bookId
                && (excludedId == null || request.getId() != excludedId)
                && (request.getStatus() == RequestStatus.CREATED
                    || request.getStatus() == RequestStatus.APPROVED
                    || request.getStatus() == RequestStatus.ISSUED));
        }
        @Override public List<BookIssueRequestDetails> findByBookTitle(String query) { return List.of(); }
        @Override public List<BookIssueRequestDetails> findByReaderName(String query) { return List.of(); }
        @Override public List<BookIssueRequestDetails> findByStatus(RequestStatus status) { return List.of(); }
        @Override public List<BookIssueRequestDetails> findByRequestedAt(LocalDate from, LocalDate to) { return List.of(); }
        @Override public BookIssueRequest transition(long id, RequestStatus expected, RequestStatus target) {
            BookIssueRequest current = values.get(id);
            if (current == null || current.getStatus() != expected) throw new BusinessRuleException("stale");
            return replace(current, target, current.getIssuedOn(), current.getDueOn(), current.getReturnedOn());
        }
        @Override public BookIssueRequest issue(long id, LocalDate issuedOn, LocalDate dueOn) {
            return replace(values.get(id), RequestStatus.ISSUED, issuedOn, dueOn, null);
        }
        @Override public BookIssueRequest returnBook(long id, LocalDate returnedOn) {
            BookIssueRequest current = values.get(id);
            return replace(current, RequestStatus.RETURNED, current.getIssuedOn(), current.getDueOn(), returnedOn);
        }

        private BookIssueRequest replace(BookIssueRequest current, RequestStatus status,
                                         LocalDate issuedOn, LocalDate dueOn, LocalDate returnedOn) {
            BookIssueRequest replacement = new BookIssueRequest(current.getId(), current.getReaderId(),
                current.getBookId(), status, current.getRequestedAt(), issuedOn, dueOn,
                returnedOn, current.getNote());
            values.put(replacement.getId(), replacement);
            return replacement;
        }

        private BookIssueRequestDetails details(BookIssueRequest request) {
            return new BookIssueRequestDetails(request, "Reader", "Book", "Author");
        }
    }
}
