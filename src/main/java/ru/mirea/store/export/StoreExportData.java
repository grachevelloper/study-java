package ru.mirea.store.export;
import java.util.List; import java.util.Objects;
import ru.mirea.store.model.*;
public record StoreExportData(List<Customer> customers,List<Product> products,List<CustomerOrderDetails> orders,SystemStatistics statistics){public StoreExportData{customers=List.copyOf(Objects.requireNonNull(customers));products=List.copyOf(Objects.requireNonNull(products));orders=List.copyOf(Objects.requireNonNull(orders));Objects.requireNonNull(statistics);}}
