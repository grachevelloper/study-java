package ru.mirea.storeapi.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.storeapi.domain.*;
import ru.mirea.storeapi.dto.OrderDtos.*;
import ru.mirea.storeapi.error.BadRequestException;
import ru.mirea.storeapi.error.ConflictException;
import ru.mirea.storeapi.error.NotFoundException;
import ru.mirea.storeapi.filter.OrderFilter;
import ru.mirea.storeapi.filter.OrderSpecifications;
import ru.mirea.storeapi.repository.*;

@Service
public class OrderService {
  private final StoreOrderRepository orders;
  private final OrderItemRepository items;
  private final CustomerRepository customers;
  private final ProductRepository products;
  private final AuditService audit;
  private final EntityManager entityManager;

  public OrderService(
      StoreOrderRepository orders,
      OrderItemRepository items,
      CustomerRepository customers,
      ProductRepository products,
      AuditService audit,
      EntityManager entityManager) {
    this.orders = orders;
    this.items = items;
    this.customers = customers;
    this.products = products;
    this.audit = audit;
    this.entityManager = entityManager;
  }

  @Transactional(readOnly = true)
  public Page<OrderRow> search(OrderFilter filter, Pageable pageable) {
    return orders.findAll(OrderSpecifications.matching(filter), pageable).map(OrderService::row);
  }

  @Transactional(readOnly = true)
  public OrderDetail get(UUID id) {
    StoreOrder order = orders.findByPublicId(id).orElseThrow(this::missing);
    return new OrderDetail(
        row(order),
        items.findByOrderIdOrderByProductId(order.getId()).stream()
            .map(OrderService::item)
            .toList());
  }

  @Transactional
  public OrderDetail create(OrderRequest request) {
    if (externalIdIsTaken(request.externalId(), null))
      throw new ConflictException("Номер заказа уже занят");
    OrderStatus target = target(request, OrderStatus.NEW);
    Customer customer = activeCustomer(request.customerId());
    Map<Long, Product> catalog = productsFor(request.items(), true);
    validateStock(target, request.items(), catalog);
    StoreOrder order = new StoreOrder(now());
    order.update(
        request.externalId(),
        customer,
        clean(request.note()),
        request.urgent(),
        tags(request.tags()));
    orders.saveAndFlush(order);
    Map<Long, OrderItem> orderItems = replaceItems(order, request.items(), catalog, List.of());
    applyStatus(order, target, orderItems.values(), catalog);
    audit.record(
        "ORDER_CREATED",
        "ORDER",
        order.getPublicId(),
        "Создан заказ " + order.getExternalId(),
        Map.of());
    return refreshed(order);
  }

  @Transactional
  public OrderDetail update(UUID id, OrderRequest request) {
    StoreOrder order = lock(id);
    if (request.version() == null || request.version() != order.getVersion()) {
      throw new ConflictException("Заказ уже изменён. Откройте карточку заново");
    }
    List<OrderItem> current = items.findByOrderIdOrderByProductId(order.getId());
    OrderStatus target = target(request, order.getStatus());
    boolean detailsChanged = validateUpdate(order, request, current);
    if (detailsChanged) {
      Customer customer = activeCustomer(request.customerId());
      Map<Long, Product> catalog = productsFor(request.items(), true);
      validateStock(target, request.items(), catalog);
      order.update(
          request.externalId(),
          customer,
          clean(request.note()),
          request.urgent(),
          tags(request.tags()));
      var changed = replaceItems(order, request.items(), catalog, current);
      applyStatus(order, target, changed.values(), catalog);
    } else {
      order.replaceTags(tags(request.tags()));
      applyStatus(order, target, current, transitionProducts(order, target, current));
    }
    order.touch(now());
    audit.record(
        "ORDER_UPDATED",
        "ORDER",
        id,
        "Изменён заказ " + order.getExternalId(),
        Map.of("status", order.getStatus()));
    return refreshed(order);
  }

  @Transactional
  public OrderRow archive(UUID id, boolean archived) {
    StoreOrder order = lock(id);
    if (order.isArchived() != archived) {
      order.setArchived(archived);
      order.touch(now());
      orders.flush();
      audit.record(
          archived ? "ORDER_ARCHIVED" : "ORDER_RESTORED",
          "ORDER",
          id,
          archived ? "Заказ отправлен в архив" : "Заказ восстановлен из архива",
          Map.of());
    }
    return row(order);
  }

  @Transactional
  public void delete(UUID id) {
    StoreOrder order = lock(id);
    if (!order.isArchived()
        || (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.CANCELLED)) {
      throw new ConflictException("Окончательно удалить можно архивный новый или отменённый заказ");
    }
    orders.delete(order);
    audit.record(
        "ORDER_DELETED",
        "ORDER",
        id,
        "Окончательно удалён заказ " + order.getExternalId(),
        Map.of());
  }

