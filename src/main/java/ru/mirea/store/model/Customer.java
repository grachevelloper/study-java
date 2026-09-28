package ru.mirea.store.model;

import java.time.LocalDateTime;
import java.util.Objects;

public final class Customer {

  private final long id;
  private final String fullName;
  private final String email;
  private final String phone;
  private final boolean active;
  private final LocalDateTime registeredAt;

  public Customer(
    long id,
    String fullName,
    String email,
    String phone,
    boolean active,
    LocalDateTime registeredAt
  ) {
    this.id = id;
    this.fullName = fullName;
    this.email = email;
    this.phone = phone;
    this.active = active;
    this.registeredAt = registeredAt;
  }

  public long getId() {
    return id;
  }

  public String getFullName() {
    return fullName;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public boolean isActive() {
    return active;
  }

  public LocalDateTime getRegisteredAt() {
    return registeredAt;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof Customer other)) return false;
    return (
      id == other.id &&
      active == other.active &&
      Objects.equals(fullName, other.fullName) &&
      Objects.equals(email, other.email) &&
      Objects.equals(phone, other.phone) &&
      Objects.equals(registeredAt, other.registeredAt)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, fullName, email, phone, active, registeredAt);
  }
}
