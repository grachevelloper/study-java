package ru.mirea.store;

import java.sql.*; import java.time.Clock;
import ru.mirea.store.config.*; import ru.mirea.store.export.ExcelExporter; import ru.mirea.store.repository.jdbc.*; import ru.mirea.store.service.*; import ru.mirea.store.ui.*;

public final class Main {
    private Main(){}
    public static void main(String[] args){DatabaseManager database=new DatabaseManager(DatabaseConfig.fromEnvironment());if(!available(database)){System.err.println("Не удалось подключиться к PostgreSQL. Проверьте DB_URL, DB_USER, DB_PASSWORD и Docker Compose.");return;}Clock clock=Clock.systemDefaultZone();JdbcCustomerRepository customers=new JdbcCustomerRepository(database);JdbcProductRepository products=new JdbcProductRepository(database);JdbcCustomerOrderRepository orders=new JdbcCustomerOrderRepository(database);new ConsoleApplication(new CustomerService(customers,clock),new ProductService(products),new CustomerOrderService(orders,customers,products,clock),new StatisticsService(new JdbcStatisticsRepository(database)),new ExcelExporter(),new ConsoleInput(System.in,System.out),new ConsolePrinter(System.out)).run();}
    private static boolean available(DatabaseManager database){try(Connection c=database.getConnection();Statement s=c.createStatement()){s.execute("SELECT 1");return true;}catch(SQLException e){return false;}}
}
