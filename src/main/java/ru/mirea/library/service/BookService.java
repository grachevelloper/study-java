package ru.mirea.library.service;

import java.util.List;
import java.util.Objects;
import ru.mirea.library.exception.BusinessRuleException;
import ru.mirea.library.exception.EntityNotFoundException;
import ru.mirea.library.model.Book;
import ru.mirea.library.repository.BookRepository;

public final class BookService {
    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public Book create(String isbn, String title, String author, int publicationYear, int totalCopies) {
        if (publicationYear <= 0) {
            throw new BusinessRuleException("Год издания должен быть положительным.");
        }
        if (totalCopies < 1) {
            throw new BusinessRuleException("Количество экземпляров должно быть не меньше 1.");
        }
        return repository.save(new Book(0, required(isbn, "ISBN"), required(title, "Название"),
            required(author, "Автор"), publicationYear, totalCopies, totalCopies));
    }

    public List<Book> getAll() {
        return repository.findAll();
    }

    public Book getById(long id) {
        if (id <= 0) throw new BusinessRuleException("ID должен быть положительным.");
        return repository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Книга с ID " + id + " не найдена."));
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessRuleException(field + " не может быть пустым.");
        }
        return value.trim();
    }
}
