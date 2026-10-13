package ru.mirea.store.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import ru.mirea.store.exception.BusinessRuleException;
import ru.mirea.store.exception.EntityNotFoundException;
import ru.mirea.store.model.Product;
import ru.mirea.store.repository.ProductRepository;
import ru.mirea.store.validation.InputValidator;

public final class ProductService {

  private final ProductRepository repository;

  public ProductService(ProductRepository repository) {
    this.repository = Objects.requireNonNull(repository);
  }

  public Product create(
    String sku,
    String name,
    BigDecimal price,
    int stockQuantity
  ) {
    String normalizedSku = InputValidator.sku(sku);
    String normalizedName = InputValidator.requiredText(
      name,
      "Название",
      InputValidator.MAX_PRODUCT_NAME_LENGTH
    );
    BigDecimal normalizedPrice = InputValidator.money(price);
    int normalizedStock = InputValidator.stock(stockQuantity);
    if (repository.findAll().stream().anyMatch(value ->
      value.getSku().equalsIgnoreCase(normalizedSku))) {
      throw new BusinessRuleException("SKU уже используется товаром.");
    }
    return repository.save(
      new Product(
        0,
        normalizedSku,
        normalizedName,
        normalizedPrice,
        normalizedStock,
        true
      )
    );
  }

  public List<Product> getAll() {
    return repository.findAll();
  }

  public Product getById(long id) {
    return repository
      .findById(id)
      .orElseThrow(() ->
        new EntityNotFoundException("Товар с ID " + id + " не найден.")
      );
  }
}
