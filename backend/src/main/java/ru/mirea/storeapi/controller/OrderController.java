package ru.mirea.storeapi.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.mirea.storeapi.dto.ImportDtos.*;
import ru.mirea.storeapi.dto.OrderDtos.*;
import ru.mirea.storeapi.dto.PageResponse;
import ru.mirea.storeapi.filter.OrderFilter;
import ru.mirea.storeapi.service.BulkOrderService;
import ru.mirea.storeapi.service.CsvOrderService;
import ru.mirea.storeapi.service.OrderService;
import ru.mirea.storeapi.web.PageableFactory;

@RestController
@RequestMapping("/api/orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
    private final OrderService orders;
    private final CsvOrderService csv;
    private final BulkOrderService bulk;
    private final PageableFactory pages;

    public OrderController(OrderService orders, CsvOrderService csv,
        BulkOrderService bulk, PageableFactory pages) {
        this.orders = orders;
        this.csv = csv;
        this.bulk = bulk;
        this.pages = pages;
    }

    @GetMapping
    public PageResponse<OrderRow> search(
        @ParameterObject @ModelAttribute OrderFilter filter,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size,
        @RequestParam(defaultValue = "created,desc") String sort) {
        return PageResponse
            .from(orders.search(filter, pages.orders(page, size, sort)));
    }

    @GetMapping("/{id}")
    public OrderDetail get(@PathVariable UUID id) {
        return orders.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public OrderDetail create(@Valid @RequestBody OrderRequest request) {
        return orders.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public OrderDetail update(@PathVariable UUID id,
        @Valid @RequestBody OrderRequest request) {
        return orders.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public OrderRow archive(@PathVariable UUID id) {
        return orders.archive(id, true);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public OrderRow restore(@PathVariable UUID id) {
        return orders.archive(id, false);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        orders.delete(id);
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public BulkResult bulk(@Valid @RequestBody BulkRequest request) {
        return bulk.apply(request);
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public ImportPreview preview(@RequestPart("file") MultipartFile file,
        @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
        return csv.preview(file, mode);
    }

    @PostMapping(value = "/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public ImportResult apply(@RequestPart("file") MultipartFile file,
        @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
        return csv.apply(file, mode);
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(
        @ParameterObject @ModelAttribute OrderFilter filter)
        throws IOException {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=orders.csv").body(csv.export(filter));
    }
}
