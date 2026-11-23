package ru.mirea.storeapi.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.storeapi.domain.OrderNote;
import ru.mirea.storeapi.domain.Role;
import ru.mirea.storeapi.domain.StoreOrder;
import ru.mirea.storeapi.domain.UserAccount;
import ru.mirea.storeapi.dto.NoteDtos.NoteRequest;
import ru.mirea.storeapi.dto.NoteDtos.NoteResponse;
import ru.mirea.storeapi.error.ConflictException;
import ru.mirea.storeapi.error.NotFoundException;
import ru.mirea.storeapi.repository.OrderNoteRepository;
import ru.mirea.storeapi.repository.StoreOrderRepository;
import ru.mirea.storeapi.repository.UserAccountRepository;
import ru.mirea.storeapi.security.RequestUser;

@Service
public class NoteService {
  private final OrderNoteRepository notes;
  private final StoreOrderRepository orders;
  private final UserAccountRepository users;
  private final AuditService audit;

  public NoteService(
      OrderNoteRepository notes,
      StoreOrderRepository orders,
      UserAccountRepository users,
      AuditService audit) {
    this.notes = notes;
    this.orders = orders;
    this.users = users;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<NoteResponse> list(UUID orderId) {
    requireOrder(orderId);
    return notes.findByOrder_PublicIdOrderByPinnedDescCreatedAtDesc(orderId).stream()
        .map(NoteService::toResponse)
        .toList();
  }

  @Transactional
  public NoteResponse create(UUID orderId, NoteRequest request) {
    StoreOrder order = requireOrder(orderId);
    UserAccount actor = users.getReferenceById(RequestUser.require().id());
    OrderNote note =
        new OrderNote(UUID.randomUUID(), order, actor, request.body().trim(), request.pinned());
    notes.saveAndFlush(note);
    audit.record(
        "NOTE_CREATED",
        "ORDER_NOTE",
        note.getId(),
        "Добавлена заметка к заказу",
        Map.of("orderId", orderId));
    return toResponse(note);
  }

  @Transactional
  public NoteResponse update(UUID orderId, UUID noteId, NoteRequest request) {
    OrderNote note = requireNote(orderId, noteId);
    assertOwnerOrAdmin(note, RequestUser.require());
    if (request.version() == null || request.version() != note.getVersion())
      throw new ConflictException("Заметка уже изменена. Обновите данные");
    note.setBody(request.body().trim());
    note.setPinned(request.pinned());
    notes.flush();
    audit.record(
        "NOTE_UPDATED", "ORDER_NOTE", noteId, "Изменена заметка", Map.of("orderId", orderId));
    return toResponse(note);
  }

  @Transactional
  public void delete(UUID orderId, UUID noteId) {
    OrderNote note = requireNote(orderId, noteId);
    assertOwnerOrAdmin(note, RequestUser.require());
    notes.delete(note);
    audit.record(
        "NOTE_DELETED", "ORDER_NOTE", noteId, "Удалена заметка", Map.of("orderId", orderId));
  }

  private StoreOrder requireOrder(UUID id) {
    return orders.findByPublicId(id).orElseThrow(() -> new NotFoundException("Заказ не найден"));
  }

  private OrderNote requireNote(UUID orderId, UUID noteId) {
    OrderNote note =
        notes.findById(noteId).orElseThrow(() -> new NotFoundException("Заметка не найдена"));
    if (!note.getOrder().getPublicId().equals(orderId))
      throw new NotFoundException("Заметка не найдена");
    return note;
  }

  private static void assertOwnerOrAdmin(OrderNote note, RequestUser actor) {
    if (!note.getAuthor().getId().equals(actor.id()) && !actor.roles().contains(Role.ADMIN)) {
      throw new AccessDeniedException("Only note author or administrator can modify it");
    }
  }

  private static NoteResponse toResponse(OrderNote note) {
    return new NoteResponse(
        note.getId(),
        note.getOrder().getPublicId(),
        note.getAuthor().getId(),
        note.getAuthor().getDisplayName(),
        note.getBody(),
        note.isPinned(),
        note.getCreatedAt(),
        note.getUpdatedAt(),
        note.getVersion());
  }
}
