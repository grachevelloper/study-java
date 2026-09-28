package ru.mirea.store.repository;
import java.time.LocalDate; import java.time.LocalDateTime; import java.util.List; import java.util.Optional;
import ru.mirea.store.model.CustomerOrder; import ru.mirea.store.model.CustomerOrderDetails; import ru.mirea.store.model.OrderItem; import ru.mirea.store.model.OrderStatus;
public interface CustomerOrderRepository {
    CustomerOrder create(CustomerOrder order, List<OrderItem> items);
    List<CustomerOrderDetails> findAll(); Optional<CustomerOrderDetails> findById(long id);
    CustomerOrder updateNew(CustomerOrder order, List<OrderItem> items); boolean deleteById(long id);
    List<CustomerOrderDetails> findByProductName(String query); List<CustomerOrderDetails> findByCustomerName(String query);
    List<CustomerOrderDetails> findByStatus(OrderStatus status); List<CustomerOrderDetails> findByCreatedAt(LocalDate from, LocalDate to);
    CustomerOrder transition(long id, OrderStatus expected, OrderStatus target, LocalDateTime changedAt);
    CustomerOrder pay(long id, LocalDateTime paidAt); CustomerOrder cancelPaid(long id, LocalDateTime cancelledAt);
}
