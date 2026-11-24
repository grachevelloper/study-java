package ru.mirea.storeapi.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    private String fullName;
    private String email;
    private String phone;
    private boolean active = true;
    private LocalDateTime registeredAt;

    protected Customer() {
    }

    public Customer(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
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

    public void update(String fullName, String email, String phone) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
