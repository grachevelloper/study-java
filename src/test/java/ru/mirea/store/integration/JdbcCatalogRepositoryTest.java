package ru.mirea.store.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import ru.mirea.store.config.ConnectionProvider;
import ru.mirea.store.exception.DataAccessException;
import ru.mirea.store.model.*;
import ru.mirea.store.repository.*;
import ru.mirea.store.repository.jdbc.*;

class JdbcCatalogRepositoryTest {

  private static ConnectionProvider connections;
  private CustomerRepository customers;
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
    customers = new JdbcCustomerRepository(connections);
    products = new JdbcProductRepository(connections);
  }

  @Test
  void savesAndReadsCustomerAndProduct() {
    Customer c = customers.save(
      new Customer(
        0,
        "Елена",
        "elena@example.com",
        "+7 999 123-45-67",
        true,
        LocalDateTime.now()
      )
    );
    Product p = products.save(
      new Product(0, "TEST", "Тест", new BigDecimal("10.25"), 3, true)
    );
    assertEquals(c, customers.findById(c.getId()).orElseThrow());
    assertEquals(p, products.findById(p.getId()).orElseThrow());
  }

  @Test
  void databaseRejectsDuplicateAndInvalidCatalogValues() {
    assertThrows(DataAccessException.class, () ->
      customers.save(
        new Customer(
          0,
          "Дубль",
          "anna@example.com",
          null,
          true,
          LocalDateTime.now()
        )
      )
    );
    assertThrows(DataAccessException.class, () ->
      customers.save(
        new Customer(
          0,
          "Имя",
          "UPPER@EXAMPLE.COM",
          null,
          true,
          LocalDateTime.now()
        )
      )
    );
    assertThrows(DataAccessException.class, () ->
      customers.save(
        new Customer(
          0,
          "Имя\u0000",
          "valid@example.com",
          null,
          true,
          LocalDateTime.now()
        )
      )
    );
    assertThrows(DataAccessException.class, () ->
      customers.save(
        new Customer(
          0,
          "Имя",
          "valid@example.com",
          "call-me",
          true,
          LocalDateTime.now()
        )
      )
    );
    assertThrows(DataAccessException.class, () ->
      customers.save(
        new Customer(
          0,
          "Имя",
          "short-phone@example.com",
          "123",
          true,
          LocalDateTime.now()
        )
      )
    );
    assertThrows(DataAccessException.class, () ->
      customers.save(
        new Customer(
          0,
          "Имя",
          "bad-plus@example.com",
          "7+99999",
          true,
          LocalDateTime.now()
        )
      )
    );
    assertThrows(DataAccessException.class, () ->
      products.save(
        new Product(0, "PHONE-01", "Дубль", BigDecimal.ONE, 1, true)
      )
    );
    assertThrows(DataAccessException.class, () ->
      products.save(
        new Product(0, "bad sku", "Ошибка", BigDecimal.ONE, 1, true)
      )
    );
    assertThrows(DataAccessException.class, () ->
      products.save(
        new Product(0, "BAD-PRICE", "Ошибка", BigDecimal.ZERO, 1, true)
      )
    );
    assertThrows(DataAccessException.class, () ->
      products.save(
        new Product(0, "BAD-STOCK", "Ошибка", BigDecimal.ONE, -1, true)
      )
    );
    assertThrows(DataAccessException.class, () ->
      products.save(
        new Product(0, "HUGE-STOCK", "Ошибка", BigDecimal.ONE, 1_000_001, true)
      )
    );
  }
}
