package ru.mirea.library.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum RequestStatus {
    CREATED,
    APPROVED,
    ISSUED,
    RETURNED,
    REJECTED,
    CANCELLED;

    public Set<RequestStatus> allowedTargets() {
        return switch (this) {
            case CREATED -> Collections.unmodifiableSet(EnumSet.of(APPROVED, REJECTED, CANCELLED));
            case APPROVED -> Collections.unmodifiableSet(EnumSet.of(ISSUED, CANCELLED));
            case ISSUED -> Collections.unmodifiableSet(EnumSet.of(RETURNED));
            case RETURNED, REJECTED, CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(RequestStatus target) {
        return target != null && allowedTargets().contains(target);
    }
}
