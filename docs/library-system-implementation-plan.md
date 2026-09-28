# Online Store Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Переделать существующую библиотечную систему в консольную информационную систему интернет-магазина. Основная сущность — заказ покупателя (`CustomerOrder`), который содержит несколько позиций (`OrderItem`).

**Architecture:** Сохранить существующие слои `Console UI -> Service -> Repository interface -> JDBC -> PostgreSQL`. Сервис отвечает за проверки и переходы статусов; JDBC-репозитории — за SQL и транзакции; UI не содержит SQL и бизнес-правил.

**Tech Stack:** Сохранить Java 21, Maven, PostgreSQL 18.4, Docker Compose, JDBC/pgJDBC 42.7.13, Apache POI 5.5.1, JUnit 5.14.3 и Testcontainers 1.21.3.

**Scope:** Изменять существующие файлы и классы там, где это возможно. Не создавать второй проект рядом и не сохранять параллельно библиотечную предметную область.

## Global Constraints

- Предметная область: интернет-магазин; связанные сущности — покупатель, товар, заказ и позиция заказа.
- Основная сущность: `CustomerOrder`; полный CRUD, поиск, фильтрация и сортировка реализуются только для неё.
- Один заказ содержит одну или несколько позиций; один товар не может повторяться в одном заказе.
- Статусы: `NEW -> PAID -> SHIPPED -> DELIVERED`; переход в `CANCELLED` разрешён из `NEW` и `PAID`.
- Возвраты товаров, платёжные интеграции и службы доставки не реализуются.
- Остатки уменьшаются атомарно при `NEW -> PAID`. При `PAID -> CANCELLED` остатки атомарно восстанавливаются.
- Заказ в `NEW` можно изменять. Удалять можно только `NEW` и `CANCELLED`; завершённые заказы сохраняются в истории.
- Цена товара копируется в `OrderItem.unitPrice` при создании или редактировании заказа, чтобы изменение каталога не меняло сумму старого заказа.
- Сохраняются Java 21, Maven, PostgreSQL в Docker Compose и JDBC без Spring/ORM.
- Все SQL-параметры передаются через `PreparedStatement`; ресурсы закрываются через `try-with-resources`.
- Не добавлять JavaFX, REST API, Spring, авторизацию, корзину, промокоды и другие функции вне КР 1.
- Не коммитить и не пушить без отдельного запроса владельца проекта.

## Task Order and Agent Handoff

Задачи выполняются последовательно. После каждой задачи агент запускает указанные проверки и передаёт изменения на review. Не начинать следующую задачу при красных проверках текущей.

```text
Task 1 -> Task 2 -> Task 3 -> Task 4 -> Task 5 -> Task 6 -> Task 7 -> Task 8 -> Task 9
```

## Target File Structure

```text
.
├── .env.example
├── .gitignore
├── compose.yaml
├── pom.xml
├── README.md
├── docker/db/init/{01-schema.sql,02-seed.sql}
├── docs/{online-store-spec.md,online-store-er-diagram.puml}
├── exports/.gitkeep
├── src/main/java/ru/mirea/store/
│   ├── Main.java
│   ├── config/{ConnectionProvider,DatabaseConfig,DatabaseManager}.java
│   ├── exception/{BusinessRuleException,DataAccessException,EntityNotFoundException,ExportException}.java
│   ├── export/{DataExporter,ExcelExporter,StoreExportData}.java
│   ├── model/{Customer,Product,CustomerOrder,OrderItem,OrderItemDetails,CustomerOrderDetails,OrderStatus,OrderSortField,SortDirection,SystemStatistics}.java
│   ├── repository/{CustomerRepository,ProductRepository,CustomerOrderRepository,StatisticsRepository}.java
│   ├── repository/jdbc/{JdbcCustomerRepository,JdbcProductRepository,JdbcCustomerOrderRepository,JdbcStatisticsRepository,RowMappers}.java
│   ├── service/{CustomerService,ProductService,CustomerOrderService,StatisticsService}.java
│   └── ui/{ConsoleApplication,ConsoleInput,ConsolePrinter}.java
└── src/test/java/ru/mirea/store/
    ├── export/ExcelExporterTest.java
    ├── integration/{JdbcCustomerOrderRepositoryTest,JdbcCatalogRepositoryTest}.java
    ├── model/OrderStatusTest.java
    ├── service/{CustomerOrderServiceTest,OrderSortingTest}.java
    └── ui/{ConsoleApplicationTest,ConsoleInputTest}.java
```

