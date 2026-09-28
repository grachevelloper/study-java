package ru.mirea.store.repository.jdbc;

import java.sql.*;
import java.util.*;
import ru.mirea.store.config.ConnectionProvider;
import ru.mirea.store.exception.DataAccessException;
import ru.mirea.store.model.Product;
import ru.mirea.store.repository.ProductRepository;

public final class JdbcProductRepository implements ProductRepository {

  private static final String COLUMNS =
    "id, sku, name, price, stock_quantity, active";
  private final ConnectionProvider provider;

  public JdbcProductRepository(ConnectionProvider provider) {
    this.provider = Objects.requireNonNull(provider);
  }

  public Product save(Product v) {
    String sql =
      "INSERT INTO products " +
      "(sku,name,price,stock_quantity,active) VALUES (?,?,?,?,?)";
    try (
      Connection c = provider.getConnection();
      PreparedStatement s = c.prepareStatement(
        sql,
        Statement.RETURN_GENERATED_KEYS
      )
    ) {
      s.setString(1, v.getSku());
      s.setString(2, v.getName());
      s.setBigDecimal(3, v.getPrice());
      s.setInt(4, v.getStockQuantity());
      s.setBoolean(5, v.isActive());
      s.executeUpdate();
      try (ResultSet k = s.getGeneratedKeys()) {
        if (!k.next()) throw new DataAccessException(
          "База данных не вернула ID товара."
        );
        return new Product(
          k.getLong(1),
          v.getSku(),
          v.getName(),
          v.getPrice(),
          v.getStockQuantity(),
          v.isActive()
        );
      }
    } catch (SQLException e) {
      throw new DataAccessException("Не удалось сохранить товар.", e);
    }
  }

  public List<Product> findAll() {
    List<Product> r = new ArrayList<>();
    try (
      Connection c = provider.getConnection();
      PreparedStatement s = c.prepareStatement(
        "SELECT " + COLUMNS + " FROM products ORDER BY id"
      );
      ResultSet rs = s.executeQuery()
    ) {
      while (rs.next()) r.add(RowMappers.toProduct(rs));
      return r;
    } catch (SQLException e) {
      throw new DataAccessException("Не удалось загрузить товары.", e);
    }
  }

  public Optional<Product> findById(long id) {
    try (
      Connection c = provider.getConnection();
      PreparedStatement s = c.prepareStatement(
        "SELECT " + COLUMNS + " FROM products WHERE id=?"
      )
    ) {
      s.setLong(1, id);
      try (ResultSet rs = s.executeQuery()) {
        return rs.next()
          ? Optional.of(RowMappers.toProduct(rs))
          : Optional.empty();
      }
    } catch (SQLException e) {
      throw new DataAccessException("Не удалось загрузить товар.", e);
    }
  }
}
