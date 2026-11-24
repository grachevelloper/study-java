package ru.mirea.storeapi.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.storeapi.domain.Customer;
import ru.mirea.storeapi.dto.CatalogDtos.*;
import ru.mirea.storeapi.error.BadRequestException;
import ru.mirea.storeapi.error.ConflictException;
import ru.mirea.storeapi.error.NotFoundException;
import ru.mirea.storeapi.repository.CustomerRepository;
import ru.mirea.storeapi.repository.ProductRepository;

@Service
public class CatalogService {
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final AuditService audit;

    public CatalogService(CustomerRepository customers,
        ProductRepository products, AuditService audit) {
        this.customers = customers;
        this.products = products;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> customers(boolean activeOnly) {
        var list = activeOnly
            ? customers.findByActiveTrueOrderByFullNameAsc()
            : customers.findAllByOrderByFullNameAsc();
        return list.stream().map(CatalogService::response).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> products() {
        return products.findAllByOrderByNameAsc().stream()
            .map(p -> new ProductResponse(p.getId(), p.getSku(), p.getName(),
                p.getPrice(), p.getStockQuantity(), p.isActive()))
            .toList();
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer(LocalDateTime.now(ZoneOffset.UTC));
        fill(customer, request);
        customers.saveAndFlush(customer);
        record("CUSTOMER_CREATED", customer, "Создан покупатель");
        return response(customer);
    }

    @Transactional
    public CustomerResponse update(long id, CustomerRequest request) {
        Customer customer = require(id);
        fill(customer, request);
        record("CUSTOMER_UPDATED", customer, "Изменён покупатель");
        return response(customer);
    }

    @Transactional
    public CustomerResponse setActive(long id, boolean active) {
        Customer customer = require(id);
        if (customer.isActive() != active) {
            customer.setActive(active);
            record(active ? "CUSTOMER_ENABLED" : "CUSTOMER_DISABLED", customer,
                active ? "Покупатель включён" : "Покупатель отключён");
        }
        return response(customer);
    }

    private Customer require(long id) {
        return customers.findById(id)
            .orElseThrow(() -> new NotFoundException("Покупатель не найден"));
    }

    private void fill(Customer customer, CustomerRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (!email.matches(
            "[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}")) {
            throw new BadRequestException("Некорректный email покупателя");
        }
        customers.findByEmail(email)
            .filter(other -> !Objects.equals(customer.getId(), other.getId()))
            .ifPresent(other -> {
                throw new ConflictException("Такой email уже используется");
            });
        String phone = request.phone() == null || request.phone().isBlank()
            ? null
            : request.phone().strip();
        if (phone != null) {
            int digits = phone.replaceAll("[^0-9]", "").length();
            if (digits < 5 || digits > 15)
                throw new BadRequestException(
                    "Телефон должен содержать от 5 до 15 цифр");
        }
        customer.update(request.fullName().strip(), email, phone);
    }

    private void record(String event, Customer customer, String summary) {
        audit.record(event, "CUSTOMER", customer.getPublicId(),
            summary + " №" + customer.getId(),
            Map.of("customerId", customer.getId()));
    }

    private static CustomerResponse response(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getFullName(),
            customer.getEmail(), customer.getPhone(), customer.isActive(),
            customer.getRegisteredAt());
    }
}
