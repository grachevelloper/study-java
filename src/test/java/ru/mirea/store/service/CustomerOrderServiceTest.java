package ru.mirea.store.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mirea.store.exception.BusinessRuleException;
import ru.mirea.store.exception.EntityNotFoundException;
import ru.mirea.store.model.Customer;
import ru.mirea.store.model.CustomerOrder;
import ru.mirea.store.model.CustomerOrderDetails;
import ru.mirea.store.model.OrderItem;
import ru.mirea.store.model.OrderItemDetails;
import ru.mirea.store.model.OrderStatus;
import ru.mirea.store.model.Product;
import ru.mirea.store.repository.CustomerOrderRepository;
import ru.mirea.store.repository.CustomerRepository;
import ru.mirea.store.repository.ProductRepository;

class CustomerOrderServiceTest {

  private final Clock clock = Clock.fixed(
    Instant.parse("2026-09-28T10:00:00Z"),
    ZoneOffset.UTC
  );
  private FakeOrderRepository orders;
  private CustomerOrderService service;

  @BeforeEach
  void setUp() {
    Customer customer = new Customer(
      1,
      "Покупатель",
      "buyer@example.com",
      null,
      true,
      LocalDateTime.of(2026, 1, 1, 10, 0)
    );
    Product first = new Product(
      10,
      "A",
      "Товар A",
      new BigDecimal("12.50"),
      5,
      true
    );
    Product second = new Product(
      20,
      "B",
      "Товар B",
      new BigDecimal("7.00"),
      2,
      true
    );
    CustomerRepository customers = new FixedCustomerRepository(customer);
    ProductRepository products = new FixedProductRepository(
      List.of(first, second)
    );
    orders = new FakeOrderRepository();
    service = new CustomerOrderService(orders, customers, products, clock);
  }

  @Test
  void createsMultiItemOrderWithCurrentProductPricesAndTrimmedNote() {
    CustomerOrder created = service.createOrder(
      1,
      List.of(
        new CustomerOrderService.OrderItemInput(10, 2),
        new CustomerOrderService.OrderItemInput(20, 1)
      ),
      "  срочно  "
    );

    assertEquals(OrderStatus.NEW, created.getStatus());
    assertEquals("срочно", created.getNote());
    assertEquals(new BigDecimal("12.50"), orders.items.get(0).getUnitPrice());
    assertEquals(new BigDecimal("7.00"), orders.items.get(1).getUnitPrice());
  }

  @Test
  void rejectsEmptyDuplicateAndInvalidItems() {
    assertThrows(BusinessRuleException.class, () ->
      service.createOrder(1, List.of(), null)
    );
    assertThrows(BusinessRuleException.class, () ->
      service.createOrder(
        1,
        List.of(
          new CustomerOrderService.OrderItemInput(10, 1),
          new CustomerOrderService.OrderItemInput(10, 2)
        ),
        null
      )
    );
    assertThrows(BusinessRuleException.class, () ->
      service.createOrder(
        1,
        List.of(new CustomerOrderService.OrderItemInput(10, 0)),
        null
      )
    );
    assertThrows(BusinessRuleException.class, () ->
      service.createOrder(
        1,
        List.of(new CustomerOrderService.OrderItemInput(10, 1_000_001)),
        null
      )
    );
    assertThrows(EntityNotFoundException.class, () ->
      service.createOrder(
        1,
        List.of(new CustomerOrderService.OrderItemInput(999, 1)),
        null
      )
    );
  }

  @Test
  void rejectsInactiveCustomerAndLongNote() {
    assertThrows(BusinessRuleException.class, () ->
      service.createOrder(
        1,
        List.of(new CustomerOrderService.OrderItemInput(10, 1)),
        "x".repeat(501)
      )
    );
    assertThrows(BusinessRuleException.class, () ->
      service.createOrder(
        1,
        List.of(new CustomerOrderService.OrderItemInput(10, 1)),
        "bad\u0000note"
      )
    );
    assertThrows(BusinessRuleException.class, () ->
      service.createOrder(
        1,
        List.of(new CustomerOrderService.OrderItemInput(10, 1)),
        "\t"
      )
    );
    assertThrows(BusinessRuleException.class, () ->
      service.createOrder(
        1,
        java.util.stream.IntStream.range(0, 101)
          .mapToObj(index ->
            new CustomerOrderService.OrderItemInput(10 + index, 1)
          )
          .toList(),
        null
      )
    );
    Customer inactive = new Customer(
      2,
      "Неактивный",
      "off@example.com",
      null,
      false,
      LocalDateTime.now(clock)
    );
    service = new CustomerOrderService(
      orders,
      new FixedCustomerRepository(inactive),
      new FixedProductRepository(
        List.of(new Product(10, "A", "Товар", BigDecimal.ONE, 1, true))
      ),
      clock
    );
    assertThrows(BusinessRuleException.class, () ->
      service.createOrder(
        2,
        List.of(new CustomerOrderService.OrderItemInput(10, 1)),
        null
      )
    );
  }

  @Test
  void allowsEditingOnlyNewAndDeletingOnlyNewOrCancelled() {
    service.createOrder(
      1,
      List.of(new CustomerOrderService.OrderItemInput(10, 1)),
      null
    );
    service.updateNew(
      1,
      1,
      List.of(new CustomerOrderService.OrderItemInput(20, 2)),
      "new"
    );
    assertEquals(20, orders.items.getFirst().getProductId());

    orders.order = copyWithStatus(orders.order, OrderStatus.PAID);
    assertThrows(BusinessRuleException.class, () ->
      service.updateNew(
        1,
        1,
        List.of(new CustomerOrderService.OrderItemInput(10, 1)),
        null
      )
    );
    assertThrows(BusinessRuleException.class, () -> service.delete(1));
    orders.order = copyWithStatus(orders.order, OrderStatus.CANCELLED);
    service.delete(1);
    assertEquals(null, orders.order);
  }

