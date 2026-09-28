package ru.mirea.store.repository.jdbc;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import ru.mirea.store.config.ConnectionProvider;
import ru.mirea.store.exception.DataAccessException;
import ru.mirea.store.model.*;
import ru.mirea.store.repository.StatisticsRepository;

public final class JdbcStatisticsRepository implements StatisticsRepository {

  private final ConnectionProvider provider;

  public JdbcStatisticsRepository(ConnectionProvider provider) {
    this.provider = Objects.requireNonNull(provider);
  }

  public SystemStatistics load() {
    String sql = """
    SELECT (SELECT count(*) FROM customers) customer_count,
           (SELECT count(*) FROM products) product_count,
           (SELECT coalesce(sum(stock_quantity),0) FROM products) stock_count,
           (SELECT count(*) FROM customer_orders) order_count,
           (SELECT count(*) FROM products WHERE stock_quantity=0) zero_count,
           (SELECT coalesce(sum(i.quantity*i.unit_price),0)
              FROM customer_orders o
              JOIN order_items i ON i.order_id=o.id
             WHERE o.status='DELIVERED') revenue
    """;
    try (
      Connection c = provider.getConnection();
      PreparedStatement s = c.prepareStatement(sql);
      ResultSet rs = s.executeQuery()
    ) {
      rs.next();
      EnumMap<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
      for (OrderStatus status : OrderStatus.values()) byStatus.put(status, 0L);
      try (
        PreparedStatement ss = c.prepareStatement(
          "SELECT status,count(*) amount FROM customer_orders GROUP BY status"
        );
        ResultSet sr = ss.executeQuery()
      ) {
        while (sr.next())
          byStatus.put(
            OrderStatus.valueOf(sr.getString("status")),
            sr.getLong("amount")
          );
      }
      BigDecimal revenue = rs.getBigDecimal("revenue");
      return new SystemStatistics(
        rs.getLong("customer_count"),
        rs.getLong("product_count"),
        rs.getLong("stock_count"),
        rs.getLong("order_count"),
        byStatus,
        revenue,
        rs.getLong("zero_count")
      );
    } catch (SQLException e) {
      throw new DataAccessException("Не удалось рассчитать статистику.", e);
    }
  }
}
