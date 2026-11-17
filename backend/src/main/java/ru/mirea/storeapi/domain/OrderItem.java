package ru.mirea.storeapi.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private StoreOrder order;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Product product;

  private int quantity;
  private BigDecimal unitPrice;

  protected OrderItem() {}

  public OrderItem(StoreOrder order, Product product, int quantity, BigDecimal unitPrice) {
    this.order = order;
    this.product = product;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
  }

  public Product getProduct() {
    return product;
  }

  public int getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
  }
}
