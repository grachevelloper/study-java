package ru.mirea.store.model;

import java.time.LocalDateTime;
import java.util.Objects;

public final class CustomerOrder {

  private final long id;
  private final long customerId;
  private final OrderStatus status;
  private final LocalDateTime createdAt;
  private final LocalDateTime paidAt;
  private final LocalDateTime shippedAt;
  private final LocalDateTime deliveredAt;
  private final LocalDateTime cancelledAt;
  private final String note;

  public CustomerOrder(
    long id,
    long customerId,
    OrderStatus status,
    LocalDateTime createdAt,
    LocalDateTime paidAt,
    LocalDateTime shippedAt,
    LocalDateTime deliveredAt,
    LocalDateTime cancelledAt,
    String note
  ) {
    this.id = id;
    this.customerId = customerId;
    this.status = status;
    this.createdAt = createdAt;
    this.paidAt = paidAt;
    this.shippedAt = shippedAt;
    this.deliveredAt = deliveredAt;
    this.cancelledAt = cancelledAt;
    this.note = note;
  }

  public long getId() {
    return id;
  }

  public long getCustomerId() {
    return customerId;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getPaidAt() {
    return paidAt;
  }

  public LocalDateTime getShippedAt() {
    return shippedAt;
  }

  public LocalDateTime getDeliveredAt() {
    return deliveredAt;
  }

  public LocalDateTime getCancelledAt() {
    return cancelledAt;
  }

  public String getNote() {
    return note;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof CustomerOrder other)) return false;
    return (
      id == other.id &&
      customerId == other.customerId &&
      status == other.status &&
      Objects.equals(createdAt, other.createdAt) &&
      Objects.equals(paidAt, other.paidAt) &&
      Objects.equals(shippedAt, other.shippedAt) &&
      Objects.equals(deliveredAt, other.deliveredAt) &&
      Objects.equals(cancelledAt, other.cancelledAt) &&
      Objects.equals(note, other.note)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      id,
      customerId,
      status,
      createdAt,
      paidAt,
      shippedAt,
      deliveredAt,
      cancelledAt,
      note
    );
  }
}
