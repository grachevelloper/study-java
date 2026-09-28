package ru.mirea.store.ui;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import ru.mirea.store.exception.*;
import ru.mirea.store.export.*;
import ru.mirea.store.model.*;
import ru.mirea.store.service.*;
import ru.mirea.store.validation.InputValidator;

public final class ConsoleApplication {

  private final CustomerService customers;
  private final ProductService products;
  private final CustomerOrderService orders;
  private final StatisticsService statistics;
  private final DataExporter exporter;
  private final ConsoleInput input;
  private final ConsolePrinter printer;

  public ConsoleApplication(
    CustomerService customers,
    ProductService products,
    CustomerOrderService orders,
    StatisticsService statistics,
    DataExporter exporter,
    ConsoleInput input,
    ConsolePrinter printer
  ) {
    this.customers = customers;
    this.products = products;
    this.orders = orders;
    this.statistics = statistics;
    this.exporter = exporter;
    this.input = input;
    this.printer = printer;
  }

  public void run() {
    printer.println("Информационная система интернет-магазина");
    while (true) {
      printer.println(
        "\n1. Покупатели  2. Товары  3. Заказы  4. Поиск  5. Фильтры\n" +
        "6. Сортировка  7. Статистика  8. Excel  " +
        "9. Все таблицы  0. Выход"
      );
      Integer choice = input.readIntInRange("Выбор: ", 0, 9);
      if (choice == null || choice == 0) {
        printer.println("До свидания!");
        return;
      }
      switch (choice) {
        case 1 -> customerMenu();
        case 2 -> productMenu();
        case 3 -> orderMenu();
        case 4 -> searchMenu();
        case 5 -> filterMenu();
        case 6 -> sortMenu();
        case 7 -> safe(() ->
          printer.printStatistics(statistics.getStatistics())
        );
        case 8 -> safe(this::export);
        case 9 -> safe(this::all);
        default -> throw new IllegalStateException();
      }
    }
  }

  private void customerMenu() {
    while (true) {
      printer.println("1. Добавить  2. Вывести все  3. Найти по ID  0. Назад");
      Integer c = input.readIntInRange("Выбор: ", 0, 3);
      if (c == null || c == 0) return;
      if (c == 1) safe(this::createCustomer);
      else if (c == 2) safe(() -> printer.printCustomers(customers.getAll()));
      else safe(() -> {
        Long id = input.readLong("ID: ");
        if (id != null) printer.printCustomers(List.of(customers.getById(id)));
      });
    }
  }

  private void productMenu() {
    while (true) {
      printer.println("1. Добавить  2. Вывести все  3. Найти по ID  0. Назад");
      Integer c = input.readIntInRange("Выбор: ", 0, 3);
      if (c == null || c == 0) return;
      if (c == 1) safe(this::createProduct);
      else if (c == 2) safe(() -> printer.printProducts(products.getAll()));
      else safe(() -> {
        Long id = input.readLong("ID: ");
        if (id != null) printer.printProducts(List.of(products.getById(id)));
      });
    }
  }

  private void orderMenu() {
    while (true) {
      printer.println(
        "1. Создать  2. Вывести все  3. Найти по ID  " +
        "4. Изменить NEW  5. Удалить  6. Изменить статус  0. Назад"
      );
      Integer c = input.readIntInRange("Выбор: ", 0, 6);
      if (c == null || c == 0) return;
      switch (c) {
        case 1 -> safe(this::createOrder);
        case 2 -> safe(() -> printer.printOrders(orders.getAll()));
        case 3 -> safe(this::findOrder);
        case 4 -> safe(this::updateOrder);
        case 5 -> safe(this::deleteOrder);
        case 6 -> safe(this::status);
        default -> throw new IllegalStateException();
      }
    }
  }

  private void searchMenu() {
    while (true) {
      printer.println("1. По товару  2. По покупателю  0. Назад");
      Integer c = input.readIntInRange("Выбор: ", 0, 2);
      if (c == null || c == 0) return;
      String q = input.readRequiredText(
        "Поиск: ",
        "Строка поиска",
        InputValidator.MAX_SEARCH_LENGTH
      );
      if (q == null) return;
      safe(() ->
        printer.printOrders(
          c == 1
            ? orders.searchByProductName(q)
            : orders.searchByCustomerName(q)
        )
      );
    }
  }

  private void filterMenu() {
    while (true) {
      printer.println("1. По статусу  2. По дате создания  0. Назад");
      Integer c = input.readIntInRange("Выбор: ", 0, 2);
      if (c == null || c == 0) return;
      if (c == 1) {
        OrderStatus status = readStatus();
        if (status != null) safe(() ->
          printer.printOrders(orders.filterByStatus(status))
        );
      } else {
        LocalDate from = input.readDate("С: ");
        LocalDate to = input.readDate("По: ");
        if (from != null && to != null) safe(() ->
          printer.printOrders(orders.filterByCreatedAt(from, to))
        );
      }
    }
  }

