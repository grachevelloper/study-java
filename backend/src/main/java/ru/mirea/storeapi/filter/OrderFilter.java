package ru.mirea.storeapi.filter;

import java.math.BigDecimal;
import ru.mirea.storeapi.domain.OrderPriority;
import ru.mirea.storeapi.domain.OrderStatus;
import ru.mirea.storeapi.error.BadRequestException;

public record OrderFilter(String q, Long customerId, OrderStatus status,
    Boolean urgent, OrderPriority priority, String tag, Archive archive,
    Comparison totalOp, BigDecimal total) {
    public OrderFilter {
        archive = archive == null ? Archive.ACTIVE : archive;
    }

    public void validate() {
        if (q != null && q.length() > 200)
            throw new BadRequestException("Поиск: не более 200 символов");
        if (customerId != null && customerId < 1)
            throw new BadRequestException("Некорректный покупатель");
        if (tag != null && tag.length() > 50)
            throw new BadRequestException("Тег: не более 50 символов");
        if ((total == null && totalOp != null)
            || (total != null && total.signum() < 0)) {
            throw new BadRequestException(
                "Укажите неотрицательную сумму для сравнения");
        }
    }

    public enum Archive {
        ACTIVE, ARCHIVED, ALL
    }

    public enum Comparison {
        EQ, GT, GTE, LT, LTE
    }
}
