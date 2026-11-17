package ru.mirea.storeapi.filter;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import ru.mirea.storeapi.domain.OrderItem;
import ru.mirea.storeapi.domain.StoreOrder;

public final class OrderSpecifications {
  private OrderSpecifications() {}

  public static Specification<StoreOrder> matching(OrderFilter filter) {
    filter.validate();
    return (order, query, cb) -> {
      var conditions = new ArrayList<Predicate>();

      if (filter.q() != null && !filter.q().isBlank()) {
        String text =
            "%"
                + filter
                    .q()
                    .strip()
                    .toLowerCase(Locale.ROOT)
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_")
                + "%";
        var products = query.subquery(Integer.class);
        var item = products.from(OrderItem.class);
        products
            .select(cb.literal(1))
            .where(
                cb.equal(item.get("order"), order),
                cb.like(cb.lower(item.get("product").get("name")), text, '!'));
        conditions.add(
            cb.or(
                cb.like(cb.lower(order.get("externalId")), text, '!'),
                cb.like(cb.lower(order.get("note")), text, '!'),
                cb.like(cb.lower(order.get("customer").get("fullName")), text, '!'),
                cb.exists(products)));
      }

      if (filter.customerId() != null)
        conditions.add(cb.equal(order.get("customer").get("id"), filter.customerId()));
      if (filter.status() != null) conditions.add(cb.equal(order.get("status"), filter.status()));
      if (filter.urgent() != null) conditions.add(cb.equal(order.get("urgent"), filter.urgent()));
      if (filter.priority() != null)
        conditions.add(cb.equal(order.get("priority"), filter.priority().name()));
      if (filter.tag() != null && !filter.tag().isBlank()) {
        conditions.add(
            cb.isMember(
                filter.tag().strip().toLowerCase(Locale.ROOT), order.<Set<String>>get("tags")));
      }
      if (filter.archive() != OrderFilter.Archive.ALL) {
        conditions.add(
            cb.equal(order.get("archived"), filter.archive() == OrderFilter.Archive.ARCHIVED));
      }
      if (filter.total() != null) {
        var total = order.<BigDecimal>get("total");
        var comparison = filter.totalOp() == null ? OrderFilter.Comparison.EQ : filter.totalOp();
        conditions.add(
            switch (comparison) {
              case EQ -> cb.equal(total, filter.total());
              case GT -> cb.greaterThan(total, filter.total());
              case GTE -> cb.greaterThanOrEqualTo(total, filter.total());
              case LT -> cb.lessThan(total, filter.total());
              case LTE -> cb.lessThanOrEqualTo(total, filter.total());
            });
      }
      return cb.and(conditions.toArray(Predicate[]::new));
    };
  }
}
