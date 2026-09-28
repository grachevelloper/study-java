package ru.mirea.store.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.mirea.store.exception.BusinessRuleException;
import ru.mirea.store.model.Customer;
import ru.mirea.store.model.Product;
import ru.mirea.store.repository.CustomerRepository;
import ru.mirea.store.repository.ProductRepository;

class CatalogValidationTest {

  @Test
  void customerValidationRejectsMalformedOrDangerousText() {
    CustomerService service = new CustomerService(
      new Customers(),
      Clock.systemUTC()
    );

    assertThrows(BusinessRuleException.class, () ->
      service.create("Имя", "no-at.example.com", null)
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("Имя", "a@b", null)
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("Имя", "a@b.c", null)
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("Имя", "a@b.12", null)
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("Имя\u0000", "a@b.ru", null)
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("x".repeat(201), "a@b.ru", null)
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("Имя", "a@b.ru", "call-me")
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("Имя", "a@b.ru", "+7)999(")
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("Имя", "a@b.ru", "+7\u200B999")
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("Имя", "a@b.ru", "\t")
    );

    Customer created = service.create(
      "  Иван   Петров  ",
      "User@Example.COM",
      " +7 (999) 123-45-67 "
    );
    assertEquals("Иван Петров", created.getFullName());
    assertEquals("user@example.com", created.getEmail());
    assertEquals("+7 (999) 123-45-67", created.getPhone());
  }

  @Test
  void productValidationRejectsInvalidSkuTextAndMoney() {
    ProductService service = new ProductService(new Products());

    assertThrows(BusinessRuleException.class, () ->
      service.create("bad sku", "Товар", BigDecimal.ONE, 1)
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("SKU", "Товар\u0007", BigDecimal.ONE, 1)
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("SKU", "x".repeat(301), BigDecimal.ONE, 1)
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("SKU", "Товар", new BigDecimal("1.001"), 1)
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("SKU", "Товар", new BigDecimal("10000000000.00"), 1)
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("SKU", "Товар", BigDecimal.ONE, -1)
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("SKU", "Товар", BigDecimal.ONE, 1_000_001)
    );
    assertThrows(BusinessRuleException.class, () ->
      service.create("СКЮ", "Товар", BigDecimal.ONE, 1)
    );

    Product created = service.create(
      " abc-_1.2 ",
      "  Полезный   товар  ",
      new BigDecimal("12.5"),
      2
    );
    assertEquals("ABC-_1.2", created.getSku());
    assertEquals("Полезный товар", created.getName());
    assertEquals(new BigDecimal("12.50"), created.getPrice());
  }

  private static final class Customers implements CustomerRepository {

    private final List<Customer> values = new ArrayList<>();

    public Customer save(Customer value) {
      values.add(value);
      return value;
    }

    public List<Customer> findAll() {
      return List.copyOf(values);
    }

    public Optional<Customer> findById(long id) {
      return Optional.empty();
    }
  }

  private static final class Products implements ProductRepository {

    private final List<Product> values = new ArrayList<>();

    public Product save(Product value) {
      values.add(value);
      return value;
    }

    public List<Product> findAll() {
      return List.copyOf(values);
    }

    public Optional<Product> findById(long id) {
      return Optional.empty();
    }
  }
}