  @Test
  void dispatchesLifecycleOperations() {
    service.createOrder(
      1,
      List.of(new CustomerOrderService.OrderItemInput(10, 1)),
      null
    );
    assertEquals(
      OrderStatus.PAID,
      service.changeStatus(1, OrderStatus.PAID).getStatus()
    );
    assertEquals(
      OrderStatus.CANCELLED,
      service.changeStatus(1, OrderStatus.CANCELLED).getStatus()
    );
    assertThrows(BusinessRuleException.class, () ->
      service.changeStatus(1, OrderStatus.SHIPPED)
    );
  }

  @Test
  void rejectsDangerousSearchAndDateInputsBeforeRepositoryCall() {
    assertThrows(BusinessRuleException.class, () ->
      service.searchByProductName("bad\u001Bquery")
    );
    assertThrows(BusinessRuleException.class, () ->
      service.searchByCustomerName("x".repeat(301))
    );
    assertThrows(BusinessRuleException.class, () ->
      service.filterByCreatedAt(
        LocalDate.of(2026, 1, 2),
        LocalDate.of(2026, 1, 1)
      )
    );
    assertThrows(BusinessRuleException.class, () ->
      service.filterByCreatedAt(
        LocalDate.of(10_000, 1, 1),
        LocalDate.of(10_000, 1, 2)
      )
    );
    assertThrows(BusinessRuleException.class, () ->
      service.createOrder(
        1,
        List.of(new CustomerOrderService.OrderItemInput(0, 1)),
        null
      )
    );
  }

  private static CustomerOrder copyWithStatus(
    CustomerOrder order,
    OrderStatus status
  ) {
    return new CustomerOrder(
      order.getId(),
      order.getCustomerId(),
      status,
      order.getCreatedAt(),
      status == OrderStatus.PAID
        ? order.getCreatedAt().plusHours(1)
        : order.getPaidAt(),
      order.getShippedAt(),
      order.getDeliveredAt(),
      status == OrderStatus.CANCELLED
        ? order.getCreatedAt().plusHours(2)
        : order.getCancelledAt(),
      order.getNote()
    );
  }

  private record FixedCustomerRepository(
    Customer value
  ) implements CustomerRepository {
    public Customer save(Customer customer) {
      return customer;
    }

    public List<Customer> findAll() {
      return List.of(value);
    }

    public Optional<Customer> findById(long id) {
      return value.getId() == id ? Optional.of(value) : Optional.empty();
    }
  }

  private record FixedProductRepository(
    List<Product> values
  ) implements ProductRepository {
    public Product save(Product product) {
      return product;
    }

    public List<Product> findAll() {
      return values;
    }

    public Optional<Product> findById(long id) {
      return values
        .stream()
        .filter(p -> p.getId() == id)
        .findFirst();
    }
  }

  private static final class FakeOrderRepository
    implements CustomerOrderRepository
  {

    private CustomerOrder order;
    private List<OrderItem> items = new ArrayList<>();

    public CustomerOrder create(CustomerOrder value, List<OrderItem> newItems) {
      order = new CustomerOrder(
        1,
        value.getCustomerId(),
        value.getStatus(),
        value.getCreatedAt(),
        null,
        null,
        null,
        null,
        value.getNote()
      );
      items = List.copyOf(newItems);
      return order;
    }

    public List<CustomerOrderDetails> findAll() {
      return order == null ? List.of() : List.of(details());
    }

    public Optional<CustomerOrderDetails> findById(long id) {
      return order != null && order.getId() == id
        ? Optional.of(details())
        : Optional.empty();
    }

    public CustomerOrder updateNew(
      CustomerOrder value,
      List<OrderItem> newItems
    ) {
      order = value;
      items = List.copyOf(newItems);
      return order;
    }

    public boolean deleteById(long id) {
      if (order == null) return false;
      order = null;
      items = List.of();
      return true;
    }

    public List<CustomerOrderDetails> findByProductName(String query) {
      return findAll();
    }

    public List<CustomerOrderDetails> findByCustomerName(String query) {
      return findAll();
    }

    public List<CustomerOrderDetails> findByStatus(OrderStatus status) {
      return findAll();
    }

    public List<CustomerOrderDetails> findByCreatedAt(
      LocalDate from,
      LocalDate to
    ) {
      return findAll();
    }

    public CustomerOrder transition(
      long id,
      OrderStatus expected,
      OrderStatus target,
      LocalDateTime at
    ) {
      order = copyWithStatus(order, target);
      return order;
    }

    public CustomerOrder pay(long id, LocalDateTime at) {
      order = copyWithStatus(order, OrderStatus.PAID);
      return order;
    }

    public CustomerOrder cancelPaid(long id, LocalDateTime at) {
      order = copyWithStatus(order, OrderStatus.CANCELLED);
      return order;
    }

    private CustomerOrderDetails details() {
      List<OrderItemDetails> detailItems = items
        .stream()
        .map(item -> new OrderItemDetails(item, "SKU", "Товар"))
        .toList();
      return new CustomerOrderDetails(order, "Покупатель", detailItems);
    }
  }
}
