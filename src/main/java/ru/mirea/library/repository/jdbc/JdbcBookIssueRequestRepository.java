package ru.mirea.library.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import ru.mirea.library.config.ConnectionProvider;
import ru.mirea.library.exception.BusinessRuleException;
import ru.mirea.library.exception.DataAccessException;
import ru.mirea.library.exception.EntityNotFoundException;
import ru.mirea.library.model.BookIssueRequest;
import ru.mirea.library.model.BookIssueRequestDetails;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.repository.BookIssueRequestRepository;

public final class JdbcBookIssueRequestRepository implements BookIssueRequestRepository {
    private static final String REQUEST_COLUMNS =
        "id, reader_id, book_id, status, requested_at, issued_on, due_on, returned_on, note";
    private static final String DETAILS_SELECT = """
        SELECT r.id, r.reader_id, r.book_id, r.status, r.requested_at,
               r.issued_on, r.due_on, r.returned_on, r.note,
               rd.full_name AS reader_name, b.title AS book_title, b.author AS book_author
        FROM book_issue_requests r
        JOIN readers rd ON rd.id = r.reader_id
        JOIN books b ON b.id = r.book_id
        """;

    private final ConnectionProvider connectionProvider;

    public JdbcBookIssueRequestRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider);
    }

    @Override
    public BookIssueRequest create(BookIssueRequest request) {
        String sql = "INSERT INTO book_issue_requests "
            + "(reader_id, book_id, status, requested_at, issued_on, due_on, returned_on, note) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setRequestValues(statement, request);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new DataAccessException("База данных не вернула ID заявки.");
                return copyWithId(request, keys.getLong(1));
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Не удалось сохранить заявку.", exception);
        }
    }

    @Override
    public List<BookIssueRequestDetails> findAll() {
        return queryDetails(DETAILS_SELECT + " ORDER BY r.id", statement -> { });
    }

    @Override
    public Optional<BookIssueRequestDetails> findById(long id) {
        List<BookIssueRequestDetails> result = queryDetails(
            DETAILS_SELECT + " WHERE r.id = ?", statement -> statement.setLong(1, id));
        return result.stream().findFirst();
    }

    @Override
    public BookIssueRequest updateDraft(BookIssueRequest request) {
        String sql = "UPDATE book_issue_requests SET reader_id = ?, book_id = ?, note = ? "
            + "WHERE id = ? AND status = 'CREATED' RETURNING " + REQUEST_COLUMNS;
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, request.getReaderId());
            statement.setLong(2, request.getBookId());
            statement.setString(3, request.getNote());
            statement.setLong(4, request.getId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new BusinessRuleException("Заявка не найдена или уже не находится в статусе CREATED.");
                }
                return RowMappers.toRequest(resultSet);
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Не удалось изменить заявку.", exception);
        }
    }

    @Override
    public boolean deleteById(long id) {
        String sql = "DELETE FROM book_issue_requests WHERE id = ? "
            + "AND status IN ('CREATED', 'REJECTED', 'CANCELLED')";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new DataAccessException("Не удалось удалить заявку.", exception);
        }
    }

    @Override
    public List<BookIssueRequestDetails> findByBookTitle(String query) {
        return queryDetails(DETAILS_SELECT + " WHERE b.title ILIKE ? ORDER BY r.id",
            statement -> statement.setString(1, "%" + query + "%"));
    }

    @Override
    public List<BookIssueRequestDetails> findByReaderName(String query) {
        return queryDetails(DETAILS_SELECT + " WHERE rd.full_name ILIKE ? ORDER BY r.id",
            statement -> statement.setString(1, "%" + query + "%"));
    }

    @Override
    public List<BookIssueRequestDetails> findByStatus(RequestStatus status) {
        return queryDetails(DETAILS_SELECT + " WHERE r.status = ? ORDER BY r.id",
            statement -> statement.setString(1, status.name()));
    }

    @Override
    public List<BookIssueRequestDetails> findByRequestedAt(LocalDate from, LocalDate to) {
        return queryDetails(DETAILS_SELECT
                + " WHERE r.requested_at >= ? AND r.requested_at < ? ORDER BY r.id",
            statement -> {
                statement.setObject(1, from.atStartOfDay());
                statement.setObject(2, to.plusDays(1).atStartOfDay());
            });
    }

    @Override
    public boolean existsActive(long readerId, long bookId, Long excludedRequestId) {
        String sql = "SELECT EXISTS (SELECT 1 FROM book_issue_requests "
            + "WHERE reader_id = ? AND book_id = ? "
            + "AND status IN ('CREATED', 'APPROVED', 'ISSUED') "
            + "AND (? IS NULL OR id <> ?))";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, readerId);
            statement.setLong(2, bookId);
            if (excludedRequestId == null) {
                statement.setNull(3, Types.BIGINT);
                statement.setNull(4, Types.BIGINT);
            } else {
                statement.setLong(3, excludedRequestId);
                statement.setLong(4, excludedRequestId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Не удалось проверить активную заявку.", exception);
        }
    }

    @Override
    public BookIssueRequest transition(long id, RequestStatus expected, RequestStatus target) {
        String sql = "UPDATE book_issue_requests SET status = ? WHERE id = ? AND status = ? RETURNING "
            + REQUEST_COLUMNS;
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, target.name());
            statement.setLong(2, id);
            statement.setString(3, expected.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new BusinessRuleException("Статус заявки уже изменён или заявка не найдена.");
                }
                return RowMappers.toRequest(resultSet);
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Не удалось изменить статус заявки.", exception);
        }
    }

    @Override
    public BookIssueRequest issue(long id, LocalDate issuedOn, LocalDate dueOn) {
        return changeStockAndRequest(id, issuedOn, dueOn, null, true);
    }

    @Override
    public BookIssueRequest returnBook(long id, LocalDate returnedOn) {
        return changeStockAndRequest(id, null, null, returnedOn, false);
    }

    private BookIssueRequest changeStockAndRequest(long id, LocalDate issuedOn, LocalDate dueOn,
                                                   LocalDate returnedOn, boolean issuing) {
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LockedRequest locked = lockRequestAndBook(connection, id);
                RequestStatus required = issuing ? RequestStatus.APPROVED : RequestStatus.ISSUED;
                if (locked.status() != required) {
                    throw new BusinessRuleException("Операция недоступна для текущего статуса заявки.");
                }
                updateStock(connection, locked, issuing);
                BookIssueRequest changed = issuing
                    ? markIssued(connection, id, issuedOn, dueOn)
                    : markReturned(connection, id, returnedOn);
                connection.commit();
                return changed;
            } catch (RuntimeException | SQLException exception) {
                rollback(connection, exception);
                if (exception instanceof RuntimeException runtimeException) throw runtimeException;
                throw new DataAccessException("Транзакция выдачи или возврата не выполнена.", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Не удалось выполнить транзакцию заявки.", exception);
        }
    }

    private LockedRequest lockRequestAndBook(Connection connection, long id) throws SQLException {
        String sql = """
            SELECT r.status, b.id AS book_id, b.available_copies, b.total_copies
            FROM book_issue_requests r
            JOIN books b ON b.id = r.book_id
            WHERE r.id = ?
            FOR UPDATE OF r, b
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new EntityNotFoundException("Заявка с ID " + id + " не найдена.");
                return new LockedRequest(RequestStatus.valueOf(resultSet.getString("status")),
                    resultSet.getLong("book_id"), resultSet.getInt("available_copies"),
                    resultSet.getInt("total_copies"));
            }
        }
    }

    private void updateStock(Connection connection, LockedRequest locked, boolean issuing) throws SQLException {
        if (issuing && locked.availableCopies() <= 0) {
            throw new BusinessRuleException("Нет доступных экземпляров книги.");
        }
        if (!issuing && locked.availableCopies() >= locked.totalCopies()) {
            throw new BusinessRuleException("Остаток книги уже равен общему количеству.");
        }
        String sql = issuing
            ? "UPDATE books SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0"
            : "UPDATE books SET available_copies = available_copies + 1 WHERE id = ? AND available_copies < total_copies";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, locked.bookId());
            if (statement.executeUpdate() != 1) {
                throw new BusinessRuleException("Остаток книги изменился, повторите операцию.");
            }
        }
    }

    private BookIssueRequest markIssued(Connection connection, long id, LocalDate issuedOn,
                                        LocalDate dueOn) throws SQLException {
        String sql = "UPDATE book_issue_requests SET status = 'ISSUED', issued_on = ?, due_on = ? "
            + "WHERE id = ? AND status = 'APPROVED' RETURNING " + REQUEST_COLUMNS;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, issuedOn);
            statement.setObject(2, dueOn);
            statement.setLong(3, id);
            return requireChangedRequest(statement);
        }
    }

    private BookIssueRequest markReturned(Connection connection, long id, LocalDate returnedOn)
            throws SQLException {
        String sql = "UPDATE book_issue_requests SET status = 'RETURNED', returned_on = ? "
            + "WHERE id = ? AND status = 'ISSUED' RETURNING " + REQUEST_COLUMNS;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, returnedOn);
            statement.setLong(2, id);
            return requireChangedRequest(statement);
        }
    }

    private BookIssueRequest requireChangedRequest(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new BusinessRuleException("Статус заявки изменился, повторите операцию.");
            }
            return RowMappers.toRequest(resultSet);
        }
    }

    private List<BookIssueRequestDetails> queryDetails(String sql, StatementBinder binder) {
        List<BookIssueRequestDetails> values = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) values.add(RowMappers.toRequestDetails(resultSet));
            }
            return values;
        } catch (SQLException exception) {
            throw new DataAccessException("Не удалось загрузить заявки.", exception);
        }
    }

    private static void setRequestValues(PreparedStatement statement, BookIssueRequest request)
            throws SQLException {
        statement.setLong(1, request.getReaderId());
        statement.setLong(2, request.getBookId());
        statement.setString(3, request.getStatus().name());
        statement.setObject(4, request.getRequestedAt());
        statement.setObject(5, request.getIssuedOn());
        statement.setObject(6, request.getDueOn());
        statement.setObject(7, request.getReturnedOn());
        statement.setString(8, request.getNote());
    }

    private static BookIssueRequest copyWithId(BookIssueRequest request, long id) {
        return new BookIssueRequest(id, request.getReaderId(), request.getBookId(), request.getStatus(),
            request.getRequestedAt(), request.getIssuedOn(), request.getDueOn(),
            request.getReturnedOn(), request.getNote());
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // The connection is closing immediately; the original failure remains authoritative.
        }
    }

    private record LockedRequest(RequestStatus status, long bookId, int availableCopies, int totalCopies) { }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
