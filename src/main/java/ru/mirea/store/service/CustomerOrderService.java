package ru.mirea.store.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import ru.mirea.store.exception.BusinessRuleException;
import ru.mirea.store.exception.EntityNotFoundException;
import ru.mirea.store.model.*;
import ru.mirea.store.repository.*;
import ru.mirea.store.validation.InputValidator;

public final class CustomerOrderService {

  private final CustomerOrderRepository orders;
  private final CustomerRepository customers;
  private final ProductRepository products;
  private final Clock clock;

  public CustomerOrderService(
    CustomerOrderRepository orders,
    CustomerRepository customers,
    ProductRepository products,
    Clock clock
  ) {
    this.orders = orders;
    this.customers = customers;
    this.products = products;
    this.clock = Objects.requireNonNull(clock);
  }

  public CustomerOrder createOrder(
    long customerId,
    List<OrderItemInput> inputs,
    String note
  ) {
    Customer customer = requireActiveCustomer(customerId);
    List<OrderItem> items = buildItems(inputs, 0);
    CustomerOrder order = new CustomerOrder(
      0,
      customer.getId(),
      OrderStatus.NEW,
      LocalDateTime.now(clock),
      null,
      null,
      null,
      null,
      normalizeNote(note)
    );
    return orders.create(order, items);
  }

  public List<CustomerOrderDetails> getAll() {
    return orders.findAll();
  }

  public CustomerOrderDetails getById(long id) {
    return orders
      .findById(id)
      .orElseThrow(() ->
        new EntityNotFoundException("Заказ с ID " + id + " не найден.")
      );
  }

  public CustomerOrder updateNew(
    long orderId,
    long customerId,
    List<OrderItemInput> inputs,
    String note
  ) {
    CustomerOrder current = getById(orderId).getOrder();
    if (current.getStatus() != OrderStatus.NEW) throw new BusinessRuleException(
      "Изменять можно только заказ в статусе NEW."
    );
    requireActiveCustomer(customerId);
    List<OrderItem> items = buildItems(inputs, orderId);
    CustomerOrder updated = new CustomerOrder(
      orderId,
      customerId,
      OrderStatus.NEW,
      current.getCreatedAt(),
      null,
      null,
      null,
      null,
      normalizeNote(note)
    );
    return orders.updateNew(updated, items);
  }

  public void delete(long orderId) {
    OrderStatus status = getById(orderId).getOrder().getStatus();
    if (
      status != OrderStatus.NEW && status != OrderStatus.CANCELLED
    ) throw new BusinessRuleException(
      "Удалять можно только NEW или CANCELLED заказ."
    );
    if (!orders.deleteById(orderId)) throw new BusinessRuleException(
      "Заказ уже изменён или не найден."
    );
  }

  public CustomerOrder changeStatus(long id, OrderStatus target) {
    CustomerOrder current = getById(id).getOrder();
    if (
      !current.getStatus().canTransitionTo(target)
    ) throw new BusinessRuleException(
      "Недопустимый переход статуса: " +
        current.getStatus() +
        " -> " +
        target +
        "."
    );
    LocalDateTime now = LocalDateTime.now(clock);
    if (
      current.getStatus() == OrderStatus.NEW && target == OrderStatus.PAID
    ) return orders.pay(id, now);
    if (
      current.getStatus() == OrderStatus.PAID && target == OrderStatus.CANCELLED
    ) return orders.cancelPaid(id, now);
    return orders.transition(id, current.getStatus(), target, now);
  }

  public List<CustomerOrderDetails> searchByProductName(String query) {
    return orders.findByProductName(requiredQuery(query));
  }

  public List<CustomerOrderDetails> searchByCustomerName(String query) {
    return orders.findByCustomerName(requiredQuery(query));
  }

  public List<CustomerOrderDetails> filterByStatus(OrderStatus status) {
    return orders.findByStatus(Objects.requireNonNull(status));
  }

  public List<CustomerOrderDetails> filterByCreatedAt(
    LocalDate from,
    LocalDate to
  ) {
    InputValidator.date(from, "Начальная дата");
    InputValidator.date(to, "Конечная дата");
    if (from.isAfter(to)) throw new BusinessRuleException(
      "Некорректный диапазон дат."
    );
    return orders.findByCreatedAt(from, to);
  }

  public List<CustomerOrderDetails> sort(
    List<CustomerOrderDetails> source,
    OrderSortField field,
    SortDirection direction
  ) {
    Comparator<CustomerOrderDetails> comparator = switch (field) {
      case CREATED_AT -> Comparator.comparing(value ->
        value.getOrder().getCreatedAt()
      );
      case TOTAL_AMOUNT -> Comparator.comparing(CustomerOrderDetails::getTotal);
    };
    if (direction == SortDirection.DESCENDING) comparator =
      comparator.reversed();
    return source
      .stream()
      .sorted(comparator.thenComparingLong(value -> value.getOrder().getId()))
      .toList();
  }

  private Customer requireActiveCustomer(long id) {
    Customer customer = customers
      .findById(id)
      .orElseThrow(() ->
        new EntityNotFoundException("Покупатель с ID " + id + " не найден.")
      );
    if (!customer.isActive()) throw new BusinessRuleException(
      "Покупатель неактивен."
    );
    return customer;
  }

  private List<OrderItem> buildItems(
    List<OrderItemInput> inputs,
    long orderId
  ) {
    if (inputs == null || inputs.isEmpty()) throw new BusinessRuleException(
      "Заказ должен содержать хотя бы одну позицию."
    );
    if (
      inputs.size() > InputValidator.MAX_ORDER_ITEMS
    ) throw new BusinessRuleException(
      "В заказе может быть не больше " +
        InputValidator.MAX_ORDER_ITEMS +
        " позиций."
    );
    Set<Long> productIds = new HashSet<>();
    List<OrderItem> result = new ArrayList<>();
    for (OrderItemInput input : inputs) {
      if (input == null) throw new BusinessRuleException(
        "Позиция заказа не может быть пустой."
      );
      if (input.productId() <= 0) throw new BusinessRuleException(
        "ID товара должен быть положительным."
      );
      InputValidator.quantity(input.quantity(), "Количество");
      if (!productIds.add(input.productId())) throw new BusinessRuleException(
        "Товар не может повторяться в заказе."
      );
      Product product = products
        .findById(input.productId())
        .orElseThrow(() ->
          new EntityNotFoundException(
            "Товар с ID " + input.productId() + " не найден."
          )
        );
      if (!product.isActive()) throw new BusinessRuleException(
        "Товар " + product.getName() + " неактивен."
      );
      result.add(
        new OrderItem(
          0,
          orderId,
          product.getId(),
          input.quantity(),
          product.getPrice()
        )
      );
    }
    return List.copyOf(result);
  }

  private static String normalizeNote(String note) {
    return InputValidator.optionalText(
      note,
      "Примечание",
      InputValidator.MAX_NOTE_LENGTH
    );
  }

  private static String requiredQuery(String query) {
    return InputValidator.requiredText(
      query,
      "Строка поиска",
      InputValidator.MAX_SEARCH_LENGTH
    );
  }

  public record OrderItemInput(long productId, int quantity) {}
}
