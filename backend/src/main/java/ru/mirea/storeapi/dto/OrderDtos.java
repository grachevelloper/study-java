package ru.mirea.storeapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ru.mirea.storeapi.domain.OrderPriority;
import ru.mirea.storeapi.domain.OrderStatus;

public final class OrderDtos {
    public static final String CODE_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,99}";
    public static final String TAG_PATTERN = "[\\p{L}\\p{N}_-]{1,50}";

    private OrderDtos() {
    }

    public record ItemRequest(@Positive long productId,
        @Min(1) @Max(1_000_000) int quantity) {
    }

    public record OrderRequest(
        @NotBlank @Pattern(regexp = CODE_PATTERN) String externalId,
        @Positive long customerId,
        @Size(max = 500) @Pattern(regexp = "[^\\p{Cntrl}]*") String note,
        boolean urgent,
        @NotNull @Size(max = 30) Set<@NotBlank @Pattern(regexp = TAG_PATTERN) String> tags,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid ItemRequest> items,
        OrderStatus status, @PositiveOrZero Long version) {
    }

    public record OrderRow(UUID id, String externalId, long customerId,
        String customerName, String note, OrderStatus status, BigDecimal total,
        boolean urgent, OrderPriority priority, boolean archived,
        LocalDateTime createdAt, LocalDateTime updatedAt, long version,
        Set<String> tags) {
    }

    public record ItemResponse(long productId, String sku, String name,
        int quantity, BigDecimal unitPrice) {
    }

    public record OrderDetail(OrderRow order, List<ItemResponse> items) {
    }

    public enum BulkAction {
        ARCHIVE, RESTORE, CHANGE_STATUS, ADD_TAG, REMOVE_TAG
    }

    public record BulkRequest(@NotEmpty @Size(max = 100) Set<@NotNull UUID> ids,
        @NotNull BulkAction action, OrderStatus status,
        @Pattern(regexp = TAG_PATTERN) String tag) {
    }

    public record BulkError(UUID id, String message) {
    }

    public record BulkResult(List<UUID> succeeded, List<BulkError> errors) {
    }
}