  private void sortMenu() {
    while (true) {
      printer.println("1. По дате  2. По сумме  0. Назад");
      Integer field = input.readIntInRange("Выбор: ", 0, 2);
      if (field == null || field == 0) return;
      Integer direction = input.readIntInRange(
        "1. Возрастание  2. Убывание: ",
        1,
        2
      );
      if (direction == null) return;
      safe(() ->
        printer.printOrders(
          orders.sort(
            orders.getAll(),
            field == 1
              ? OrderSortField.CREATED_AT
              : OrderSortField.TOTAL_AMOUNT,
            direction == 1 ? SortDirection.ASCENDING : SortDirection.DESCENDING
          )
        )
      );
    }
  }

  private void createCustomer() {
    String name = input.readRequiredText(
      "ФИО: ",
      "ФИО",
      InputValidator.MAX_NAME_LENGTH
    );
    String email = input.readEmail("Email: ");
    String phone = input.readPhone("Телефон: ");
    if (name != null && email != null && !input.isEndOfInput()) printer.success(
      "Покупатель создан, ID " + customers.create(name, email, phone).getId()
    );
  }

  private void createProduct() {
    String sku = input.readSku("SKU: ");
    String name = input.readRequiredText(
      "Название: ",
      "Название",
      InputValidator.MAX_PRODUCT_NAME_LENGTH
    );
    BigDecimal price = input.readPositiveDecimal("Цена: ");
    Integer stock = input.readNonNegativeInt("Остаток: ");
    if (
      sku != null && name != null && price != null && stock != null
    ) printer.success(
      "Товар создан, ID " + products.create(sku, name, price, stock).getId()
    );
  }

  private void createOrder() {
    Long customer = input.readLong("ID покупателя: ");
    List<CustomerOrderService.OrderItemInput> items = readItems();
    String note = input.readOptionalText(
      "Примечание: ",
      "Примечание",
      InputValidator.MAX_NOTE_LENGTH
    );
    if (
      customer != null && items != null && !input.isEndOfInput()
    ) printer.success(
      "Заказ создан, ID " + orders.createOrder(customer, items, note).getId()
    );
  }

  private void updateOrder() {
    Long id = input.readLong("ID заказа: ");
    Long customer = input.readLong("ID покупателя: ");
    List<CustomerOrderService.OrderItemInput> items = readItems();
    String note = input.readOptionalText(
      "Примечание: ",
      "Примечание",
      InputValidator.MAX_NOTE_LENGTH
    );
    if (
      id != null && customer != null && items != null && !input.isEndOfInput()
    ) {
      orders.updateNew(id, customer, items, note);
      printer.success("Заказ изменён.");
    }
  }

  private List<CustomerOrderService.OrderItemInput> readItems() {
    Integer count = input.readIntInRange(
      "Количество позиций: ",
      1,
      InputValidator.MAX_ORDER_ITEMS
    );
    if (count == null) return null;
    List<CustomerOrderService.OrderItemInput> values = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      Long product = input.readLong("ID товара #" + i + ": ");
      Integer quantity = input.readPositiveInt("Количество #" + i + ": ");
      if (product == null || quantity == null) return null;
      values.add(new CustomerOrderService.OrderItemInput(product, quantity));
    }
    return values;
  }

  private void findOrder() {
    Long id = input.readLong("ID: ");
    if (id != null) printer.printOrders(List.of(orders.getById(id)));
  }

  private void deleteOrder() {
    Long id = input.readLong("ID: ");
    if (id != null) {
      orders.delete(id);
      printer.success("Заказ удалён.");
    }
  }

  private void status() {
    Long id = input.readLong("ID: ");
    if (id == null) return;
    OrderStatus status = readStatus();
    if (status != null) {
      orders.changeStatus(id, status);
      printer.success("Статус изменён.");
    }
  }

  private OrderStatus readStatus() {
    OrderStatus[] values = OrderStatus.values();
    for (int i = 0; i < values.length; i++) printer.println(
      i + 1 + ". " + values[i]
    );
    Integer c = input.readIntInRange("Статус: ", 1, values.length);
    return c == null ? null : values[c - 1];
  }

  private void export() {
    Path path = exporter.export(
      new StoreExportData(
        customers.getAll(),
        products.getAll(),
        orders.getAll(),
        statistics.getStatistics()
      ),
      Path.of("exports")
    );
    printer.success("Файл создан: " + path.toAbsolutePath());
  }

  private void all() {
    printer.println("\nПОКУПАТЕЛИ");
    printer.printCustomers(customers.getAll());
    printer.println("\nТОВАРЫ");
    printer.printProducts(products.getAll());
    printer.println("\nЗАКАЗЫ");
    printer.printOrders(orders.getAll());
  }

  private void safe(Runnable operation) {
    try {
      operation.run();
    } catch (EntityNotFoundException | BusinessRuleException e) {
      printer.error(e.getMessage());
    } catch (DataAccessException e) {
      printer.error("Ошибка доступа к данным: " + e.getMessage());
    } catch (ExportException e) {
      printer.error("Ошибка экспорта: " + e.getMessage());
    }
  }
}
