package ru.mirea.library.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import ru.mirea.library.exception.BusinessRuleException;
import ru.mirea.library.exception.EntityNotFoundException;
import ru.mirea.library.model.Reader;
import ru.mirea.library.repository.ReaderRepository;

public final class ReaderService {
    private final ReaderRepository repository;
    private final Clock clock;

    public ReaderService(ReaderRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public Reader create(String cardNumber, String fullName, String email) {
        return repository.save(new Reader(0, required(cardNumber, "Номер билета"),
            required(fullName, "ФИО"), required(email, "Email"), true, LocalDate.now(clock)));
    }

    public List<Reader> getAll() {
        return repository.findAll();
    }

    public Reader getById(long id) {
        requirePositiveId(id);
        return repository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Читатель с ID " + id + " не найден."));
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessRuleException(field + " не может быть пустым.");
        }
        return value.trim();
    }

    private static void requirePositiveId(long id) {
        if (id <= 0) throw new BusinessRuleException("ID должен быть положительным.");
    }
}
