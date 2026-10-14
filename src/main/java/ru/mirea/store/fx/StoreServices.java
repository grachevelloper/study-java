package ru.mirea.store.fx;

import java.time.Clock;
import ru.mirea.store.config.DatabaseConfig;
import ru.mirea.store.config.DatabaseManager;
import ru.mirea.store.export.ExcelExporter;
import ru.mirea.store.repository.jdbc.*;
import ru.mirea.store.service.*;

public record StoreServices(
  CustomerService customers,
  ProductService products,
  CustomerOrderService orders,
  StatisticsService statistics,
  OrderQueryService query,
  ExcelExporter exporter
) {
  public static StoreServices connect() {
    var database = new DatabaseManager(DatabaseConfig.fromEnvironment());
    var customers = new JdbcCustomerRepository(database);
    var products = new JdbcProductRepository(database);
    var clock = Clock.systemDefaultZone();
    var orders = new CustomerOrderService(
      new JdbcCustomerOrderRepository(database), customers, products, clock
    );
    return new StoreServices(
      new CustomerService(customers, clock),
      new ProductService(products), orders,
      new StatisticsService(new JdbcStatisticsRepository(database)),
      new OrderQueryService(orders), new ExcelExporter()
    );
  }
}
