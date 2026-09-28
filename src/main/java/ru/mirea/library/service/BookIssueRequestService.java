package ru.mirea.library.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import ru.mirea.library.exception.BusinessRuleException;
import ru.mirea.library.exception.EntityNotFoundException;
import ru.mirea.library.model.Book;
import ru.mirea.library.model.BookIssueRequest;
import ru.mirea.library.model.BookIssueRequestDetails;
import ru.mirea.library.model.Reader;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.model.RequestSortField;
import ru.mirea.library.model.SortDirection;
import ru.mirea.library.repository.BookIssueRequestRepository;
import ru.mirea.library.repository.BookRepository;
import ru.mirea.library.repository.ReaderRepository;

public final class BookIssueRequestService {
    private static final LocalDate MIN_SUPPORTED_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate MAX_SUPPORTED_DATE = LocalDate.of(9999, 12, 31);
    private static final Set<RequestStatus> DELETABLE_STATUSES =
        Set.of(RequestStatus.CREATED, RequestStatus.REJECTED, RequestStatus.CANCELLED);

    private final BookIssueRequestRepository requestRepository;
    private final ReaderRepository readerRepository;
    private final BookRepository bookRepository;
    private final Clock clock;

    public BookIssueRequestService(BookIssueRequestRepository requestRepository,
                                   ReaderRepository readerRepository,
                                   BookRepository bookRepository,
                                   Clock clock) {
        this.requestRepository = Objects.requireNonNull(requestRepository);
        this.readerRepository = Objects.requireNonNull(readerRepository);
        this.bookRepository = Objects.requireNonNull(bookRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public BookIssueRequest createRequest(long readerId, long bookId, String note) {
        Reader reader = requireReader(readerId);
        requireActive(reader);
        requireBook(bookId);
        ensureNoDuplicate(readerId, bookId, null);
        return requestRepository.create(new BookIssueRequest(
            0, readerId, bookId, RequestStatus.CREATED, LocalDateTime.now(clock),
            null, null, null, normalizeNote(note)));
    }

    public List<BookIssueRequestDetails> getAll() {
        return requestRepository.findAll();
    }

    public BookIssueRequestDetails getById(long id) {
        requirePositiveId(id);
        return requestRepository.findById(id)
            .orElseThrow(() -> notFound(id));
    }

    public BookIssueRequest updateDraft(long requestId, long readerId, long bookId, String note) {
        BookIssueRequest current = getById(requestId).getRequest();
        if (current.getStatus() != RequestStatus.CREATED) {
            throw new BusinessRuleException("Изменять можно только заявку в статусе CREATED.");
        }
        Reader reader = requireReader(readerId);
        requireActive(reader);
        requireBook(bookId);
        ensureNoDuplicate(readerId, bookId, requestId);
        return requestRepository.updateDraft(new BookIssueRequest(
            requestId, readerId, bookId, current.getStatus(), current.getRequestedAt(),
            current.getIssuedOn(), current.getDueOn(), current.getReturnedOn(), normalizeNote(note)));
    }

    public void delete(long requestId) {
        BookIssueRequest current = getById(requestId).getRequest();
        if (!DELETABLE_STATUSES.contains(current.getStatus())) {
            throw new BusinessRuleException("Историю фактической выдачи удалять нельзя.");
        }
        if (!requestRepository.deleteById(requestId)) {
            throw notFound(requestId);
        }
    }

    public BookIssueRequest changeStatus(long requestId, RequestStatus target) {
        if (target == null) throw new BusinessRuleException("Новый статус не задан.");
        BookIssueRequest current = getById(requestId).getRequest();
        if (!current.getStatus().canTransitionTo(target)) {
            throw new BusinessRuleException("Переход " + current.getStatus() + " -> " + target + " запрещён.");
        }
        LocalDate today = LocalDate.now(clock);
        return switch (target) {
            case ISSUED -> requestRepository.issue(requestId, today, today.plusDays(30));
            case RETURNED -> requestRepository.returnBook(requestId, today);
            default -> requestRepository.transition(requestId, current.getStatus(), target);
        };
    }

    public List<BookIssueRequestDetails> searchByBookTitle(String query) {
        return requestRepository.findByBookTitle(requireQuery(query));
    }

    public List<BookIssueRequestDetails> searchByReaderName(String query) {
        return requestRepository.findByReaderName(requireQuery(query));
    }

    public List<BookIssueRequestDetails> filterByStatus(RequestStatus status) {
        if (status == null) throw new BusinessRuleException("Статус для фильтра не задан.");
        return requestRepository.findByStatus(status);
    }

    public List<BookIssueRequestDetails> filterByRequestedAt(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessRuleException("Обе даты диапазона обязательны.");
        }
        if (from.isAfter(to)) {
            throw new BusinessRuleException("Начало диапазона не может быть позже конца.");
        }
        if (from.isBefore(MIN_SUPPORTED_DATE) || to.isAfter(MAX_SUPPORTED_DATE)) {
            throw new BusinessRuleException("Дата должна быть в диапазоне от 0001-01-01 до 9999-12-31.");
        }
        return requestRepository.findByRequestedAt(from, to);
    }

    public List<BookIssueRequestDetails> sort(List<BookIssueRequestDetails> source,
                                              RequestSortField field,
                                              SortDirection direction) {
        if (source == null || field == null || direction == null) {
            throw new BusinessRuleException("Данные и параметры сортировки обязательны.");
        }
        Comparator<LocalDateTime> dateTimeOrder = direction == SortDirection.ASCENDING
            ? Comparator.naturalOrder() : Comparator.reverseOrder();
        Comparator<LocalDate> dateOrder = direction == SortDirection.ASCENDING
            ? Comparator.naturalOrder() : Comparator.reverseOrder();
        Comparator<BookIssueRequestDetails> comparator = switch (field) {
            case REQUESTED_AT -> Comparator.comparing(
                item -> item.getRequest().getRequestedAt(), Comparator.nullsLast(dateTimeOrder));
            case DUE_ON -> Comparator.comparing(
                item -> item.getRequest().getDueOn(), Comparator.nullsLast(dateOrder));
        };
        return source.stream().sorted(comparator).toList();
    }

    private Reader requireReader(long id) {
        requirePositiveId(id);
        return readerRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Читатель с ID " + id + " не найден."));
    }

    private Book requireBook(long id) {
        requirePositiveId(id);
        return bookRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Книга с ID " + id + " не найдена."));
    }

    private static void requireActive(Reader reader) {
        if (!reader.isActive()) {
            throw new BusinessRuleException("Неактивный читатель не может создавать заявки.");
        }
    }

    private void ensureNoDuplicate(long readerId, long bookId, Long excludedRequestId) {
        if (requestRepository.existsActive(readerId, bookId, excludedRequestId)) {
            throw new BusinessRuleException("У читателя уже есть активная заявка на эту книгу.");
        }
    }

    private static String normalizeNote(String note) {
        if (note == null || note.trim().isEmpty()) return null;
        String normalized = note.trim();
        if (normalized.length() > 500) {
            throw new BusinessRuleException("Примечание не может быть длиннее 500 символов.");
        }
        return normalized;
    }

    private static String requireQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new BusinessRuleException("Поисковая строка не может быть пустой.");
        }
        return query.trim();
    }

    private static void requirePositiveId(long id) {
        if (id <= 0) throw new BusinessRuleException("ID должен быть положительным.");
    }

    private static EntityNotFoundException notFound(long id) {
        return new EntityNotFoundException("Заявка с ID " + id + " не найдена.");
    }
}
