package ru.mirea.store.service;
import java.util.Objects; import ru.mirea.store.model.SystemStatistics; import ru.mirea.store.repository.StatisticsRepository;
public final class StatisticsService { private final StatisticsRepository repository; public StatisticsService(StatisticsRepository repository) { this.repository = Objects.requireNonNull(repository); } public SystemStatistics getStatistics() { return repository.load(); } }
