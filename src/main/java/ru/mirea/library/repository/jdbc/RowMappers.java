package ru.mirea.library.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import ru.mirea.library.model.Book;
import ru.mirea.library.model.BookIssueRequest;
import ru.mirea.library.model.BookIssueRequestDetails;
import ru.mirea.library.model.Reader;
import ru.mirea.library.model.RequestStatus;

final class RowMappers {
    private RowMappers() {
    }

    static Reader toReader(ResultSet resultSet) throws SQLException {
        return new Reader(
            resultSet.getLong("id"),
            resultSet.getString("library_card_number"),
            resultSet.getString("full_name"),
            resultSet.getString("email"),
            resultSet.getBoolean("active"),
            resultSet.getObject("registered_at", LocalDate.class)
        );
    }

    static Book toBook(ResultSet resultSet) throws SQLException {
        return new Book(
            resultSet.getLong("id"),
            resultSet.getString("isbn"),
            resultSet.getString("title"),
            resultSet.getString("author"),
            resultSet.getInt("publication_year"),
            resultSet.getInt("total_copies"),
            resultSet.getInt("available_copies")
        );
    }

    static BookIssueRequest toRequest(ResultSet resultSet) throws SQLException {
        return new BookIssueRequest(
            resultSet.getLong("id"),
            resultSet.getLong("reader_id"),
            resultSet.getLong("book_id"),
            RequestStatus.valueOf(resultSet.getString("status")),
            resultSet.getObject("requested_at", LocalDateTime.class),
            resultSet.getObject("issued_on", LocalDate.class),
            resultSet.getObject("due_on", LocalDate.class),
            resultSet.getObject("returned_on", LocalDate.class),
            resultSet.getString("note")
        );
    }

    static BookIssueRequestDetails toRequestDetails(ResultSet resultSet) throws SQLException {
        return new BookIssueRequestDetails(
            toRequest(resultSet),
            resultSet.getString("reader_name"),
            resultSet.getString("book_title"),
            resultSet.getString("book_author")
        );
    }
}
