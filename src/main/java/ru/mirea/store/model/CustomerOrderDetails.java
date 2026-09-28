package ru.mirea.store.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class CustomerOrderDetails {
    private final CustomerOrder order; private final String customerName; private final List<OrderItemDetails> items;
    public CustomerOrderDetails(CustomerOrder order, String customerName, List<OrderItemDetails> items) {
        this.order = Objects.requireNonNull(order); this.customerName = customerName; this.items = List.copyOf(items);
    }
    public CustomerOrder getOrder() { return order; }
    public String getCustomerName() { return customerName; }
    public List<OrderItemDetails> getItems() { return items; }
    public BigDecimal getTotal() { return items.stream().map(value -> value.getItem().getLineTotal()).reduce(BigDecimal.ZERO, BigDecimal::add); }
}