  @Transactional
  public void bulkAction(UUID id, BulkRequest request) {
    if (request.action() == BulkAction.ARCHIVE || request.action() == BulkAction.RESTORE) {
      archive(id, request.action() == BulkAction.ARCHIVE);
      return;
    }
    StoreOrder order = lock(id);
    requireActive(order);
    switch (request.action()) {
      case CHANGE_STATUS -> {
        var current = items.findByOrderIdOrderByProductId(order.getId());
        applyStatus(
            order,
            checkedTransition(order.getStatus(), request.status()),
            current,
            transitionProducts(order, request.status(), current));
      }
      case ADD_TAG -> {
        var tag = tag(request.tag());
        if (!order.getTags().contains(tag) && order.getTags().size() >= 30) {
          throw new BadRequestException("У заказа уже 30 тегов");
        }
        order.getTags().add(tag);
      }
      case REMOVE_TAG -> order.getTags().remove(tag(request.tag()));
      default -> throw new IllegalArgumentException("Unsupported action");
    }
    order.touch(now());
    audit.record(
        "ORDER_BULK_" + request.action(), "ORDER", id, "Массовое изменение заказа", Map.of());
  }

  @Transactional(readOnly = true)
  public void validateImport(OrderRequest request, UUID existingId) {
    StoreOrder order =
        existingId == null ? null : orders.findByPublicId(existingId).orElseThrow(this::missing);
    OrderStatus currentStatus = order == null ? OrderStatus.NEW : order.getStatus();
    OrderStatus target = target(request, currentStatus);
    List<OrderItem> currentItems =
        order == null ? List.of() : items.findByOrderIdOrderByProductId(order.getId());
    if (order == null || validateUpdate(order, request, currentItems)) {
      activeCustomer(request.customerId());
      validateStock(target, request.items(), productsFor(request.items(), false));
    } else if (currentStatus == OrderStatus.NEW && target == OrderStatus.PAID) {
      validateStock(target, request.items(), productsFor(request.items(), false));
    } else if (currentStatus == OrderStatus.PAID && target == OrderStatus.CANCELLED) {
      currentItems.forEach(item -> validateReturn(item.getProduct(), item.getQuantity()));
    }
  }

  static OrderRow row(StoreOrder order) {
    return new OrderRow(
        order.getPublicId(),
        order.getExternalId(),
        order.getCustomer().getId(),
        order.getCustomer().getFullName(),
        order.getNote(),
        order.getStatus(),
        order.getTotal(),
        order.isUrgent(),
        order.getPriority(),
        order.isArchived(),
        order.getCreatedAt(),
        order.getUpdatedAt(),
        order.getVersion(),
        Set.copyOf(order.getTags()));
  }

  static ItemResponse item(OrderItem item) {
    Product product = item.getProduct();
    return new ItemResponse(
        product.getId(),
        product.getSku(),
        product.getName(),
        item.getQuantity(),
        item.getUnitPrice());
  }

  private OrderDetail refreshed(StoreOrder order) {
    entityManager.flush();
    entityManager.refresh(order);
    return new OrderDetail(
        row(order),
        items.findByOrderIdOrderByProductId(order.getId()).stream()
            .map(OrderService::item)
            .toList());
  }

  private StoreOrder lock(UUID id) {
    return orders.lockByPublicId(id).orElseThrow(this::missing);
  }

  private NotFoundException missing() {
    return new NotFoundException("Заказ не найден");
  }

  private boolean validateUpdate(StoreOrder order, OrderRequest request, List<OrderItem> current) {
    requireActive(order);
    if (externalIdIsTaken(request.externalId(), order.getPublicId())) {
      throw new ConflictException("Номер заказа уже занят");
    }
    Set<ItemRequest> before =
        current.stream()
            .map(i -> new ItemRequest(i.getProduct().getId(), i.getQuantity()))
            .collect(Collectors.toSet());
    boolean changed =
        !order.getExternalId().equals(request.externalId())
            || order.getCustomer().getId() != request.customerId()
            || !Objects.equals(order.getNote(), clean(request.note()))
            || order.isUrgent() != request.urgent()
            || before.size() != request.items().size()
            || !before.equals(new HashSet<>(request.items()));
    if (changed && order.getStatus() != OrderStatus.NEW) {
      throw new BadRequestException("Данные и позиции можно менять только у нового заказа");
    }
    return changed;
  }

  private boolean externalIdIsTaken(String externalId, UUID orderId) {
    return orders
        .findByExternalId(externalId)
        .filter(order -> !order.getPublicId().equals(orderId))
        .isPresent();
  }

  private static void requireActive(StoreOrder order) {
    if (order.isArchived()) throw new BadRequestException("Сначала восстановите заказ из архива");
  }

