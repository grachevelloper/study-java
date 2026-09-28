package ru.mirea.store.model;

import java.util.Set;

public enum OrderStatus {
    NEW, PAID, SHIPPED, DELIVERED, CANCELLED;

    public Set<OrderStatus> allowedTargets() {
        return switch (this) {
            case NEW -> Set.of(PAID, CANCELLED);
            case PAID -> Set.of(SHIPPED, CANCELLED);
            case SHIPPED -> Set.of(DELIVERED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return target != null && allowedTargets().contains(target);
    }
}
