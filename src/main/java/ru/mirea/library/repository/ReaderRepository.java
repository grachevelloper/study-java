package ru.mirea.library.repository;

import java.util.List;
import java.util.Optional;
import ru.mirea.library.model.Reader;

public interface ReaderRepository {
    Reader save(Reader reader);
    List<Reader> findAll();
    Optional<Reader> findById(long id);
}
