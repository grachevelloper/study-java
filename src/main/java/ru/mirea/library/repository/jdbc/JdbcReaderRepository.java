package ru.mirea.library.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import ru.mirea.library.config.ConnectionProvider;
import ru.mirea.library.exception.DataAccessException;
import ru.mirea.library.model.Reader;
import ru.mirea.library.repository.ReaderRepository;

public final class JdbcReaderRepository implements ReaderRepository {
    private static final String COLUMNS =
        "id, library_card_number, full_name, email, active, registered_at";

    private final ConnectionProvider connectionProvider;

    public JdbcReaderRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider);
    }

    @Override
    public Reader save(Reader reader) {
        String sql = "INSERT INTO readers "
            + "(library_card_number, full_name, email, active, registered_at) "
            + "VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, reader.getLibraryCardNumber());
            statement.setString(2, reader.getFullName());
            statement.setString(3, reader.getEmail());
            statement.setBoolean(4, reader.isActive());
            statement.setObject(5, reader.getRegisteredAt());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("База данных не вернула ID читателя.");
                }
                return new Reader(keys.getLong(1), reader.getLibraryCardNumber(), reader.getFullName(),
                    reader.getEmail(), reader.isActive(), reader.getRegisteredAt());
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Не удалось сохранить читателя.", exception);
        }
    }

    @Override
    public List<Reader> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM readers ORDER BY id";
        List<Reader> readers = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) readers.add(RowMappers.toReader(resultSet));
            return readers;
        } catch (SQLException exception) {
            throw new DataAccessException("Не удалось загрузить читателей.", exception);
        }
    }

    @Override
    public Optional<Reader> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM readers WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(RowMappers.toReader(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Не удалось загрузить читателя.", exception);
        }
    }
}
