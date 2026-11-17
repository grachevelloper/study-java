package ru.mirea.storeapi.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {
  @Id private Long id;
  private String sku;
  private String name;
  private BigDecimal price;
  private int stockQuantity;
  private boolean active;

  protected Product() {}

  public Long getId() {
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

  public void adjustStock(int quantity) {
    stockQuantity += quantity;
  }
}