После переноса удалить старые Java-пакеты `ru.mirea.library` и устаревшие файлы документации/экспорта, когда их замены готовы и проверены.

## Review Focus

- Заказ невозможно создать без покупателя и хотя бы одной корректной позиции.
- Две позиции одного товара объединяются или отклоняются; дубликаты в БД запрещены `UNIQUE (order_id, product_id)`.
- Два заказа на последний товар: оплатиться может только один, остаток не становится отрицательным.
- Ошибка во время оплаты или отмены откатывает и статус заказа, и все изменения остатков.
- `PAID -> CANCELLED` восстанавливает каждую позицию ровно один раз; после `SHIPPED` отмена запрещена.
- Изменение цены товара не меняет `unitPrice` и итог ранее созданного заказа.
- Пустой ввод, неверные ID, количество, цена и дата не завершают приложение.
- Excel открывается и содержит данные покупателей, товаров, заказов, позиций и статистики.

---

### Task 1: Rename Project and Replace PostgreSQL Schema

**Owner:** foundation/database agent
**Depends on:** none

**Files:**

- Modify: `pom.xml`, `.env.example`, `compose.yaml`
- Replace: `docker/db/init/01-schema.sql`, `docker/db/init/02-seed.sql`

- [ ] **Step 1: Rename the build artifact and package root**

Change Maven coordinates/name from the library system to the online store, set the shaded main class to `ru.mirea.store.Main`, and set the final JAR name to `online-store`. Keep all dependency and plugin versions unchanged unless compilation requires a correction.

- [ ] **Step 2: Replace the database schema**

Use database `online_store_db` and tables:

```text
customers
  id, full_name, email UNIQUE, phone, active, registered_at

products
  id, sku UNIQUE, name, price numeric(12,2), stock_quantity, active

customer_orders
  id, customer_id FK, status, created_at,
  paid_at, shipped_at, delivered_at, cancelled_at, note

order_items
  id, order_id FK, product_id FK, quantity, unit_price numeric(12,2)
```

Required constraints:

- `status IN ('NEW','PAID','SHIPPED','DELIVERED','CANCELLED')`;
- `price > 0`, `unit_price > 0`, `quantity > 0`, `stock_quantity >= 0`;
- `UNIQUE (order_id, product_id)`;
- customers/products use `ON DELETE RESTRICT` from referenced data;
- items use `ON DELETE CASCADE` from their order;
- status timestamps are consistent: each later lifecycle timestamp is present only when its status has been reached; cancelled orders have `cancelled_at` and no shipped/delivered timestamp.

- [ ] **Step 3: Replace seed data**

Insert 5 customers, 8 products, at least 8 orders, and their items. Include all five statuses, multi-item orders, one inactive product/customer, and prices/stock suitable for search, statistics and payment tests. Seeded stock must already reflect every `PAID` and `SHIPPED` order that reserved stock; `DELIVERED` orders also keep stock deducted.

- [ ] **Step 4: Update Docker and environment defaults**

Keep the existing PostgreSQL image and healthcheck. Rename the database, volume and JDBC defaults consistently in Compose and `.env.example`. Do not expose or track real credentials.

- [ ] **Step 5: Verify the schema**

```bash
docker compose down -v
docker compose up -d --wait
docker compose exec -T db psql -U postgres -d online_store_db -c "SELECT COUNT(*) FROM customers;"
docker compose exec -T db psql -U postgres -d online_store_db -c "SELECT COUNT(*) FROM products;"
docker compose exec -T db psql -U postgres -d online_store_db -c "SELECT COUNT(*) FROM customer_orders;"
docker compose exec -T db psql -U postgres -d online_store_db -c "SELECT COUNT(*) FROM order_items;"
```

