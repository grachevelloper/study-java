package ru.mirea.storeapi.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {
  private AnalyticsDtos() {}

  public record CustomerCount(long customerId, String name, long count) {}

  public record AnalyticsResponse(
      long total,
      long active,
      long archived,
      long urgent,
      BigDecimal averageTotal,
      List<CustomerCount> byCustomer,
      Map<String, Long> byStatus,
      Map<String, Long> byPriority) {}
}
