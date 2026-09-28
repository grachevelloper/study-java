package ru.mirea.store.repository.jdbc;

import java.sql.ResultSet; import java.sql.SQLException;
import ru.mirea.store.model.*;

final class RowMappers {
    private RowMappers() { }
    static Customer toCustomer(ResultSet rs) throws SQLException { return new Customer(rs.getLong("id"), rs.getString("full_name"), rs.getString("email"), rs.getString("phone"), rs.getBoolean("active"), rs.getObject("registered_at", java.time.LocalDateTime.class)); }
    static Product toProduct(ResultSet rs) throws SQLException { return new Product(rs.getLong("id"), rs.getString("sku"), rs.getString("name"), rs.getBigDecimal("price"), rs.getInt("stock_quantity"), rs.getBoolean("active")); }
    static CustomerOrder toOrder(ResultSet rs) throws SQLException { return new CustomerOrder(rs.getLong("id"), rs.getLong("customer_id"), OrderStatus.valueOf(rs.getString("status")), rs.getObject("created_at", java.time.LocalDateTime.class), rs.getObject("paid_at", java.time.LocalDateTime.class), rs.getObject("shipped_at", java.time.LocalDateTime.class), rs.getObject("delivered_at", java.time.LocalDateTime.class), rs.getObject("cancelled_at", java.time.LocalDateTime.class), rs.getString("note")); }
    static OrderItem toItem(ResultSet rs) throws SQLException { return new OrderItem(rs.getLong("item_id"), rs.getLong("order_id"), rs.getLong("product_id"), rs.getInt("quantity"), rs.getBigDecimal("unit_price")); }
}
