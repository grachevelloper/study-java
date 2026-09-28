package ru.mirea.library.repository;

import java.time.LocalDate;
import ru.mirea.library.model.SystemStatistics;

public interface StatisticsRepository {
    SystemStatistics load(LocalDate today);
}
