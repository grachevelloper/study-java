package ru.mirea.store.model;

import java.util.Objects;

public final class OrderItemDetails {

  private final OrderItem item;
  private final String productSku;
  private final String productName;

  public OrderItemDetails(
    OrderItem item,
    String productSku,
    String productName
  ) {
    this.item = Objects.requireNonNull(item);
    this.productSku = productSku;
    this.productName = productName;
  }

  public OrderItem getItem() {
    return item;
  }

  public String getProductSku() {
    return productSku;
  }

  public String getProductName() {
    return productName;
  }
}
