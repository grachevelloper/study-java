package ru.mirea.storeapi.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.storeapi.dto.NoteDtos.NoteRequest;
import ru.mirea.storeapi.dto.NoteDtos.NoteResponse;
import ru.mirea.storeapi.service.NoteService;

@RestController
@RequestMapping("/api/orders/{orderId}/notes")
@SecurityRequirement(name = "bearerAuth")
public class NoteController {
  private final NoteService notes;

  public NoteController(NoteService notes) {
    this.notes = notes;
  }

  @GetMapping
  public List<NoteResponse> list(@PathVariable UUID orderId) {
    return notes.list(orderId);
  }

  @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
  @PostMapping
  public ResponseEntity<NoteResponse> create(
      @PathVariable UUID orderId, @Valid @RequestBody NoteRequest request) {
    return ResponseEntity.status(201).body(notes.create(orderId, request));
  }

  @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
  @PutMapping("/{noteId}")
  public NoteResponse update(
      @PathVariable UUID orderId,
      @PathVariable UUID noteId,
      @Valid @RequestBody NoteRequest request) {
    return notes.update(orderId, noteId, request);
  }

  @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
  @DeleteMapping("/{noteId}")
  public ResponseEntity<Void> delete(@PathVariable UUID orderId, @PathVariable UUID noteId) {
    notes.delete(orderId, noteId);
    return ResponseEntity.noContent().build();
  }
}
