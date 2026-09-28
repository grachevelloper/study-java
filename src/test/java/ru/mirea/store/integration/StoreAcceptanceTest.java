package ru.mirea.store.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import ru.mirea.store.config.ConnectionProvider;
import ru.mirea.store.exception.BusinessRuleException;
import ru.mirea.store.model.*;
import ru.mirea.store.repository.jdbc.*;
import ru.mirea.store.service.*;

class StoreAcceptanceTest {

  private static ConnectionProvider connections;

  @BeforeAll
  static void start() {
    connections = TestDatabase.start();
  }

  @AfterAll
  static void stop() {
    TestDatabase.stop();
  }

  @BeforeEach
  void reset() throws Exception {
    TestDatabase.reset();
  }

  @Test
  void completesRequiredOrderLifecycleScenario() throws Exception {
    JdbcCustomerRepository customerRepository = new JdbcCustomerRepository(
      connections
    );
    JdbcProductRepository productRepository = new JdbcProductRepository(
      connections
    );
    JdbcCustomerOrderRepository orderRepository =
      new JdbcCustomerOrderRepository(connections);
    Clock clock = Clock.fixed(
      Instant.parse("2026-09-28T12:00:00Z"),
      ZoneOffset.UTC
    );
    CustomerService customers = new CustomerService(customerRepository, clock);
    ProductService products = new ProductService(productRepository);
    CustomerOrderService orders = new CustomerOrderService(
      orderRepository,
      customerRepository,
      productRepository,
      clock
    );
    Customer customer = customers.create(
      "Приёмочный покупатель",
      "accept@example.com",
      "+7 999 123-45-67"
    );
    Product first = products.create(
      "ACC-1",
      "Первый приёмочный",
      new BigDecimal("100.00"),
      5
    );
    Product second = products.create(
      "ACC-2",
      "Второй приёмочный",
      new BigDecimal("25.00"),
      8
    );
    CustomerOrder created = orders.createOrder(
      customer.getId(),
      List.of(
        new CustomerOrderService.OrderItemInput(first.getId(), 2),
        new CustomerOrderService.OrderItemInput(second.getId(), 1)
      ),
      " first "
    );
    orders.updateNew(
      created.getId(),
      customer.getId(),
      List.of(
        new CustomerOrderService.OrderItemInput(first.getId(), 1),
        new CustomerOrderService.OrderItemInput(second.getId(), 3)
      ),
      "edited"
    );
    BigDecimal total = orders.getById(created.getId()).getTotal();
    assertEquals(new BigDecimal("175.00"), total);
    try (
      Connection c = connections.getConnection();
      PreparedStatement s = c.prepareStatement(
        "UPDATE products SET price=999 WHERE id=?"
      )
    ) {
      s.setLong(1, first.getId());
      s.executeUpdate();
    }
    assertEquals(total, orders.getById(created.getId()).getTotal());
    assertTrue(
      orders
        .searchByProductName("приёмочный")
        .stream()
        .anyMatch(v -> v.getOrder().getId() == created.getId())
    );
    assertTrue(
      orders
        .searchByCustomerName("ПРИЁМОЧНЫЙ")
        .stream()
        .anyMatch(v -> v.getOrder().getId() == created.getId())
    );
    assertTrue(
      orders
        .filterByStatus(OrderStatus.NEW)
        .stream()
        .anyMatch(v -> v.getOrder().getId() == created.getId())
    );
    assertTrue(
      orders
        .filterByCreatedAt(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 28))
        .stream()
        .anyMatch(v -> v.getOrder().getId() == created.getId())
    );
    assertEquals(
      created.getId(),
      orders
        .sort(
          orders.getAll(),
          OrderSortField.TOTAL_AMOUNT,
          SortDirection.DESCENDING
        )
        .stream()
        .filter(v -> v.getOrder().getId() == created.getId())
        .findFirst()
        .orElseThrow()
        .getOrder()
        .getId()
    );
    int firstStock = products.getById(first.getId()).getStockQuantity();
    int secondStock = products.getById(second.getId()).getStockQuantity();
    orders.changeStatus(created.getId(), OrderStatus.PAID);
    assertEquals(
      firstStock - 1,
      products.getById(first.getId()).getStockQuantity()
    );
    assertEquals(
      secondStock - 3,
      products.getById(second.getId()).getStockQuantity()
    );
    orders.changeStatus(created.getId(), OrderStatus.CANCELLED);
    assertEquals(
      firstStock,
      products.getById(first.getId()).getStockQuantity()
    );
    assertEquals(
      secondStock,
      products.getById(second.getId()).getStockQuantity()
    );
    CustomerOrder delivered = orders.createOrder(
      customer.getId(),
      List.of(new CustomerOrderService.OrderItemInput(second.getId(), 1)),
      null
    );
    orders.changeStatus(delivered.getId(), OrderStatus.PAID);
    orders.changeStatus(delivered.getId(), OrderStatus.SHIPPED);
    orders.changeStatus(delivered.getId(), OrderStatus.DELIVERED);
    assertEquals(
      OrderStatus.DELIVERED,
      orders.getById(delivered.getId()).getOrder().getStatus()
    );
    assertThrows(BusinessRuleException.class, () ->
      orders.changeStatus(delivered.getId(), OrderStatus.CANCELLED)
    );
  }
}
