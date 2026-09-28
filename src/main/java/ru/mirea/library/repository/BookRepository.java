package ru.mirea.library.repository;

import java.util.List;
import java.util.Optional;
import ru.mirea.library.model.Book;

public interface BookRepository {
    Book save(Book book);
    List<Book> findAll();
    Optional<Book> findById(long id);
}
