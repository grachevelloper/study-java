package ru.mirea.storeapi.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.storeapi.domain.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByOrderByFullNameAsc();

    List<Customer> findByActiveTrueOrderByFullNameAsc();

    Optional<Customer> findByEmail(String email);
}
