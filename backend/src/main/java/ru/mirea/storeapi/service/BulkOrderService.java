package ru.mirea.storeapi.service;

import java.util.ArrayList;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import ru.mirea.storeapi.dto.OrderDtos.*;
import ru.mirea.storeapi.error.BadRequestException;
import ru.mirea.storeapi.error.ConflictException;
import ru.mirea.storeapi.error.NotFoundException;

@Service
public class BulkOrderService {
  private final OrderService orders;

  public BulkOrderService(OrderService orders) {
    this.orders = orders;
  }

  public BulkResult apply(BulkRequest request) {
    var succeeded = new ArrayList<UUID>();
    var errors = new ArrayList<BulkError>();
    for (UUID id : request.ids()) {
      try {
        orders.bulkAction(id, request);
        succeeded.add(id);
      } catch (BadRequestException | ConflictException | NotFoundException ex) {
        errors.add(new BulkError(id, ex.getMessage()));
      } catch (DataIntegrityViolationException | OptimisticLockingFailureException ex) {
        errors.add(new BulkError(id, "Заказ конфликтует с текущими данными"));
      }
    }
    return new BulkResult(succeeded, errors);
  }
}
