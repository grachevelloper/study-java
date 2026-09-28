package ru.mirea.store.repository;

import java.util.List;
import java.util.Optional;
import ru.mirea.store.model.Customer;

public interface CustomerRepository {
  Customer save(Customer customer);
  List<Customer> findAll();
  Optional<Customer> findById(long id);
}
