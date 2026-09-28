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
import ru.mirea.library.model.Book;
import ru.mirea.library.repository.BookRepository;

public final class JdbcBookRepository implements BookRepository {
    private static final String COLUMNS =
        "id, isbn, title, author, publication_year, total_copies, available_copies";

    private final ConnectionProvider connectionProvider;

    public JdbcBookRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider);
    }

    @Override
    public Book save(Book book) {
        String sql = "INSERT INTO books "
            + "(isbn, title, author, publication_year, total_copies, available_copies) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, book.getIsbn());
            statement.setString(2, book.getTitle());
            statement.setString(3, book.getAuthor());
            statement.setInt(4, book.getPublicationYear());
            statement.setInt(5, book.getTotalCopies());
            statement.setInt(6, book.getAvailableCopies());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("База данных не вернула ID книги.");
                }
                return new Book(keys.getLong(1), book.getIsbn(), book.getTitle(), book.getAuthor(),
                    book.getPublicationYear(), book.getTotalCopies(), book.getAvailableCopies());
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Не удалось сохранить книгу.", exception);
        }
    }

    @Override
    public List<Book> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM books ORDER BY id";
        List<Book> books = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) books.add(RowMappers.toBook(resultSet));
            return books;
        } catch (SQLException exception) {
            throw new DataAccessException("Не удалось загрузить книги.", exception);
        }
    }

    @Override
    public Optional<Book> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM books WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(RowMappers.toBook(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Не удалось загрузить книгу.", exception);
        }
    }
}