Inside rolled-back transactions, verify rejection of an invalid status, negative stock, zero quantity and duplicate product in one order.

---

### Task 2: Replace Domain Model and Repository Contracts

**Owner:** domain agent
**Depends on:** Task 1

**Files:**

- Move/replace: existing `ru/mirea/library` model, exception and repository contracts under `ru/mirea/store`
- Replace: `RequestStatusTest` with `OrderStatusTest`

- [ ] **Step 1: Implement and test order transitions**

```java
public enum OrderStatus {
    NEW, PAID, SHIPPED, DELIVERED, CANCELLED;

    public Set<OrderStatus> allowedTargets() {
        return switch (this) {
            case NEW -> Set.of(PAID, CANCELLED);
            case PAID -> Set.of(SHIPPED, CANCELLED);
            case SHIPPED -> Set.of(DELIVERED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }
}
```

Test every allowed transition, representative forbidden transitions, and terminal statuses.

- [ ] **Step 2: Replace the entity classes**

Required domain data:

```java
Customer(id, fullName, email, phone, active, registeredAt)
Product(id, sku, name, price, stockQuantity, active)
CustomerOrder(id, customerId, status, createdAt,
              paidAt, shippedAt, deliveredAt, cancelledAt, note)
OrderItem(id, orderId, productId, quantity, unitPrice)
```

Use `BigDecimal` for money. `OrderItemDetails` adds the product name/SKU. `CustomerOrderDetails` contains the order, customer name, a list of item details and a calculated total. Return item lists as defensive unmodifiable copies. Implement value-based `equals`/`hashCode` for persisted entities.

`OrderSortField` contains `CREATED_AT` and `TOTAL_AMOUNT`; keep `SortDirection` with `ASCENDING`/`DESCENDING`.

- [ ] **Step 3: Replace repository contracts**

```java
interface CustomerRepository {
    Customer save(Customer customer);
    List<Customer> findAll();
    Optional<Customer> findById(long id);
}

interface ProductRepository {
    Product save(Product product);
    List<Product> findAll();
    Optional<Product> findById(long id);
}

interface CustomerOrderRepository {
    CustomerOrder create(CustomerOrder order, List<OrderItem> items);
    List<CustomerOrderDetails> findAll();
    Optional<CustomerOrderDetails> findById(long id);
    CustomerOrder updateNew(CustomerOrder order, List<OrderItem> items);
    boolean deleteById(long id);
    List<CustomerOrderDetails> findByProductName(String query);
    List<CustomerOrderDetails> findByCustomerName(String query);
    List<CustomerOrderDetails> findByStatus(OrderStatus status);
    List<CustomerOrderDetails> findByCreatedAt(LocalDate from, LocalDate to);
    CustomerOrder transition(long id, OrderStatus expected, OrderStatus target, LocalDateTime changedAt);
    CustomerOrder pay(long id, LocalDateTime paidAt);
    CustomerOrder cancelPaid(long id, LocalDateTime cancelledAt);
}
```

Keep `StatisticsRepository`, `ConnectionProvider` and custom exception roles, renaming only library-specific text.

- [ ] **Step 4: Run model tests**

```bash
mvn -Dtest=OrderStatusTest test
```

---

### Task 3: Adapt JDBC Configuration and Catalog Repositories

**Owner:** JDBC catalog agent
**Depends on:** Task 2

**Files:**

- Modify/move: `DatabaseConfig`, `DatabaseManager`, `RowMappers`
- Replace: reader/book repositories with customer/product repositories
- Rewrite: `JdbcCatalogRepositoryTest`

- [ ] **Step 1: Change configuration defaults**

Default URL becomes `jdbc:postgresql://localhost:5432/online_store_db`; preserve `.env`, `DB_ENV_FILE`, and environment-variable precedence already implemented by the project.

- [ ] **Step 2: Replace row mappings**

Provide mappings for customer, product, order, item and joined detail rows. Read/write money as `BigDecimal` without converting through `double`.

