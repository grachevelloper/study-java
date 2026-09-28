package ru.mirea.store.repository.jdbc;

import java.sql.*; import java.util.*;
import ru.mirea.store.config.ConnectionProvider; import ru.mirea.store.exception.DataAccessException;
import ru.mirea.store.model.Customer; import ru.mirea.store.repository.CustomerRepository;

public final class JdbcCustomerRepository implements CustomerRepository {
    private static final String COLUMNS = "id, full_name, email, phone, active, registered_at"; private final ConnectionProvider provider;
    public JdbcCustomerRepository(ConnectionProvider provider) { this.provider = Objects.requireNonNull(provider); }
    public Customer save(Customer value) { String sql = "INSERT INTO customers (full_name,email,phone,active,registered_at) VALUES (?,?,?,?,?)"; try (Connection c=provider.getConnection(); PreparedStatement s=c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) { s.setString(1,value.getFullName()); s.setString(2,value.getEmail()); s.setString(3,value.getPhone()); s.setBoolean(4,value.isActive()); s.setObject(5,value.getRegisteredAt()); s.executeUpdate(); try(ResultSet keys=s.getGeneratedKeys()){ if(!keys.next()) throw new DataAccessException("База данных не вернула ID покупателя."); return new Customer(keys.getLong(1),value.getFullName(),value.getEmail(),value.getPhone(),value.isActive(),value.getRegisteredAt()); } } catch(SQLException e){ throw new DataAccessException("Не удалось сохранить покупателя.",e); } }
    public List<Customer> findAll(){ List<Customer> result=new ArrayList<>(); try(Connection c=provider.getConnection(); PreparedStatement s=c.prepareStatement("SELECT "+COLUMNS+" FROM customers ORDER BY id"); ResultSet rs=s.executeQuery()){ while(rs.next()) result.add(RowMappers.toCustomer(rs)); return result; } catch(SQLException e){ throw new DataAccessException("Не удалось загрузить покупателей.",e); } }
    public Optional<Customer> findById(long id){ try(Connection c=provider.getConnection(); PreparedStatement s=c.prepareStatement("SELECT "+COLUMNS+" FROM customers WHERE id=?")){ s.setLong(1,id); try(ResultSet rs=s.executeQuery()){ return rs.next()?Optional.of(RowMappers.toCustomer(rs)):Optional.empty(); } } catch(SQLException e){ throw new DataAccessException("Не удалось загрузить покупателя.",e); } }
}
