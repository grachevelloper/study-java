package ru.mirea.storeapi.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.mirea.storeapi.dto.CatalogDtos.*;
import ru.mirea.storeapi.service.CatalogService;

@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "bearerAuth")
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/customers")
    public List<CustomerResponse> customers(
        @RequestParam(defaultValue = "false") boolean activeOnly) {
        return catalog.customers(activeOnly);
    }

    @GetMapping("/products")
    public List<ProductResponse> products() {
        return catalog.products();
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse create(
        @Valid @RequestBody CustomerRequest request) {
        return catalog.create(request);
    }

    @PutMapping("/customers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse update(@PathVariable long id,
        @Valid @RequestBody CustomerRequest request) {
        return catalog.update(id, request);
    }

    @PatchMapping("/customers/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse setActive(@PathVariable long id,
        @RequestParam boolean active) {
        return catalog.setActive(id, active);
    }
}
