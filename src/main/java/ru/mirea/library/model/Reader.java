package ru.mirea.library.model;

import java.time.LocalDate;
import java.util.Objects;

public final class Reader {
    private final long id;
    private final String libraryCardNumber;
    private final String fullName;
    private final String email;
    private final boolean active;
    private final LocalDate registeredAt;

    public Reader(long id, String libraryCardNumber, String fullName, String email,
                  boolean active, LocalDate registeredAt) {
        this.id = id;
        this.libraryCardNumber = libraryCardNumber;
        this.fullName = fullName;
        this.email = email;
        this.active = active;
        this.registeredAt = registeredAt;
    }

    public long getId() { return id; }
    public String getLibraryCardNumber() { return libraryCardNumber; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
    public LocalDate getRegisteredAt() { return registeredAt; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Reader reader)) return false;
        return id == reader.id && active == reader.active
            && Objects.equals(libraryCardNumber, reader.libraryCardNumber)
            && Objects.equals(fullName, reader.fullName)
            && Objects.equals(email, reader.email)
            && Objects.equals(registeredAt, reader.registeredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, libraryCardNumber, fullName, email, active, registeredAt);
    }
}