- [ ] **Step 3: Replace catalog repositories**

Adapt existing JDBC patterns rather than adding a second repository layer. Inserts use generated keys, all user values use parameters, and SQL errors retain their causes.

- [ ] **Step 4: Rewrite integration tests**

Cover customer/product insert and read, duplicate email, duplicate SKU, invalid price and invalid stock. Apply the new schema to an isolated Testcontainers database.

```bash
mvn -Dtest=JdbcCatalogRepositoryTest test
```

---

### Task 4: Replace Services and Implement NEW Order CRUD

**Owner:** service agent
**Depends on:** Task 3

**Files:**

- Replace: reader/book/request services with customer/product/order services
- Rewrite: `CustomerOrderServiceTest`

- [ ] **Step 1: Test service rules using in-test fakes**

Cover:

- customer must exist and be active;
- every product must exist and be active;
- an order contains at least one item;
- item quantity is positive and product IDs are unique;
- note is trimmed, optional and at most 500 characters;
- only `NEW` orders can be edited;
- only `NEW` and `CANCELLED` orders can be deleted;
- item price is copied from the current product price;
- changing a product price later does not alter the saved order item.

- [ ] **Step 2: Implement customer and product use cases**

Provide create/list/get methods only. Validate nonblank names, email, SKU, positive price and nonnegative initial stock. New records are active.

- [ ] **Step 3: Implement order CRUD**

```java
CustomerOrder createOrder(long customerId, List<OrderItemInput> items, String note);
List<CustomerOrderDetails> getAll();
CustomerOrderDetails getById(long id);
CustomerOrder updateNew(long orderId, long customerId,
                        List<OrderItemInput> items, String note);
void delete(long orderId);
```

`OrderItemInput` contains only `productId` and `quantity`; callers cannot set price or status directly. Creating an order does not reserve stock. Repository creation/update of an order and all its items must be one transaction.

- [ ] **Step 4: Run service tests**

```bash
mvn -Dtest=CustomerOrderServiceTest test
```

---

### Task 5: Implement Order JDBC Repository and Atomic Stock Lifecycle

**Owner:** JDBC workflow agent
**Depends on:** Task 4

**Files:**

- Replace: `JdbcBookIssueRequestRepository` with `JdbcCustomerOrderRepository`
- Rewrite: repository integration tests
- Complete: `CustomerOrderService.changeStatus`

- [ ] **Step 1: Implement joined reads without duplicate orders**

Load order headers and items deterministically (`ORDER BY order_id, item_id`) and group them into one `CustomerOrderDetails` per order. Searches must return distinct orders even when several items match.

- [ ] **Step 2: Implement transactional create/update/delete**

Create the order header and item rows in one transaction. For update, lock and require `NEW`, update the header, replace its items, and commit once. Delete SQL must include the allowed statuses so stale UI state cannot delete a progressed order.

- [ ] **Step 3: Implement atomic payment**

In one transaction:

1. Lock the order and require `NEW`.
2. Load its items; reject an order with no items.
3. Lock all referenced product rows in ascending product ID order.
4. Require every product to be active with `stock_quantity >= requested quantity`.
5. Decrement every stock using guarded updates.
6. Change order to `PAID`, set `paid_at`, then commit.
7. On any failure, roll back the complete operation.

- [ ] **Step 4: Implement atomic cancellation of a paid order**

Lock rows in the same order, require `PAID`, restore every product quantity, set `CANCELLED` and `cancelled_at`, then commit. `NEW -> CANCELLED` is a simple status transition because stock was not reserved.

- [ ] **Step 5: Implement remaining transitions**

`PAID -> SHIPPED` sets `shipped_at`; `SHIPPED -> DELIVERED` sets `delivered_at`. The service validates `allowedTargets()` and dispatches payment and paid cancellation to their transactional repository methods.

- [ ] **Step 6: Test rollback and concurrency**

Cover full CRUD, multi-item reads, all status transitions, insufficient stock, inactive product, rollback partway through payment/cancellation, and two simultaneous payments competing for the last unit. Exactly one competing payment succeeds and stock never becomes negative.

