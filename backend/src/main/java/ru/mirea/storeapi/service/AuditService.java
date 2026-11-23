package ru.mirea.storeapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.storeapi.domain.AuditEvent;
import ru.mirea.storeapi.domain.UserAccount;
import ru.mirea.storeapi.dto.AuditDtos.AuditResponse;
import ru.mirea.storeapi.repository.AuditEventRepository;
import ru.mirea.storeapi.security.RequestUser;
import ru.mirea.storeapi.web.RequestMetadata;

@Service
public class AuditService {
  private final AuditEventRepository events;
  private final ObjectMapper objectMapper;
  private final RequestMetadata metadata;

  public AuditService(
      AuditEventRepository events, ObjectMapper objectMapper, RequestMetadata metadata) {
    this.events = events;
    this.objectMapper = objectMapper;
    this.metadata = metadata;
  }

  @Transactional
  public void record(
      String eventType,
      String entityType,
      UUID entityId,
      String summary,
      Map<String, ?> safeDetails) {
    RequestUser principal = RequestUser.currentOrNull();
    String json = null;
    if (safeDetails != null && !safeDetails.isEmpty()) {
      try {
        json = objectMapper.writeValueAsString(safeDetails);
      } catch (JsonProcessingException ignored) {
        json = "{\"serialization\":\"failed\"}";
      }
    }
    events.save(
        new AuditEvent(
            UUID.randomUUID(),
            principal == null ? null : principal.id(),
            principal == null ? null : principal.email(),
            eventType,
            entityType,
            entityId,
            summary,
            metadata.correlationId(),
            metadata.ipAddress(),
            metadata.userAgent(),
            json));
  }

  public void recordLogin(UserAccount user, String clientType) {
    events.save(
        new AuditEvent(
            UUID.randomUUID(),
            user.getId(),
            user.getEmail(),
            "LOGIN_SUCCESS",
            "USER",
            user.getId(),
            "Успешный вход",
            metadata.correlationId(),
            metadata.ipAddress(),
            metadata.userAgent(),
            "{\"clientType\":\"" + clientType + "\"}"));
  }

  @Transactional(readOnly = true)
  public Page<AuditResponse> search(String query, Pageable pageable) {
    String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    Specification<AuditEvent> spec =
        (root, cq, cb) -> {
          if (normalized.isBlank()) return cb.conjunction();
          String like = "%" + normalized + "%";
          return cb.or(
              cb.like(cb.lower(root.get("actorEmail")), like),
              cb.like(cb.lower(root.get("eventType")), like),
              cb.like(cb.lower(root.get("entityType")), like),
              cb.like(cb.lower(root.get("summary")), like),
              cb.like(cb.lower(root.get("correlationId")), like));
        };
    return events.findAll(spec, pageable).map(AuditService::toResponse);
  }

  private static AuditResponse toResponse(AuditEvent event) {
    return new AuditResponse(
        event.getId(),
        event.getActorId(),
        event.getActorEmail(),
        event.getEventType(),
        event.getEntityType(),
        event.getEntityId(),
        event.getSummary(),
        event.getCorrelationId(),
        event.getIpAddress(),
        event.getOccurredAt(),
        event.getDetailsJson());
  }

}
