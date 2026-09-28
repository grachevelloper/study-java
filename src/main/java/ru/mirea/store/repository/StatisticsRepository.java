package ru.mirea.store.repository;

import ru.mirea.store.model.SystemStatistics;

public interface StatisticsRepository {
  SystemStatistics load();
}
