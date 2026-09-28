package ru.mirea.store.repository;
import java.util.List; import java.util.Optional; import ru.mirea.store.model.Product;
public interface ProductRepository { Product save(Product product); List<Product> findAll(); Optional<Product> findById(long id); }
