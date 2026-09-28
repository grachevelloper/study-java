package ru.mirea.library.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import ru.mirea.library.model.BookIssueRequest;
import ru.mirea.library.model.BookIssueRequestDetails;
import ru.mirea.library.model.RequestStatus;

public interface BookIssueRequestRepository {
    BookIssueRequest create(BookIssueRequest request);
    List<BookIssueRequestDetails> findAll();
    Optional<BookIssueRequestDetails> findById(long id);
    BookIssueRequest updateDraft(BookIssueRequest request);
    boolean deleteById(long id);
    List<BookIssueRequestDetails> findByBookTitle(String query);
    List<BookIssueRequestDetails> findByReaderName(String query);
    List<BookIssueRequestDetails> findByStatus(RequestStatus status);
    List<BookIssueRequestDetails> findByRequestedAt(LocalDate from, LocalDate to);
    boolean existsActive(long readerId, long bookId, Long excludedRequestId);
    BookIssueRequest transition(long id, RequestStatus expected, RequestStatus target);
    BookIssueRequest issue(long id, LocalDate issuedOn, LocalDate dueOn);
    BookIssueRequest returnBook(long id, LocalDate returnedOn);
}
