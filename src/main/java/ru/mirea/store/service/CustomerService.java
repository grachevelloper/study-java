package ru.mirea.store.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import ru.mirea.store.exception.BusinessRuleException;
import ru.mirea.store.exception.EntityNotFoundException;
import ru.mirea.store.model.Customer;
import ru.mirea.store.repository.CustomerRepository;
import ru.mirea.store.validation.InputValidator;

public final class CustomerService {

  private final CustomerRepository repository;
  private final Clock clock;

  public CustomerService(CustomerRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository);
    this.clock = Objects.requireNonNull(clock);
  }

  public Customer create(String fullName, String email, String phone) {
    String name = InputValidator.requiredText(
      fullName,
      "ФИО",
      InputValidator.MAX_NAME_LENGTH
    );
    String normalizedEmail = InputValidator.email(email);
    String normalizedPhone = InputValidator.phone(phone);
    return repository.save(
      new Customer(
        0,
        name,
        normalizedEmail,
        normalizedPhone,
        true,
        LocalDateTime.now(clock)
      )
    );
  }

  public List<Customer> getAll() {
    return repository.findAll();
  }

  public Customer getById(long id) {
    return repository
      .findById(id)
      .orElseThrow(() ->
        new EntityNotFoundException("Покупатель с ID " + id + " не найден.")
      );
  }
}
