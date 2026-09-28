package ru.mirea.store.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class SystemStatistics {
    private final long customerCount; private final long productCount; private final long stockUnitCount;
    private final long orderCount; private final Map<OrderStatus, Long> ordersByStatus;
    private final BigDecimal deliveredRevenue; private final long outOfStockProductCount;
    public SystemStatistics(long customerCount, long productCount, long stockUnitCount, long orderCount,
                            Map<OrderStatus, Long> ordersByStatus, BigDecimal deliveredRevenue,
                            long outOfStockProductCount) {
        this.customerCount = customerCount; this.productCount = productCount; this.stockUnitCount = stockUnitCount;
        this.orderCount = orderCount; EnumMap<OrderStatus, Long> copy = new EnumMap<>(OrderStatus.class);
        copy.putAll(ordersByStatus); this.ordersByStatus = Collections.unmodifiableMap(copy);
        this.deliveredRevenue = deliveredRevenue; this.outOfStockProductCount = outOfStockProductCount;
    }
    public long getCustomerCount() { return customerCount; }
    public long getProductCount() { return productCount; }
    public long getStockUnitCount() { return stockUnitCount; }
    public long getOrderCount() { return orderCount; }
    public Map<OrderStatus, Long> getOrdersByStatus() { return ordersByStatus; }
    public BigDecimal getDeliveredRevenue() { return deliveredRevenue; }
    public long getOutOfStockProductCount() { return outOfStockProductCount; }
}
