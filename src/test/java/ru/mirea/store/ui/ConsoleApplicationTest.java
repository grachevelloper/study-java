package ru.mirea.store.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.*; import java.math.BigDecimal; import java.nio.charset.StandardCharsets; import java.time.Clock; import java.util.*;
import org.junit.jupiter.api.Test;
import ru.mirea.store.export.DataExporter; import ru.mirea.store.model.*; import ru.mirea.store.repository.*; import ru.mirea.store.service.*;

class ConsoleApplicationTest {
    @Test void invalidInputDoesNotTerminateAndProductsCanBeListed(){
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();PrintStream output=new PrintStream(bytes,true,StandardCharsets.UTF_8);
        CustomerRepository customers=new EmptyCustomers();Product product=new Product(1,"SKU-1","Тестовый товар",new BigDecimal("10.00"),3,true);ProductRepository products=new FixedProducts(product);CustomerOrderRepository orders=new EmptyOrders();
        StatisticsRepository statistics=()->new SystemStatistics(0,1,3,0,new EnumMap<>(OrderStatus.class),BigDecimal.ZERO,0);
        DataExporter exporter=(data,path)->path.resolve("test.xlsx");
        ConsoleApplication app=new ConsoleApplication(new CustomerService(customers,Clock.systemUTC()),new ProductService(products),new CustomerOrderService(orders,customers,products,Clock.systemUTC()),new StatisticsService(statistics),exporter,new ConsoleInput(new ByteArrayInputStream("abc\n2\n2\n0\n0\n".getBytes(StandardCharsets.UTF_8)),output),new ConsolePrinter(output));
        app.run();String text=bytes.toString(StandardCharsets.UTF_8);assertTrue(text.contains("Ошибка"));assertTrue(text.contains("Тестовый товар"));assertTrue(text.contains("До свидания"));
    }
    @Test void createsAndViewsMultiItemOrderWithoutLeavingConsole(){
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();PrintStream output=new PrintStream(bytes,true,StandardCharsets.UTF_8);
        Customer customer=new Customer(1,"Покупатель","buyer@example.com",null,true,java.time.LocalDateTime.now());CustomerRepository customers=new FixedCustomers(customer);
        List<Product> catalog=List.of(new Product(1,"A","Товар A",new BigDecimal("10.00"),5,true),new Product(2,"B","Товар B",new BigDecimal("5.00"),5,true));ProductRepository products=new ListProducts(catalog);MemoryOrders orderRepository=new MemoryOrders(catalog);
        StatisticsRepository statistics=()->new SystemStatistics(1,2,10,1,new EnumMap<>(OrderStatus.class),BigDecimal.ZERO,0);
        String script="3\n1\n1\n2\n1\n2\n2\n1\nсрочно\n3\n1\n0\n0\n";
        ConsoleApplication app=new ConsoleApplication(new CustomerService(customers,Clock.systemUTC()),new ProductService(products),new CustomerOrderService(orderRepository,customers,products,Clock.systemUTC()),new StatisticsService(statistics),(data,path)->path.resolve("test.xlsx"),new ConsoleInput(new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),output),new ConsolePrinter(output));
        app.run();String text=bytes.toString(StandardCharsets.UTF_8);assertTrue(text.contains("Заказ создан, ID 1"));assertTrue(text.contains("Заказ #1"));assertTrue(text.contains("Товар A"));assertTrue(text.contains("Товар B"));assertTrue(text.contains("25.00"));
    }
    private static final class EmptyCustomers implements CustomerRepository{public Customer save(Customer v){return v;}public List<Customer>findAll(){return List.of();}public Optional<Customer>findById(long id){return Optional.empty();}}
    private record FixedProducts(Product value)implements ProductRepository{public Product save(Product v){return v;}public List<Product>findAll(){return List.of(value);}public Optional<Product>findById(long id){return id==value.getId()?Optional.of(value):Optional.empty();}}
    private record FixedCustomers(Customer value)implements CustomerRepository{public Customer save(Customer v){return v;}public List<Customer>findAll(){return List.of(value);}public Optional<Customer>findById(long id){return id==value.getId()?Optional.of(value):Optional.empty();}}
    private record ListProducts(List<Product> values)implements ProductRepository{public Product save(Product v){return v;}public List<Product>findAll(){return values;}public Optional<Product>findById(long id){return values.stream().filter(v->v.getId()==id).findFirst();}}
    private static final class MemoryOrders extends EmptyOrders{
        private final List<Product> products;private CustomerOrder order;private List<OrderItem> items=List.of();MemoryOrders(List<Product> products){this.products=products;}
        @Override public CustomerOrder create(CustomerOrder value,List<OrderItem> newItems){order=new CustomerOrder(1,value.getCustomerId(),value.getStatus(),value.getCreatedAt(),null,null,null,null,value.getNote());items=List.copyOf(newItems);return order;}
        @Override public List<CustomerOrderDetails> findAll(){return order==null?List.of():List.of(details());}
        @Override public Optional<CustomerOrderDetails> findById(long id){return order!=null&&id==1?Optional.of(details()):Optional.empty();}
        private CustomerOrderDetails details(){return new CustomerOrderDetails(order,"Покупатель",items.stream().map(item->{Product product=products.stream().filter(v->v.getId()==item.getProductId()).findFirst().orElseThrow();return new OrderItemDetails(item,product.getSku(),product.getName());}).toList());}
    }
    private static class EmptyOrders implements CustomerOrderRepository{public CustomerOrder create(CustomerOrder o,List<OrderItem>i){return o;}public List<CustomerOrderDetails>findAll(){return List.of();}public Optional<CustomerOrderDetails>findById(long id){return Optional.empty();}public CustomerOrder updateNew(CustomerOrder o,List<OrderItem>i){return o;}public boolean deleteById(long id){return false;}public List<CustomerOrderDetails>findByProductName(String q){return List.of();}public List<CustomerOrderDetails>findByCustomerName(String q){return List.of();}public List<CustomerOrderDetails>findByStatus(OrderStatus s){return List.of();}public List<CustomerOrderDetails>findByCreatedAt(java.time.LocalDate f,java.time.LocalDate t){return List.of();}public CustomerOrder transition(long id,OrderStatus e,OrderStatus t,java.time.LocalDateTime a){throw new UnsupportedOperationException();}public CustomerOrder pay(long id,java.time.LocalDateTime a){throw new UnsupportedOperationException();}public CustomerOrder cancelPaid(long id,java.time.LocalDateTime a){throw new UnsupportedOperationException();}}
}
