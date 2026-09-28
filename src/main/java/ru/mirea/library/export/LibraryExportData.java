package ru.mirea.library.export;

import java.util.List;
import java.util.Objects;
import ru.mirea.library.model.Book;
import ru.mirea.library.model.BookIssueRequestDetails;
import ru.mirea.library.model.Reader;
import ru.mirea.library.model.SystemStatistics;

public record LibraryExportData(List<Reader> readers,
                                List<Book> books,
                                List<BookIssueRequestDetails> requests,
                                SystemStatistics statistics) {
    public LibraryExportData {
        readers = List.copyOf(Objects.requireNonNull(readers));
        books = List.copyOf(Objects.requireNonNull(books));
        requests = List.copyOf(Objects.requireNonNull(requests));
        Objects.requireNonNull(statistics);
    }
}