  private Customer activeCustomer(long id) {
    Customer customer =
        customers.findById(id).orElseThrow(() -> new BadRequestException("Покупатель не найден"));
    if (!customer.isActive()) throw new BadRequestException("Покупатель отключён");
    return customer;
  }

  private Map<Long, Product> productsFor(List<ItemRequest> requested, boolean lock) {
    Set<Long> ids = new HashSet<>();
    for (ItemRequest item : requested) {
      if (!ids.add(item.productId())) throw new BadRequestException("Товар повторяется в заказе");
    }
    List<Product> catalog = lock ? lockedProducts(ids) : products.findAllById(ids);
    if (catalog.size() != ids.size() || catalog.stream().anyMatch(product -> !product.isActive())) {
      throw new BadRequestException("Товар не найден или отключён");
    }
    return catalog.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
  }

  private Map<Long, Product> transitionProducts(
      StoreOrder order, OrderStatus target, List<OrderItem> current) {
    if (target == order.getStatus()
        || (target != OrderStatus.PAID
            && !(target == OrderStatus.CANCELLED && order.getStatus() == OrderStatus.PAID)))
      return Map.of();
    var ids = current.stream().map(item -> item.getProduct().getId()).toList();
    return lockedProducts(ids).stream()
        .collect(Collectors.toMap(Product::getId, Function.identity()));
  }

  private List<Product> lockedProducts(Collection<Long> ids) {
    List<Product> locked = products.lockAll(ids);
    // A product loaded with its order item can predate the row lock.
    locked.forEach(entityManager::refresh);
    return locked;
  }

  private Map<Long, OrderItem> replaceItems(
      StoreOrder order,
      List<ItemRequest> requested,
      Map<Long, Product> catalog,
      List<OrderItem> current) {
    Map<Long, OrderItem> old =
        current.stream()
            .collect(Collectors.toMap(i -> i.getProduct().getId(), Function.identity()));
    Map<Long, OrderItem> result = new LinkedHashMap<>();
    for (ItemRequest request : requested) {
      Product product = catalog.get(request.productId());
      OrderItem item = old.remove(request.productId());
      if (item == null) {
        item = items.save(new OrderItem(order, product, request.quantity(), product.getPrice()));
      } else {
        item.setQuantity(request.quantity());
      }
      result.put(request.productId(), item);
    }
    items.deleteAll(old.values());
    return result;
  }

  private static OrderStatus target(OrderRequest request, OrderStatus current) {
    return request.status() == null ? current : checkedTransition(current, request.status());
  }

  private static OrderStatus checkedTransition(OrderStatus current, OrderStatus target) {
    if (target == null || (target != current && !current.canTransitionTo(target))) {
      throw new BadRequestException("Недопустимый переход из " + current + " в " + target);
    }
    return target;
  }

  private static void validateStock(
      OrderStatus target, List<ItemRequest> requested, Map<Long, Product> catalog) {
    if (target != OrderStatus.PAID) return;
    for (ItemRequest item : requested) {
      Product product = catalog.get(item.productId());
      if (product.getStockQuantity() < item.quantity()) {
        throw new BadRequestException("Недостаточно на складе: " + product.getName());
      }
    }
  }

  private static void applyStatus(
      StoreOrder order,
      OrderStatus target,
      Collection<OrderItem> orderItems,
      Map<Long, Product> catalog) {
    if (target == order.getStatus()) return;
    if (target == OrderStatus.PAID) {
      for (OrderItem item : orderItems) {
        Product product = catalog.get(item.getProduct().getId());
        if (!product.isActive() || product.getStockQuantity() < item.getQuantity()) {
          throw new BadRequestException("Недостаточно доступного товара: " + product.getName());
        }
        product.adjustStock(-item.getQuantity());
      }
    }
    if (target == OrderStatus.CANCELLED && order.getStatus() == OrderStatus.PAID) {
      for (OrderItem item : orderItems) {
        Product product = catalog.get(item.getProduct().getId());
        validateReturn(product, item.getQuantity());
        product.adjustStock(item.getQuantity());
      }
    }
    order.transition(target, now());
  }

  private static void validateReturn(Product product, int quantity) {
    if (product.getStockQuantity() + quantity > 1_000_000) {
      throw new BadRequestException("Возврат превышает допустимый остаток: " + product.getName());
    }
  }

  private static Set<String> tags(Set<String> input) {
    return input.stream()
        .map(OrderService::tag)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static String tag(String input) {
    if (input == null || input.isBlank()) throw new BadRequestException("Укажите тег");
    return input.toLowerCase(Locale.ROOT);
  }

  private static String clean(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static LocalDateTime now() {
    return LocalDateTime.now(ZoneOffset.UTC);
  }
}
