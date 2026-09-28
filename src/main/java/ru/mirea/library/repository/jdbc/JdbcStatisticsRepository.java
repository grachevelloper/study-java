package ru.mirea.library.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import ru.mirea.library.config.ConnectionProvider;
import ru.mirea.library.exception.DataAccessException;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.model.SystemStatistics;
import ru.mirea.library.repository.StatisticsRepository;

public final class JdbcStatisticsRepository implements StatisticsRepository {
    private final ConnectionProvider connectionProvider;

    public JdbcStatisticsRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider);
    }

    @Override
    public SystemStatistics load(LocalDate today) {
        try (Connection connection = connectionProvider.getConnection()) {
            long readerCount = scalar(connection, "SELECT count(*) FROM readers");
            long[] bookTotals = bookTotals(connection);
            long requestCount = scalar(connection, "SELECT count(*) FROM book_issue_requests");
            Map<RequestStatus, Long> byStatus = requestCountsByStatus(connection);
            long overdue = overdueCount(connection, today);
            return new SystemStatistics(readerCount, bookTotals[0], bookTotals[1], bookTotals[2],
                requestCount, byStatus, overdue);
        } catch (SQLException exception) {
            throw new DataAccessException("Не удалось загрузить статистику.", exception);
        }
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static long[] bookTotals(Connection connection) throws SQLException {
        String sql = "SELECT count(*), coalesce(sum(total_copies), 0), "
            + "coalesce(sum(available_copies), 0) FROM books";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return new long[] {resultSet.getLong(1), resultSet.getLong(2), resultSet.getLong(3)};
        }
    }

    private static Map<RequestStatus, Long> requestCountsByStatus(Connection connection)
            throws SQLException {
        EnumMap<RequestStatus, Long> counts = new EnumMap<>(RequestStatus.class);
        for (RequestStatus status : RequestStatus.values()) counts.put(status, 0L);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT status, count(*) FROM book_issue_requests GROUP BY status");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                counts.put(RequestStatus.valueOf(resultSet.getString(1)), resultSet.getLong(2));
            }
        }
        return counts;
    }

    private static long overdueCount(Connection connection, LocalDate today) throws SQLException {
        String sql = "SELECT count(*) FROM book_issue_requests "
            + "WHERE status = 'ISSUED' AND due_on < ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, today);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
