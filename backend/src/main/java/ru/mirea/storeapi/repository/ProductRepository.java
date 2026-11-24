package ru.mirea.storeapi.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import ru.mirea.storeapi.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByOrderByNameAsc();

    List<Product> findBySkuIn(Collection<String> skus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> lockAll(Collection<Long> ids);
}
