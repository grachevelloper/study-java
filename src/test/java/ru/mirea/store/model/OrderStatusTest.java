package ru.mirea.store.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderStatusTest {

  @Test
  void exposesOnlyAllowedLifecycleTransitions() {
    assertTrue(
      OrderStatus.NEW.allowedTargets().equals(
        Set.of(OrderStatus.PAID, OrderStatus.CANCELLED)
      )
    );
    assertTrue(
      OrderStatus.PAID.allowedTargets().equals(
        Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED)
      )
    );
    assertTrue(
      OrderStatus.SHIPPED.allowedTargets().equals(Set.of(OrderStatus.DELIVERED))
    );
    assertTrue(OrderStatus.DELIVERED.allowedTargets().isEmpty());
    assertTrue(OrderStatus.CANCELLED.allowedTargets().isEmpty());
    assertFalse(OrderStatus.NEW.canTransitionTo(OrderStatus.DELIVERED));
    assertFalse(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.NEW));
    assertFalse(OrderStatus.NEW.canTransitionTo(null));
  }
}
