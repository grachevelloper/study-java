package ru.mirea.storeapi.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CatalogDtos {
    private CatalogDtos() {
    }

    public record CustomerRequest(
        @NotBlank @Size(max = 200) @Pattern(regexp = "[^\\p{Cntrl}]*") String fullName,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 50) @Pattern(regexp = "\\+?[()0-9 -]*") String phone) {
    }

    public record CustomerResponse(long id, String fullName, String email,
        String phone, boolean active, LocalDateTime registeredAt) {
    }

    public record ProductResponse(long id, String sku, String name,
        BigDecimal price, int stockQuantity, boolean active) {
    }
}
