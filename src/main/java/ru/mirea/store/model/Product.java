package ru.mirea.store.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class Product {

  private final long id;
  private final String sku;
  private final String name;
  private final BigDecimal price;
  private final int stockQuantity;
  private final boolean active;

  public Product(
    long id,
    String sku,
    String name,
    BigDecimal price,
    int stockQuantity,
    boolean active
  ) {
    this.id = id;
    this.sku = sku;
    this.name = name;
    this.price = price;
    this.stockQuantity = stockQuantity;
    this.active = active;
  }

  public long getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public int getStockQuantity() {
    return stockQuantity;
  }

  public boolean isActive() {
    return active;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof Product other)) return false;
    return (
      id == other.id &&
      stockQuantity == other.stockQuantity &&
      active == other.active &&
      Objects.equals(sku, other.sku) &&
      Objects.equals(name, other.name) &&
      Objects.equals(price, other.price)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, sku, name, price, stockQuantity, active);
  }
}
