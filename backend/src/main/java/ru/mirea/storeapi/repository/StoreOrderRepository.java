package ru.mirea.storeapi.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import ru.mirea.storeapi.domain.StoreOrder;

public interface StoreOrderRepository
    extends JpaRepository<StoreOrder, Long>, JpaSpecificationExecutor<StoreOrder> {
  Optional<StoreOrder> findByPublicId(UUID publicId);

  Optional<StoreOrder> findByExternalId(String externalId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from StoreOrder o where o.publicId = :publicId")
  Optional<StoreOrder> lockByPublicId(UUID publicId);
}
