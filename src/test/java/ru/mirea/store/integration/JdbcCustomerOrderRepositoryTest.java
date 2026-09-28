package ru.mirea.store.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import ru.mirea.store.config.ConnectionProvider;
import ru.mirea.store.exception.BusinessRuleException;
import ru.mirea.store.exception.DataAccessException;
import ru.mirea.store.model.*;
import ru.mirea.store.repository.*;
import ru.mirea.store.repository.jdbc.*;

class JdbcCustomerOrderRepositoryTest {

  private static ConnectionProvider connections;
  private CustomerOrderRepository orders;
  private ProductRepository products;

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
    orders = new JdbcCustomerOrderRepository(connections);
    products = new JdbcProductRepository(connections);
  }

  @Test
  void supportsMultiItemCrudSearchFiltersAndPriceSnapshot() {
    CustomerOrder created = orders.create(
      order(0, 1),
      List.of(item(1, 2, "10.00"), item(2, 1, "20.00"))
    );
    CustomerOrderDetails details = orders
      .findById(created.getId())
      .orElseThrow();
    assertEquals(2, details.getItems().size());
    assertEquals(new BigDecimal("40.00"), details.getTotal());
    assertTrue(
      orders
        .findByProductName("Смарт")
        .stream()
        .anyMatch(v -> v.getOrder().getId() == 1)
    );
    assertEquals(
      1,
      orders
        .findByCustomerName("АнНа")
        .stream()
        .filter(v -> v.getOrder().getId() == 1)
        .count()
    );
    assertFalse(orders.findByStatus(OrderStatus.DELIVERED).isEmpty());
    assertFalse(
      orders
        .findByCreatedAt(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20))
        .isEmpty()
    );
    CustomerOrder updated = new CustomerOrder(
      created.getId(),
      2,
      OrderStatus.NEW,
      created.getCreatedAt(),
      null,
      null,
      null,
      null,
      "changed"
    );
    orders.updateNew(updated, List.of(item(3, 1, "99.00")));
    assertEquals(
      new BigDecimal("99.00"),
      orders.findById(created.getId()).orElseThrow().getTotal()
    );
    assertTrue(orders.deleteById(created.getId()));
  }

  @Test
  void treatsLikeMetacharactersAndInjectionTextAsLiterals() {
    assertTrue(orders.findByProductName("%").isEmpty());
    assertTrue(orders.findByProductName("_").isEmpty());
    assertTrue(orders.findByProductName("\\").isEmpty());
    assertTrue(orders.findByCustomerName("' OR 1=1 --").isEmpty());
  }

  @Test
  void paysAndCancelsAtomically() {
    CustomerOrder created = orders.create(
      order(0, 1),
      List.of(item(1, 2, "10.00"), item(2, 3, "20.00"))
    );
    int first = products.findById(1).orElseThrow().getStockQuantity();
    int second = products.findById(2).orElseThrow().getStockQuantity();
    assertEquals(
      OrderStatus.PAID,
      orders.pay(created.getId(), LocalDateTime.now()).getStatus()
    );
    assertEquals(
      first - 2,
      products.findById(1).orElseThrow().getStockQuantity()
    );
    assertEquals(
      second - 3,
      products.findById(2).orElseThrow().getStockQuantity()
    );
    assertEquals(
      OrderStatus.CANCELLED,
      orders
        .cancelPaid(created.getId(), LocalDateTime.now().plusMinutes(1))
        .getStatus()
    );
    assertEquals(first, products.findById(1).orElseThrow().getStockQuantity());
    assertEquals(second, products.findById(2).orElseThrow().getStockQuantity());
    assertThrows(BusinessRuleException.class, () ->
      orders.cancelPaid(created.getId(), LocalDateTime.now())
    );
  }

  @Test
  void insufficientStockRollsBackEveryProductAndStatus() {
    CustomerOrder created = orders.create(
      order(0, 1),
      List.of(item(1, 1, "10.00"), item(8, 1, "20.00"))
    );
    int stock = products.findById(1).orElseThrow().getStockQuantity();
    assertThrows(BusinessRuleException.class, () ->
      orders.pay(created.getId(), LocalDateTime.now())
    );
    assertEquals(stock, products.findById(1).orElseThrow().getStockQuantity());
    assertEquals(
      OrderStatus.NEW,
      orders.findById(created.getId()).orElseThrow().getOrder().getStatus()
    );
  }

  @Test
  void databaseRejectsOversizedQuantityAndControlCharactersAtomically() {
    long before = orders.findAll().size();
    assertThrows(ru.mirea.store.exception.DataAccessException.class, () ->
      orders.create(order(0, 1), List.of(item(1, 1_000_001, "10.00")))
    );
    assertEquals(before, orders.findAll().size());
    CustomerOrder invalid = new CustomerOrder(
      0,
      1,
      OrderStatus.NEW,
      LocalDateTime.now(),
      null,
      null,
      null,
      null,
      "bad\u0000note"
    );
    assertThrows(ru.mirea.store.exception.DataAccessException.class, () ->
      orders.create(invalid, List.of(item(1, 1, "10.00")))
    );
    assertEquals(before, orders.findAll().size());
  }

  @Test
  void databaseFailureDuringSecondStockUpdateRollsBackFirstUpdate()
    throws Exception {
    CustomerOrder created = orders.create(
      order(0, 1),
      List.of(item(1, 1, "10.00"), item(2, 1, "20.00"))
    );
    int first = products.findById(1).orElseThrow().getStockQuantity();
    int second = products.findById(2).orElseThrow().getStockQuantity();
    execute(
      """
      CREATE FUNCTION fail_second_product()
      RETURNS trigger LANGUAGE plpgsql AS $$
      BEGIN
        IF NEW.id = 2
           AND NEW.stock_quantity < OLD.stock_quantity THEN
          RAISE EXCEPTION 'forced payment failure';
        END IF;
        RETURN NEW;
      END
      $$;
      CREATE TRIGGER fail_second_product_trigger
      BEFORE UPDATE ON products
      FOR EACH ROW EXECUTE FUNCTION fail_second_product();
      """
    );
    assertThrows(DataAccessException.class, () ->
      orders.pay(created.getId(), LocalDateTime.now())
    );
    assertEquals(first, products.findById(1).orElseThrow().getStockQuantity());
    assertEquals(second, products.findById(2).orElseThrow().getStockQuantity());
    assertEquals(
      OrderStatus.NEW,
      orders.findById(created.getId()).orElseThrow().getOrder().getStatus()
    );
  }

  @Test
  void databaseFailureAfterStockRestoreRollsBackCancellation()
    throws Exception {
    CustomerOrder created = orders.create(
      order(0, 1),
      List.of(item(1, 2, "10.00"), item(2, 1, "20.00"))
    );
    orders.pay(created.getId(), LocalDateTime.now());
    int first = products.findById(1).orElseThrow().getStockQuantity();
    int second = products.findById(2).orElseThrow().getStockQuantity();
    execute(
      """
      CREATE FUNCTION fail_cancellation()
      RETURNS trigger LANGUAGE plpgsql AS $$
      BEGIN
        IF NEW.status = 'CANCELLED' AND OLD.status = 'PAID' THEN
          RAISE EXCEPTION 'forced cancellation failure';
        END IF;
        RETURN NEW;
      END
      $$;
      CREATE TRIGGER fail_cancellation_trigger
      BEFORE UPDATE ON customer_orders
      FOR EACH ROW EXECUTE FUNCTION fail_cancellation();
      """
    );
    assertThrows(DataAccessException.class, () ->
      orders.cancelPaid(created.getId(), LocalDateTime.now().plusMinutes(1))
    );
    assertEquals(first, products.findById(1).orElseThrow().getStockQuantity());
    assertEquals(second, products.findById(2).orElseThrow().getStockQuantity());
    assertEquals(
      OrderStatus.PAID,
      orders.findById(created.getId()).orElseThrow().getOrder().getStatus()
    );
  }

  @Test
  void onlyOneConcurrentPaymentGetsLastUnit() throws Exception {
    long productId;
    try (
      Connection c = connections.getConnection();
      PreparedStatement s = c.prepareStatement(
        "INSERT INTO products(sku,name,price,stock_quantity,active) " +
        "VALUES('LAST','Последний',1,1,true) RETURNING id"
      );
      ResultSet rs = s.executeQuery()
    ) {
      rs.next();
      productId = rs.getLong(1);
    }
    long first = orders
      .create(order(0, 1), List.of(item(productId, 1, "1")))
      .getId();
    long second = orders
      .create(order(0, 2), List.of(item(productId, 1, "1")))
      .getId();
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Boolean> a = executor.submit(() -> pay(first, start));
      Future<Boolean> b = executor.submit(() -> pay(second, start));
      start.countDown();
      assertEquals(
        1,
        List.of(a.get(), b.get()).stream().filter(Boolean::booleanValue).count()
      );
    }
    assertEquals(
      0,
      products.findById(productId).orElseThrow().getStockQuantity()
    );
  }

  @Test
  void statisticsMatchSeed() {
    SystemStatistics stats = new JdbcStatisticsRepository(connections).load();
    assertEquals(5, stats.getCustomerCount());
    assertEquals(8, stats.getProductCount());
    assertEquals(8, stats.getOrderCount());
    assertEquals(2, stats.getOrdersByStatus().get(OrderStatus.DELIVERED));
    assertTrue(stats.getDeliveredRevenue().signum() > 0);
  }

  private boolean pay(long id, CountDownLatch start) throws Exception {
    start.await();
    try {
      orders.pay(id, LocalDateTime.now());
      return true;
    } catch (BusinessRuleException e) {
      return false;
    }
  }

  private static void execute(String sql) throws Exception {
    try (
      Connection connection = connections.getConnection();
      Statement statement = connection.createStatement()
    ) {
      statement.execute(sql);
    }
  }

  private static CustomerOrder order(long id, long customer) {
    return new CustomerOrder(
      id,
      customer,
      OrderStatus.NEW,
      LocalDateTime.now(),
      null,
      null,
      null,
      null,
      null
    );
  }

  private static OrderItem item(long product, int quantity, String price) {
    return new OrderItem(0, 0, product, quantity, new BigDecimal(price));
  }
}
