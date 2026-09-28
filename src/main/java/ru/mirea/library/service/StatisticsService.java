package ru.mirea.library.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import ru.mirea.library.model.SystemStatistics;
import ru.mirea.library.repository.StatisticsRepository;

public final class StatisticsService {
    private final StatisticsRepository repository;
    private final Clock clock;

    public StatisticsService(StatisticsRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public SystemStatistics getStatistics() {
        return repository.load(LocalDate.now(clock));
    }
}
