package ru.mirea.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.mirea.library.exception.BusinessRuleException;
import ru.mirea.library.model.Book;
import ru.mirea.library.model.BookIssueRequest;
import ru.mirea.library.model.BookIssueRequestDetails;
import ru.mirea.library.model.Reader;
import ru.mirea.library.model.RequestSortField;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.model.SortDirection;
import ru.mirea.library.repository.BookIssueRequestRepository;
import ru.mirea.library.repository.BookRepository;
import ru.mirea.library.repository.ReaderRepository;

class RequestSortingTest {
    private final BookIssueRequestService service = new BookIssueRequestService(
        new StubRequestRepository(), new StubReaderRepository(), new StubBookRepository(),
        Clock.fixed(LocalDateTime.of(2026, 9, 27, 9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    @Test
    void sortsDueDatesAscendingWithNullAlwaysLast() {
        List<BookIssueRequestDetails> sorted = service.sort(source(),
            RequestSortField.DUE_ON, SortDirection.ASCENDING);

        assertEquals(2L, sorted.get(0).getRequest().getId());
        assertEquals(1L, sorted.get(1).getRequest().getId());
        assertNull(sorted.get(2).getRequest().getDueOn());
    }

    @Test
    void sortsDueDatesDescendingWithNullAlwaysLast() {
        List<BookIssueRequestDetails> sorted = service.sort(source(),
            RequestSortField.DUE_ON, SortDirection.DESCENDING);

        assertEquals(1L, sorted.get(0).getRequest().getId());
        assertEquals(2L, sorted.get(1).getRequest().getId());
        assertNull(sorted.get(2).getRequest().getDueOn());
    }

    @Test
    void sortsRequestedAtWithoutMutatingSource() {
        List<BookIssueRequestDetails> source = source();
        List<BookIssueRequestDetails> sorted = service.sort(source,
            RequestSortField.REQUESTED_AT, SortDirection.DESCENDING);

        assertEquals(3L, sorted.get(0).getRequest().getId());
        assertEquals(1L, source.get(0).getRequest().getId());
    }

    @Test
    void validatesQueryArguments() {
        assertThrows(BusinessRuleException.class, () -> service.searchByBookTitle("  "));
        assertThrows(BusinessRuleException.class, () -> service.searchByReaderName(null));
        assertThrows(BusinessRuleException.class, () -> service.filterByRequestedAt(
            LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 27)));
    }

    private static List<BookIssueRequestDetails> source() {
        return List.of(
            details(1, LocalDateTime.of(2026, 9, 25, 9, 0), LocalDate.of(2026, 10, 10)),
            details(2, LocalDateTime.of(2026, 9, 26, 9, 0), LocalDate.of(2026, 10, 5)),
            details(3, LocalDateTime.of(2026, 9, 27, 9, 0), null)
        );
    }

    private static BookIssueRequestDetails details(long id, LocalDateTime requestedAt, LocalDate dueOn) {
        return new BookIssueRequestDetails(new BookIssueRequest(
            id, 1, 1, dueOn == null ? RequestStatus.CREATED : RequestStatus.ISSUED,
            requestedAt, dueOn == null ? null : dueOn.minusDays(30), dueOn, null, null),
            "Reader", "Book", "Author");
    }

    private static final class StubRequestRepository implements BookIssueRequestRepository {
        @Override public BookIssueRequest create(BookIssueRequest value) { throw new UnsupportedOperationException(); }
        @Override public List<BookIssueRequestDetails> findAll() { return List.of(); }
        @Override public Optional<BookIssueRequestDetails> findById(long id) { return Optional.empty(); }
        @Override public BookIssueRequest updateDraft(BookIssueRequest value) { throw new UnsupportedOperationException(); }
        @Override public boolean deleteById(long id) { return false; }
        @Override public List<BookIssueRequestDetails> findByBookTitle(String query) { return List.of(); }
        @Override public List<BookIssueRequestDetails> findByReaderName(String query) { return List.of(); }
        @Override public List<BookIssueRequestDetails> findByStatus(RequestStatus status) { return List.of(); }
        @Override public List<BookIssueRequestDetails> findByRequestedAt(LocalDate from, LocalDate to) { return List.of(); }
        @Override public boolean existsActive(long readerId, long bookId, Long excludedId) { return false; }
        @Override public BookIssueRequest transition(long id, RequestStatus expected, RequestStatus target) { throw new UnsupportedOperationException(); }
        @Override public BookIssueRequest issue(long id, LocalDate issuedOn, LocalDate dueOn) { throw new UnsupportedOperationException(); }
        @Override public BookIssueRequest returnBook(long id, LocalDate returnedOn) { throw new UnsupportedOperationException(); }
    }

    private static final class StubReaderRepository implements ReaderRepository {
        @Override public Reader save(Reader reader) { throw new UnsupportedOperationException(); }
        @Override public List<Reader> findAll() { return List.of(); }
        @Override public Optional<Reader> findById(long id) { return Optional.empty(); }
    }

    private static final class StubBookRepository implements BookRepository {
        @Override public Book save(Book book) { throw new UnsupportedOperationException(); }
        @Override public List<Book> findAll() { return List.of(); }
        @Override public Optional<Book> findById(long id) { return Optional.empty(); }
    }
}
