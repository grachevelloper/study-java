package ru.mirea.store.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mirea.store.model.CustomerOrder;
import ru.mirea.store.model.CustomerOrderDetails;
import ru.mirea.store.model.OrderItem;
import ru.mirea.store.model.OrderItemDetails;
import ru.mirea.store.model.OrderSortField;
import ru.mirea.store.model.OrderStatus;
import ru.mirea.store.model.SortDirection;

class OrderSortingTest {

  @Test
  void sortsCopiesByCreationDateAndTotalInBothDirections() {
    CustomerOrderService service = new CustomerOrderService(
      null,
      null,
      null,
      Clock.systemUTC()
    );
    CustomerOrderDetails olderExpensive = details(
      1,
      LocalDateTime.of(2026, 1, 1, 0, 0),
      "20.00"
    );
    CustomerOrderDetails newerCheap = details(
      2,
      LocalDateTime.of(2026, 1, 2, 0, 0),
      "5.00"
    );
    List<CustomerOrderDetails> source = List.of(olderExpensive, newerCheap);

    List<CustomerOrderDetails> byDate = service.sort(
      source,
      OrderSortField.CREATED_AT,
      SortDirection.DESCENDING
    );
    List<CustomerOrderDetails> byTotal = service.sort(
      source,
      OrderSortField.TOTAL_AMOUNT,
      SortDirection.ASCENDING
    );

    assertEquals(
      List.of(2L, 1L),
      byDate
        .stream()
        .map(d -> d.getOrder().getId())
        .toList()
    );
    assertEquals(
      List.of(2L, 1L),
      byTotal
        .stream()
        .map(d -> d.getOrder().getId())
        .toList()
    );
    assertNotSame(source, byDate);
  }

  private static CustomerOrderDetails details(
    long id,
    LocalDateTime createdAt,
    String price
  ) {
    CustomerOrder order = new CustomerOrder(
      id,
      1,
      OrderStatus.NEW,
      createdAt,
      null,
      null,
      null,
      null,
      null
    );
    OrderItem item = new OrderItem(id, id, id, 1, new BigDecimal(price));
    return new CustomerOrderDetails(
      order,
      "Покупатель",
      List.of(new OrderItemDetails(item, "SKU", "Товар"))
    );
  }
}