```bash
mvn -Dtest=JdbcCustomerOrderRepositoryTest,CustomerOrderServiceTest test
```

---

### Task 6: Replace Search, Filters, Sorting, and Statistics

**Owner:** query/statistics agent
**Depends on:** Task 5

**Files:**

- Modify: `CustomerOrderService`, `JdbcStatisticsRepository`, `StatisticsService`
- Replace: `RequestSortingTest` with `OrderSortingTest`

- [ ] **Step 1: Implement the query API**

```java
List<CustomerOrderDetails> searchByProductName(String query);
List<CustomerOrderDetails> searchByCustomerName(String query);
List<CustomerOrderDetails> filterByStatus(OrderStatus status);
List<CustomerOrderDetails> filterByCreatedAt(LocalDate from, LocalDate to);
List<CustomerOrderDetails> sort(List<CustomerOrderDetails> source,
                                OrderSortField field, SortDirection direction);
```

Search is case-insensitive and partial. Date boundaries are inclusive. Sorting produces a new list, uses Stream API/`Comparator`, and supports creation date and calculated total in both directions.

- [ ] **Step 2: Replace statistics**

Return at least these indicators:

1. customer count;
2. product count;
3. total units currently in stock;
4. total order count;
5. order count by every status using `EnumMap<OrderStatus, Long>`;
6. total revenue of `DELIVERED` orders;
7. count of products with zero stock.

- [ ] **Step 3: Verify queries and statistics**

Test both searches, both filters, both sort fields/directions, multi-item order de-duplication and every statistic against known rows.

```bash
mvn -Dtest=OrderSortingTest,JdbcCustomerOrderRepositoryTest test
```

---

### Task 7: Adapt Excel Export

**Owner:** export agent
**Depends on:** Task 6

**Files:**

- Rename: `LibraryExportData` to `StoreExportData`
- Modify: `DataExporter`, `ExcelExporter`, `ExcelExporterTest`

- [ ] **Step 1: Replace export data**

`StoreExportData` contains customers, products, order details and statistics. Export five sheets: `Customers`, `Products`, `Orders`, `Order Items`, `Statistics`.

- [ ] **Step 2: Adapt workbook content**

Orders contain customer, status, timestamps and total. Order Items contain order ID, product/SKU, quantity, unit price and line total. Money remains numeric Excel cells; timestamps use a readable date-time style. Keep header styling, frozen rows, filters and bounded auto-width.

Filename format:

```text
online-store-export-yyyyMMdd-HHmmss.xlsx
```

- [ ] **Step 3: Verify the workbook**

Open the produced file with Apache POI in the test and verify sheet names, row counts, numeric money cells and representative values.

```bash
mvn -Dtest=ExcelExporterTest test
```

---

### Task 8: Replace Console UI and Application Wiring

**Owner:** UI agent
**Depends on:** Task 7

**Files:**

- Modify/move: `ConsoleInput`, `ConsolePrinter`, `ConsoleApplication`, `Main`
- Rewrite: UI tests

- [ ] **Step 1: Replace menus and labels**

Main menu contains customers, products, orders, search, filters, sorting, statistics and Excel export. Remove every library-specific label. Order creation/editing must accept repeated `product ID + quantity` input until the user finishes, while rejecting an empty item list.

- [ ] **Step 2: Preserve resilient input handling**

Reuse current line-based parsing and retry loops. Add safe parsing for positive quantity and positive decimal price using `BigDecimal`. Invalid input prints a Russian message and returns control to the current menu instead of terminating the program.

- [ ] **Step 3: Replace output tables**

Print customers, products, order headers and nested item rows. Show each order total. Null lifecycle timestamps print `—`; empty results print `Записи не найдены.`

- [ ] **Step 4: Rewire `Main`**

Instantiate only store repositories/services and keep dependency injection through interfaces. Startup still performs a database connectivity check and prints a user-safe error without a stack trace.

- [ ] **Step 5: Run UI tests and smoke test**

