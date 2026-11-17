package ru.mirea.storeapi.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.Formula;

@Entity
@Table(name = "customer_orders")
public class StoreOrder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private UUID publicId = UUID.randomUUID();

  @Column(nullable = false)
  private String externalId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Customer customer;

  @Enumerated(EnumType.STRING)
  private OrderStatus status = OrderStatus.NEW;

  private String note;
  private boolean urgent;
  private boolean archived;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime paidAt;
  private LocalDateTime shippedAt;
  private LocalDateTime deliveredAt;
  private LocalDateTime cancelledAt;

  @Version private long version;

  @ElementCollection
  @CollectionTable(name = "order_tag", joinColumns = @JoinColumn(name = "order_id"))
  @Column(name = "tag", nullable = false)
  private Set<String> tags = new LinkedHashSet<>();

  @Formula(
      "coalesce((select sum(i.quantity * i.unit_price) from order_items i where i.order_id = id),"
          + " 0)")
  private BigDecimal total;

  @Formula(
      "case when status in ('DELIVERED', 'CANCELLED') then 'LOW' when urgent then 'HIGH' else"
          + " 'NORMAL' end")
  private String priority;

  protected StoreOrder() {}

  public StoreOrder(LocalDateTime now) {
    createdAt = now;
    updatedAt = now;
  }

  public void update(
      String externalId, Customer customer, String note, boolean urgent, Set<String> tags) {
    this.externalId = externalId;
    this.customer = customer;
    this.note = note;
    this.urgent = urgent;
    replaceTags(tags);
  }

  public void replaceTags(Set<String> newTags) {
    tags.retainAll(newTags);
    tags.addAll(newTags);
  }

  public void transition(OrderStatus target, LocalDateTime now) {
    // Existing orders can contain local timestamps written by the desktop client.
    LocalDateTime previous =
        switch (status) {
          case NEW -> createdAt;
          case PAID -> paidAt;
          case SHIPPED -> shippedAt;
          case DELIVERED -> deliveredAt;
          case CANCELLED -> cancelledAt;
        };
    LocalDateTime eventTime = now.isBefore(previous) ? previous : now;
    switch (target) {
      case PAID -> paidAt = eventTime;
      case SHIPPED -> shippedAt = eventTime;
      case DELIVERED -> deliveredAt = eventTime;
      case CANCELLED -> cancelledAt = eventTime;
      case NEW -> throw new IllegalArgumentException("NEW is an initial status");
    }
    status = target;
  }

  public void touch(LocalDateTime now) {
    updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1_000);
  }

  public Long getId() {
    return id;
  }

  public UUID getPublicId() {
    return publicId;
  }

  public String getExternalId() {
    return externalId;
  }

  public Customer getCustomer() {
    return customer;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public String getNote() {
    return note;
  }

  public boolean isUrgent() {
    return urgent;
  }

  public boolean isArchived() {
    return archived;
  }

  public void setArchived(boolean archived) {
    this.archived = archived;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public OrderPriority getPriority() {
    return OrderPriority.valueOf(priority);
  }

  public long getVersion() {
    return version;
  }

  public Set<String> getTags() {
    return tags;
  }
}
