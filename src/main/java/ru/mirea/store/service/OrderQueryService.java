package ru.mirea.store.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import ru.mirea.store.exception.BusinessRuleException;
import ru.mirea.store.model.CustomerOrderDetails;
import ru.mirea.store.model.OrderSortField;
import ru.mirea.store.model.OrderStatus;
import ru.mirea.store.model.SortDirection;
import ru.mirea.store.validation.InputValidator;

/** Applies all GUI criteria to the same set of orders. */
public final class OrderQueryService {
  private final CustomerOrderService orders;

  public OrderQueryService(CustomerOrderService orders) {
    this.orders = orders;
  }

  public List<CustomerOrderDetails> select(
    List<CustomerOrderDetails> source,
    String query,
    boolean byProduct,
    OrderStatus status,
    LocalDate from,
    LocalDate to,
    OrderSortField sort,
    SortDirection direction
  ) {
    if ((from == null) != (to == null)) {
      throw new BusinessRuleException("Заполните обе даты периода.");
    }
    if (from != null && from.isAfter(to)) {
      throw new BusinessRuleException(
        "Начальная дата не должна быть позже конечной."
      );
    }
    String text = InputValidator.optionalText(
      query, "Поиск", InputValidator.MAX_SEARCH_LENGTH
    );
    String needle = text == null ? "" : text.toLowerCase(Locale.ROOT);
    List<CustomerOrderDetails> result = source.stream().filter(value -> {
      var order = value.getOrder();
      LocalDate date = order.getCreatedAt().toLocalDate();
      boolean matchesText = byProduct
        ? value.getItems().stream().anyMatch(item ->
          item.getProductName().toLowerCase(Locale.ROOT).contains(needle))
        : value.getCustomerName().toLowerCase(Locale.ROOT).contains(needle);
      return matchesText && (status == null || order.getStatus() == status)
        && (from == null || (!date.isBefore(from) && !date.isAfter(to)));
    }).toList();
    return orders.sort(result, sort, direction);
  }
}
