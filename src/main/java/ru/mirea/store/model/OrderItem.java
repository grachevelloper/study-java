package ru.mirea.store.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class OrderItem {

  private final long id;
  private final long orderId;
  private final long productId;
  private final int quantity;
  private final BigDecimal unitPrice;

  public OrderItem(
    long id,
    long orderId,
    long productId,
    int quantity,
    BigDecimal unitPrice
  ) {
    this.id = id;
    this.orderId = orderId;
    this.productId = productId;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
  }

  public long getId() {
    return id;
  }

  public long getOrderId() {
    return orderId;
  }

  public long getProductId() {
    return productId;
  }

  public int getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getLineTotal() {
    return unitPrice.multiply(BigDecimal.valueOf(quantity));
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof OrderItem other)) return false;
    return (
      id == other.id &&
      orderId == other.orderId &&
      productId == other.productId &&
      quantity == other.quantity &&
      Objects.equals(unitPrice, other.unitPrice)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, orderId, productId, quantity, unitPrice);
  }
}
