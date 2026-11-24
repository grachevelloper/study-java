package ru.mirea.storeapi.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.storeapi.dto.AnalyticsDtos.AnalyticsResponse;
import ru.mirea.storeapi.dto.AnalyticsDtos.CustomerCount;

@Service
public class AnalyticsService {
    private final EntityManager em;

    public AnalyticsService(EntityManager em) {
        this.em = em;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AnalyticsResponse summary() {
        Tuple stats = em.createQuery("""
                select count(o.id), sum(case when o.archived = true then 1L else 0L end),
                       sum(case when o.urgent = true then 1L else 0L end), sum(o.total)
                from StoreOrder o
                """, Tuple.class).getSingleResult();
        long total = stats.get(0, Long.class);
        long archived = total == 0 ? 0 : stats.get(1, Long.class);
        long urgent = total == 0 ? 0 : stats.get(2, Long.class);
        BigDecimal average = total == 0
            ? BigDecimal.ZERO
            : stats.get(3, BigDecimal.class).divide(BigDecimal.valueOf(total),
                2, RoundingMode.HALF_UP);
        var customers = em.createQuery("""
                select o.customer.id, o.customer.fullName, count(o.id)
                from StoreOrder o
                group by o.customer.id, o.customer.fullName order by o.customer.fullName
                """, Tuple.class).getResultList().stream()
            .map(t -> new CustomerCount(t.get(0, Long.class),
                t.get(1, String.class), t.get(2, Long.class)))
            .toList();
        var statuses = em.createQuery("""
                select o.status, count(o.id) from StoreOrder o group by o.status order by o.status
                """, Tuple.class).getResultList();
        var priorities = em.createQuery("""
                select o.priority, count(o.id) from StoreOrder o group by o.priority order by o.priority
                """, Tuple.class).getResultList();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Map<String, Long> byPriority = new LinkedHashMap<>();
        statuses.forEach(
            t -> byStatus.put(t.get(0).toString(), t.get(1, Long.class)));
        priorities.forEach(
            t -> byPriority.put(t.get(0, String.class), t.get(1, Long.class)));
        return new AnalyticsResponse(total, total - archived, archived, urgent,
            average, customers, byStatus, byPriority);
    }
}
