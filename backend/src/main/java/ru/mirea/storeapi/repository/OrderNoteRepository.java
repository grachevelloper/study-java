package ru.mirea.storeapi.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.storeapi.domain.OrderNote;

public interface OrderNoteRepository extends JpaRepository<OrderNote, UUID> {
  List<OrderNote> findByOrder_PublicIdOrderByPinnedDescCreatedAtDesc(UUID orderId);
}