```bash
mvn -Dtest=ConsoleInputTest,ConsoleApplicationTest test
```

Script at least: invalid menu input, list products, create a multi-item order, view it, and exit successfully.

---

### Task 9: Replace Documentation, Diagram, Example Export, and Run Full Verification

**Owner:** release/documentation agent
**Depends on:** Task 8

**Files:**

- Replace: `docs/library-system-spec.md` with `docs/online-store-spec.md`
- Replace: `docs/er-diagram.puml` with `docs/online-store-er-diagram.puml`
- Rewrite: `README.md`
- Replace: old example export with `exports/online-store-export-example.xlsx`
- Delete: obsolete library-specific files after replacements are verified

- [ ] **Step 1: Update the human-readable specification**

Preserve the current specification structure and mandatory КР 1 requirements, replacing only the domain model, statuses, business rules, menus, statistics and examples with the decisions fixed in this plan. Do not introduce returns or external integrations.

- [ ] **Step 2: Replace the ER diagram**

Show all four tables, PK/UK/FK markers and cardinalities:

```text
Customer 1 -> many CustomerOrder
CustomerOrder 1 -> many OrderItem
Product 1 -> many OrderItem
```

Compare every diagram column and constraint with `01-schema.sql`.

- [ ] **Step 3: Rewrite README and run commands**

Document setup, `.env`, reset warning, architecture, entity relationships, order lifecycle, stock transaction, tests, export and defence checklist. Commands:

```bash
cp .env.example .env
docker compose up -d --wait
mvn clean test
mvn clean package
java -jar target/online-store.jar
```

- [ ] **Step 4: Run full automated verification**

```bash
docker compose down -v
docker compose up -d --wait
mvn clean test
mvn clean package
```

Expected: all tests pass, the shaded JAR exists, and no old `ru.mirea.library` Java package remains.

- [ ] **Step 5: Run the acceptance scenario**

1. Create a customer and two products.
2. Create one order containing both products.
3. Edit the `NEW` order and verify copied item prices and total.
4. Search by product and customer; filter by `NEW` and date; sort by date and total.
5. Pay the order and verify both stocks decrease.
6. Cancel it and verify both stocks restore exactly once.
7. Create another order and move it through `NEW -> PAID -> SHIPPED -> DELIVERED`.
8. Verify cancellation after shipment is rejected.
9. Verify invalid ID, quantity and insufficient-stock errors do not terminate the UI.
10. Print statistics, export Excel and open the file.

- [ ] **Step 6: Audit deliverables**

Save the checked example as `exports/online-store-export-example.xlsx`. Confirm that code, SQL, documentation, diagram, tests and example export all use only the online-store domain. Run `git diff --check` and `git status --short`. Do not commit or push without explicit approval.

## Requirement Traceability

| ТЗ | Реализация | Проверка |
|---|---|---|
| Java 21, Maven, JDBC, PostgreSQL | Tasks 1–3 | package and integration tests |
| Связанные сущности | `Customer`, `CustomerOrder`, `OrderItem`, `Product` | schema FK tests |
| CRUD основной сущности | Tasks 4–5 | order service/repository tests |
| enum | `OrderStatus` | `OrderStatusTest` |
| интерфейсы и полиморфизм | repositories, `DataExporter` | wiring review |
| коллекции и Stream API | item lists, sorting, `EnumMap` | unit tests |
| собственные исключения | retained and renamed messages | service/UI tests |
| минимум 5 бизнес-правил | Global Constraints and Tasks 4–5 | unit/integration tests |
| два поиска | product and customer | repository tests |
| два фильтра | status and creation date | repository tests |
| два способа сортировки | creation date and total | `OrderSortingTest` |
| минимум 5 показателей | seven store indicators | statistics tests |
| Excel `.xlsx` | Task 7 | workbook reopen test |
| начальные данные | Task 1 | SQL count/constraint checks |
| обработка неверного ввода и SQL | Tasks 3, 4, 8 | automated and smoke tests |
| слоистая архитектура, SQL вне UI | all tasks | source review |
