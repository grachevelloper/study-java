package ru.mirea.store.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mirea.store.exception.BusinessRuleException;
import ru.mirea.store.model.*;

class OrderQueryServiceTest {
  private final OrderQueryService service = new OrderQueryService(
    new CustomerOrderService(null, null, null, Clock.systemUTC()));

  @Test
  void intersectsSearchStatusAndInclusivePeriodBeforeSorting() {
    var one = details(1, "Иван Петров", "Клавиатура", OrderStatus.NEW,
      LocalDate.of(2026, 10, 1), "20.00");
    var two = details(2, "Иван Петров", "Мышь", OrderStatus.PAID,
      LocalDate.of(2026, 10, 2), "10.00");
    var three = details(3, "Анна", "Клавиатура", OrderStatus.NEW,
      LocalDate.of(2026, 10, 3), "5.00");
    var source = List.of(one, two, three);
    assertEquals(List.of(one), service.select(source, "ПЕТРОВ", false,
      OrderStatus.NEW, LocalDate.of(2026, 10, 1),
      LocalDate.of(2026, 10, 3), OrderSortField.CREATED_AT,
      SortDirection.ASCENDING));
    assertEquals(List.of(three, one), service.select(source, "клав", true,
      OrderStatus.NEW, LocalDate.of(2026, 10, 1),
      LocalDate.of(2026, 10, 3), OrderSortField.TOTAL_AMOUNT,
      SortDirection.ASCENDING));
    assertTrue(service.select(source, "Нет такого", false, null, null,
      null, OrderSortField.CREATED_AT, SortDirection.ASCENDING).isEmpty());
    assertEquals(3, source.size());
  }

  @Test
  void rejectsIncompleteAndReversedDatePeriods() {
    LocalDate date = LocalDate.of(2026, 10, 1);
    assertThrows(BusinessRuleException.class, () -> service.select(
      List.of(), "", false, null, date, null,
      OrderSortField.CREATED_AT, SortDirection.ASCENDING));
    assertThrows(BusinessRuleException.class, () -> service.select(
      List.of(), "", false, null, date.plusDays(1), date,
      OrderSortField.CREATED_AT, SortDirection.ASCENDING));
  }

  private static CustomerOrderDetails details(
    long id, String customer, String product, OrderStatus status,
    LocalDate date, String price
  ) {
    var order = new CustomerOrder(id, 1, status, date.atStartOfDay(),
      null, null, null, null, null);
    var item = new OrderItem(id, id, id, 1, new BigDecimal(price));
    return new CustomerOrderDetails(order, customer,
      List.of(new OrderItemDetails(item, "SKU", product)));
  }
}
