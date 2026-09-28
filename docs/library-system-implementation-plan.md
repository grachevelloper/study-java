# Library Information System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать консольную библиотечную информационную систему для полного жизненного цикла заявки на выдачу книги в соответствии с КР 1.

**Architecture:** Слои `Console UI -> Service -> Repository interface -> JDBC -> PostgreSQL`. Сервис владеет бизнес-правилами; JDBC-репозитории владеют SQL и транзакциями; интерфейсы репозиториев и экспортера дают требуемый полиморфизм.

**Tech Stack:** Java 21, Maven, PostgreSQL 18.4, Docker Compose, JDBC/pgJDBC 42.7.13, Apache POI 5.5.1, JUnit 5.14.3, Testcontainers 1.21.3.

**Spec:** `docs/library-system-spec.md`

## Global Constraints

- Предметная область и имена в коде: библиотека, читатель, книга, заявка на выдачу.
- Основная сущность: `BookIssueRequest`; для неё обязателен полный CRUD, поиск, фильтрация и сортировка.
- Java 21; Maven; PostgreSQL запускается через Docker Compose.
- Только JDBC: без Spring, ORM и in-memory БД в production-коде.
- Все SQL-параметры передаются через `PreparedStatement`; JDBC-ресурсы закрываются через `try-with-resources`.
- `Main` только собирает зависимости и запускает UI; SQL и бизнес-правила в UI запрещены.
- Срок возврата равен 30 календарным дням после выдачи.
- Не добавлять функции КР 2–4: JavaFX, REST API, Spring или Android.
- Не коммитить, не пушить и не менять согласованную спецификацию без явного запроса владельца проекта.

## Task Order and Agent Handoff

Задачи выполняются по порядку. Один агент реализует одну задачу, запускает её проверки и передаёт результат на review до начала следующей. Задачи 3 и 4 можно выполнять параллельно только после Task 2: у них разные production-файлы, но каждый агент не должен менять `pom.xml` или интерфейсы без согласования.

```text
Task 1 -> Task 2 -> Task 3 ----+
                  -> Task 4 ----+-> Task 5 -> Task 6 -> Task 7 -> Task 8 -> Task 9
```

## Target File Structure

```text
.
├── .env.example
├── .gitignore
├── compose.yaml
├── pom.xml
├── README.md
├── docker/db/init/01-schema.sql
├── docker/db/init/02-seed.sql
├── docs/er-diagram.puml
├── exports/.gitkeep
├── src/main/java/ru/mirea/library/
│   ├── Main.java
│   ├── config/{ConnectionProvider,DatabaseConfig,DatabaseManager}.java
│   ├── exception/{BusinessRuleException,DataAccessException,EntityNotFoundException,ExportException}.java
│   ├── export/{DataExporter,ExcelExporter,LibraryExportData}.java
│   ├── model/{Book,BookIssueRequest,BookIssueRequestDetails,Reader,RequestSortField,RequestStatus,SortDirection,SystemStatistics}.java
│   ├── repository/{BookIssueRequestRepository,BookRepository,ReaderRepository,StatisticsRepository}.java
│   ├── repository/jdbc/{JdbcBookIssueRequestRepository,JdbcBookRepository,JdbcReaderRepository,JdbcStatisticsRepository,RowMappers}.java
│   ├── service/{BookIssueRequestService,BookService,ReaderService,StatisticsService}.java
│   └── ui/{ConsoleApplication,ConsoleInput,ConsolePrinter}.java
└── src/test/java/ru/mirea/library/
    ├── export/ExcelExporterTest.java
    ├── integration/{JdbcBookIssueRequestRepositoryTest,JdbcCatalogRepositoryTest}.java
    ├── model/RequestStatusTest.java
    ├── service/{BookIssueRequestServiceTest,RequestSortingTest}.java
    └── ui/ConsoleInputTest.java
```

## Review Focus

- Два одновременных запроса на последний экземпляр: только один переводится в `ISSUED`, остаток не становится отрицательным — Task 5.
- Повторная активная заявка того же читателя на ту же книгу отклоняется и сервисом, и частичным уникальным индексом — Tasks 1, 4, 5.
- Пустые строки, `abc` вместо ID и неверная дата не завершают приложение — Tasks 4 и 8.
- Ошибка посередине выдачи или возврата откатывает и заявку, и остаток — Task 5.
- Excel-файл после экспорта открывается, содержит четыре листа и строки из PostgreSQL — Task 7.

---

### Task 1: Maven Project and Reproducible PostgreSQL

**Owner:** foundation/database agent
**Depends on:** none

**Files:**

- Create: `pom.xml`
- Create: `.gitignore`
- Create: `.env.example`
- Create: `compose.yaml`
- Create: `docker/db/init/01-schema.sql`
- Create: `docker/db/init/02-seed.sql`

**Interfaces:**

- Produces database `library_db` on `localhost:5432`.
- Produces tables `readers`, `books`, `book_issue_requests` and deterministic seed IDs.
- Produces Maven package root `ru.mirea.library` and Java release 21.

- [ ] **Step 1: Create Maven configuration**

Set these exact versions/properties in `pom.xml`:

```xml
<maven.compiler.release>21</maven.compiler.release>
<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
<postgresql.version>42.7.13</postgresql.version>
<poi.version>5.5.1</poi.version>
<junit.version>5.14.3</junit.version>
<testcontainers.version>1.21.3</testcontainers.version>
```

Dependencies: `org.postgresql:postgresql`, `org.apache.poi:poi-ooxml`, test-scoped `org.junit.jupiter:junit-jupiter`, `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`. Configure Compiler Plugin 3.16.0, Surefire 3.6.0 and Shade 3.6.2 with main class `ru.mirea.library.Main`; set build final name to `library-information-system`.

- [ ] **Step 2: Define the schema with database-level invariants**

The request status constraint and active-request index must be equivalent to:

```sql
status varchar(20) NOT NULL DEFAULT 'CREATED'
    CHECK (status IN ('CREATED','APPROVED','ISSUED','RETURNED','REJECTED','CANCELLED'))
```

```sql
CREATE UNIQUE INDEX uq_active_reader_book_request
ON book_issue_requests(reader_id, book_id)
WHERE status IN ('CREATED', 'APPROVED', 'ISSUED');
```

Add date consistency checks: issue/due dates are both null before issuance and both non-null for `ISSUED`/`RETURNED`; `due_on = issued_on + 30`; `returned_on >= issued_on` when present. Add `ON DELETE RESTRICT` foreign keys and copy-count checks.

- [ ] **Step 3: Add deterministic seed data**

Insert 5 readers, 8 books and 10 requests spanning at least `CREATED`, `APPROVED`, `ISSUED`, `RETURNED`, `REJECTED`, and `CANCELLED`. Ensure `available_copies` agrees with seeded `ISSUED` requests and include one overdue `ISSUED` row.

- [ ] **Step 4: Configure Docker Compose**

Use image `postgres:18.4-alpine3.23`, named volume `library_postgres_data`, port `5432:5432`, `POSTGRES_DB=library_db`, `POSTGRES_USER=postgres`, password from `${POSTGRES_PASSWORD:-postgres}`, init mount `./docker/db/init:/docker-entrypoint-initdb.d:ro`, and healthcheck:

```yaml
test: ["CMD-SHELL", "pg_isready -U postgres -d library_db"]
interval: 5s
timeout: 3s
retries: 10
```

- [ ] **Step 5: Verify schema and seed data**

Run:

```bash
docker compose config
docker compose up -d --wait
docker compose exec -T db psql -U postgres -d library_db -c "SELECT COUNT(*) FROM readers;"
docker compose exec -T db psql -U postgres -d library_db -c "SELECT COUNT(*) FROM books;"
docker compose exec -T db psql -U postgres -d library_db -c "SELECT COUNT(*) FROM book_issue_requests;"
```

Expected counts: `5`, `8`, `10`. Verify an invalid status and negative copy count are rejected inside a transaction that is rolled back.

---

### Task 2: Domain Model, Exceptions, and Contracts

**Owner:** domain agent
**Depends on:** Task 1

**Files:**

- Create: all files in `model/`, `exception/`, and `repository/` listed in Target File Structure
- Create: `src/main/java/ru/mirea/library/config/ConnectionProvider.java`
- Create: `src/test/java/ru/mirea/library/model/RequestStatusTest.java`

**Interfaces:**

- Produces immutable domain classes with private fields, constructors and getters.
- Produces repository contracts consumed by Tasks 3–6.

- [ ] **Step 1: Write the status-transition test**

```java
@ParameterizedTest
@MethodSource("allowedTransitions")
void allowsOnlyDeclaredTransitions(RequestStatus from, RequestStatus to) {
    assertTrue(from.canTransitionTo(to));
}

@Test
void terminalStatusesHaveNoOutgoingTransitions() {
    assertAll(
        () -> assertTrue(RequestStatus.RETURNED.allowedTargets().isEmpty()),
        () -> assertTrue(RequestStatus.REJECTED.allowedTargets().isEmpty()),
        () -> assertTrue(RequestStatus.CANCELLED.allowedTargets().isEmpty())
    );
}
```

Run `mvn -Dtest=RequestStatusTest test`; expect compilation failure because `RequestStatus` does not exist.

- [ ] **Step 2: Implement `RequestStatus`**

```java
public enum RequestStatus {
    CREATED, APPROVED, ISSUED, RETURNED, REJECTED, CANCELLED;

    public Set<RequestStatus> allowedTargets() {
        return switch (this) {
            case CREATED -> Collections.unmodifiableSet(
                EnumSet.of(APPROVED, REJECTED, CANCELLED));
            case APPROVED -> Collections.unmodifiableSet(
                EnumSet.of(ISSUED, CANCELLED));
            case ISSUED -> Collections.unmodifiableSet(EnumSet.of(RETURNED));
            case RETURNED, REJECTED, CANCELLED -> Set.of();
        };
    }
    public boolean canTransitionTo(RequestStatus target) { return allowedTargets().contains(target); }
}
```

Use `EnumSet` and return an unmodifiable set.

- [ ] **Step 3: Implement domain classes**

Create conventional classes, not persistence-aware Active Record objects. Required constructors/getters:

```java
Reader(long id, String libraryCardNumber, String fullName, String email,
       boolean active, LocalDate registeredAt)

Book(long id, String isbn, String title, String author, int publicationYear,
     int totalCopies, int availableCopies)

BookIssueRequest(long id, long readerId, long bookId, RequestStatus status,
                 LocalDateTime requestedAt, LocalDate issuedOn, LocalDate dueOn,
                 LocalDate returnedOn, String note)
```

`BookIssueRequestDetails` composes a request with `readerName`, `bookTitle`, and `bookAuthor`. `RequestSortField` has `REQUESTED_AT` and `DUE_ON`; `SortDirection` has `ASCENDING` and `DESCENDING`. `SystemStatistics` contains `readerCount`, `bookTitleCount`, `totalCopyCount`, `availableCopyCount`, `requestCount`, `Map<RequestStatus, Long> requestsByStatus`, and `overdueRequestCount`. Implement value-based `equals`/`hashCode` for the three domain entities so repository round-trip tests compare values rather than object identity.

- [ ] **Step 4: Define exact repository contracts**

```java
public interface ReaderRepository {
    Reader save(Reader reader);
    List<Reader> findAll();
    Optional<Reader> findById(long id);
}

public interface BookRepository {
    Book save(Book book);
    List<Book> findAll();
    Optional<Book> findById(long id);
}

public interface BookIssueRequestRepository {
    BookIssueRequest create(BookIssueRequest request);
    List<BookIssueRequestDetails> findAll();
    Optional<BookIssueRequestDetails> findById(long id);
    BookIssueRequest updateDraft(BookIssueRequest request);
    boolean deleteById(long id);
    List<BookIssueRequestDetails> findByBookTitle(String query);
    List<BookIssueRequestDetails> findByReaderName(String query);
    List<BookIssueRequestDetails> findByStatus(RequestStatus status);
    List<BookIssueRequestDetails> findByRequestedAt(LocalDate from, LocalDate to);
    boolean existsActive(long readerId, long bookId, Long excludedRequestId);
    BookIssueRequest transition(long id, RequestStatus expected, RequestStatus target);
    BookIssueRequest issue(long id, LocalDate issuedOn, LocalDate dueOn);
    BookIssueRequest returnBook(long id, LocalDate returnedOn);
}

public interface StatisticsRepository {
    SystemStatistics load(LocalDate today);
}

@FunctionalInterface
public interface ConnectionProvider {
    Connection getConnection() throws SQLException;
}
```

- [ ] **Step 5: Add custom exceptions and run the model tests**

All custom exceptions extend `RuntimeException`, retain the cause where applicable, and expose message/cause constructors. Run `mvn -Dtest=RequestStatusTest test`; expect PASS.

---

### Task 3: JDBC Infrastructure and Catalog Repositories

**Owner:** JDBC catalog agent
**Depends on:** Task 2

**Files:**

- Create: `config/DatabaseConfig.java`
- Create: `config/DatabaseManager.java`
- Create: `repository/jdbc/RowMappers.java`
- Create: `repository/jdbc/JdbcReaderRepository.java`
- Create: `repository/jdbc/JdbcBookRepository.java`
- Create: `integration/JdbcCatalogRepositoryTest.java`

**Interfaces:**

- Consumes: `ConnectionProvider`, `ReaderRepository`, `BookRepository`.
- Produces: JDBC implementations for reader and book services.

- [ ] **Step 1: Write Testcontainers integration tests**

Use a shared `PostgreSQLContainer<>("postgres:18.4-alpine3.23")`, apply `01-schema.sql`, and assert:

```java
assertEquals(savedReader, repository.findById(savedReader.getId()).orElseThrow());
assertEquals(6, repository.findAll().size());
assertThrows(DataAccessException.class, () -> repository.save(duplicateEmailReader));
```

Add equivalent tests for book insert/read and duplicate ISBN. Run the test and expect failure before implementations exist.

- [ ] **Step 2: Implement environment configuration**

`DatabaseConfig.fromEnvironment()` reads `DB_URL`, `DB_USER`, `DB_PASSWORD` with local defaults:

```text
jdbc:postgresql://localhost:5432/library_db
postgres
postgres
```

Reject blank values with a clear `IllegalArgumentException`. `DatabaseManager` implements `ConnectionProvider` using `DriverManager.getConnection(config.url(), config.user(), config.password())`.

- [ ] **Step 3: Implement shared row mapping**

`RowMappers` provides package-private static functions `toReader(ResultSet)`, `toBook(ResultSet)`, `toRequest(ResultSet)`, and `toRequestDetails(ResultSet)`. Convert status with `RequestStatus.valueOf(resultSet.getString("status"))` and dates via `getObject(column, LocalDate.class)` / `LocalDateTime.class`.

- [ ] **Step 4: Implement reader and book repositories**

Every method opens its own connection and statement with nested `try-with-resources`. Inserts use `RETURN_GENERATED_KEYS`. No SQL string includes user input. Translate `SQLException` to `DataAccessException` with a user-safe message and retained cause.

- [ ] **Step 5: Run catalog integration tests**

Run `mvn -Dtest=JdbcCatalogRepositoryTest test`; expect PASS.

---

### Task 4: Services for Readers, Books, and Draft Requests

**Owner:** service agent
**Depends on:** Task 2

**Files:**

- Create: `service/ReaderService.java`
- Create: `service/BookService.java`
- Create: `service/BookIssueRequestService.java`
- Create: `service/BookIssueRequestServiceTest.java`

**Interfaces:**

- Consumes repository interfaces only; must not depend on JDBC classes.
- Produces validated use cases for the UI.

- [ ] **Step 1: Write service tests with small in-test fakes**

Cover these exact cases using `Clock.fixed(Instant.parse("2026-09-27T09:00:00Z"), ZoneOffset.UTC)`:

```java
assertThrows(BusinessRuleException.class,
    () -> service.createRequest(inactiveReaderId, bookId, "note"));

assertThrows(EntityNotFoundException.class,
    () -> service.createRequest(9999L, bookId, "note"));

assertThrows(BusinessRuleException.class,
    () -> service.createRequest(readerId, bookId, "x".repeat(501)));

assertEquals(RequestStatus.CREATED,
    service.createRequest(readerId, bookId, "  note  ").getStatus());
```

Also test duplicate active request, draft-only editing, restricted deletion, blank names/titles and invalid copy counts. Run `mvn -Dtest=BookIssueRequestServiceTest test`; expect failure.

- [ ] **Step 2: Implement shared validation helpers inside services**

Normalize human-entered strings with `trim()`. Email and ISBN are stored as entered after trimming; do not invent complex format validation absent from the spec. IDs must be positive. Notes may be null/blank and are normalized to null; length after trimming is at most 500.

- [ ] **Step 3: Implement reader and book use cases**

Required public methods:

```java
Reader create(String cardNumber, String fullName, String email);
List<Reader> getAll();
Reader getById(long id);

Book create(String isbn, String title, String author, int publicationYear, int totalCopies);
List<Book> getAll();
Book getById(long id);
```

New readers are active and registered on `LocalDate.now(clock)`. New books start with `availableCopies == totalCopies`.

- [ ] **Step 4: Implement request draft CRUD**

Required public methods:

```java
BookIssueRequest createRequest(long readerId, long bookId, String note);
List<BookIssueRequestDetails> getAll();
BookIssueRequestDetails getById(long id);
BookIssueRequest updateDraft(long requestId, long readerId, long bookId, String note);
void delete(long requestId);
```

Service performs existence/active/duplicate/status checks before calling the repository. The repository still provides DB constraints as a second line of defence.

- [ ] **Step 5: Run unit tests**

Run `mvn -Dtest=BookIssueRequestServiceTest test`; expect PASS without Docker.

---

### Task 5: Request JDBC Repository and Atomic Lifecycle

**Owner:** JDBC workflow agent
**Depends on:** Tasks 3 and 4

**Files:**

- Create: `repository/jdbc/JdbcBookIssueRequestRepository.java`
- Create: `integration/JdbcBookIssueRequestRepositoryTest.java`
- Modify: `service/BookIssueRequestService.java`
- Modify: `service/BookIssueRequestServiceTest.java`

**Interfaces:**

- Consumes the Task 2 repository contract.
- Produces all request persistence and `changeStatus(long, RequestStatus)` use case.

- [ ] **Step 1: Write repository integration tests**

Cover create/read/update/delete, both searches, both filters, active duplicate detection and all lifecycle operations. The critical transaction assertions are:

```java
BookIssueRequest issued = repository.issue(requestId, issuedOn, issuedOn.plusDays(30));
assertEquals(RequestStatus.ISSUED, issued.getStatus());
assertEquals(before - 1, bookRepository.findById(bookId).orElseThrow().getAvailableCopies());

BookIssueRequest returned = repository.returnBook(requestId, issuedOn.plusDays(5));
assertEquals(RequestStatus.RETURNED, returned.getStatus());
assertEquals(before, bookRepository.findById(bookId).orElseThrow().getAvailableCopies());
```

Also assert that issuing when `available_copies = 0` changes neither table.

- [ ] **Step 2: Implement joined reads and parameterized queries**

Use one shared projection selecting request columns plus `reader_name`, `book_title`, `book_author`. Searches use `ILIKE ?` with `%` + query + `%`. Date filter uses the half-open SQL interval `requested_at >= from` and `requested_at < to.plusDays(1)` so both boundary dates are included.

- [ ] **Step 3: Implement draft CRUD with optimistic status predicates**

Update and delete SQL must include the allowed statuses in `WHERE`; inspect affected-row count and return/throw a business-safe failure instead of assuming success. This prevents a stale UI read from bypassing service checks.

- [ ] **Step 4: Implement atomic issue transaction**

Within one connection:

```java
connection.setAutoCommit(false);
```

1. Lock the request and its book row with `SELECT r.status, b.id, b.available_copies, b.total_copies FROM book_issue_requests r JOIN books b ON b.id = r.book_id WHERE r.id = ? FOR UPDATE OF r, b`.
2. Verify current status is `APPROVED` and `available_copies > 0`.
3. Decrement the book with `UPDATE books SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0` and require one affected row.
4. Update request to `ISSUED`, setting `issued_on` and `due_on`.
5. `commit()`; on every exception `rollback()`; restore auto-commit before closing.

- [ ] **Step 5: Implement atomic return transaction**

Lock the same rows in the same order as issue, require `ISSUED`, increment only when `available_copies < total_copies`, update request to `RETURNED`, set `returned_on`, and commit. Lock ordering prevents avoidable deadlocks.

- [ ] **Step 6: Implement lifecycle service method**

```java
public BookIssueRequest changeStatus(long requestId, RequestStatus target)
```

Load the request, validate `canTransitionTo`, then dispatch:

- target `ISSUED` -> `repository.issue(id, today, today.plusDays(30))`;
- target `RETURNED` -> `repository.returnBook(id, today)`;
- other allowed target -> `repository.transition(id, current, target)`.

- [ ] **Step 7: Test concurrency and rollback**

Use two executor tasks and a `CountDownLatch` to attempt issuance of two approved requests for a one-copy book. Assert exactly one succeeds, exactly one remains unissued, and stock is zero. For rollback verification, create a test-only PostgreSQL `BEFORE UPDATE` trigger that raises an exception when `NEW.status = 'ISSUED'`, call `issue`, then assert the book stock is unchanged and drop the trigger in `finally`.

Run `mvn -Dtest=JdbcBookIssueRequestRepositoryTest,BookIssueRequestServiceTest test`; expect PASS.

---

### Task 6: Search, Filters, Sorting, and Statistics

**Owner:** query/statistics agent
**Depends on:** Task 5

**Files:**

- Modify: `service/BookIssueRequestService.java`
- Create: `repository/jdbc/JdbcStatisticsRepository.java`
- Create: `service/StatisticsService.java`
- Create: `service/RequestSortingTest.java`
- Modify: `integration/JdbcBookIssueRequestRepositoryTest.java`

**Interfaces:**

- Produces UI-ready search/filter/sort/statistics methods.

- [ ] **Step 1: Write search, filtering and sorting tests**

Test case-insensitive partial title and reader searches, inclusive date boundaries, status filter, and both sort directions. For nullable due dates assert `null` is always last:

```java
List<BookIssueRequestDetails> sorted = service.sort(
    source, RequestSortField.DUE_ON, SortDirection.ASCENDING);
assertNull(sorted.get(sorted.size() - 1).getRequest().getDueOn());
```

- [ ] **Step 2: Implement service query API**

```java
List<BookIssueRequestDetails> searchByBookTitle(String query);
List<BookIssueRequestDetails> searchByReaderName(String query);
List<BookIssueRequestDetails> filterByStatus(RequestStatus status);
List<BookIssueRequestDetails> filterByRequestedAt(LocalDate from, LocalDate to);
List<BookIssueRequestDetails> sort(List<BookIssueRequestDetails> source,
                                   RequestSortField field, SortDirection direction);
```

Reject blank search terms and `from > to`. Sorting returns a new list and uses Stream API plus `Comparator`; it never mutates repository results.

- [ ] **Step 3: Implement statistics in SQL**

`JdbcStatisticsRepository.load(today)` must return all seven indicators from the spec. Use aggregate queries and an `EnumMap<RequestStatus, Long>` initialized with zero for missing statuses. Overdue means `status = 'ISSUED' AND due_on < ?`.

- [ ] **Step 4: Verify query behavior**

Run `mvn -Dtest=RequestSortingTest,JdbcBookIssueRequestRepositoryTest test`; expect PASS.

---

### Task 7: Excel Export

**Owner:** export agent
**Depends on:** Task 6

**Files:**

- Create: `export/DataExporter.java`
- Create: `export/ExcelExporter.java`
- Create: `export/LibraryExportData.java`
- Create: `export/ExcelExporterTest.java`
- Create: `exports/.gitkeep`

**Interfaces:**

- Consumes complete snapshots of readers, books, requests and statistics.
- Produces a valid `.xlsx` path.

- [ ] **Step 1: Define exporter polymorphic contract and test**

```java
public interface DataExporter {
    Path export(LibraryExportData data, Path targetDirectory);
}
```

`LibraryExportData` contains `List<Reader> readers`, `List<Book> books`, `List<BookIssueRequestDetails> requests`, and `SystemStatistics statistics`. The test writes to `@TempDir`, opens the result with `WorkbookFactory.create(file)`, and asserts sheet names `Readers`, `Books`, `Requests`, `Statistics`, header rows, data row counts and date cell types.

- [ ] **Step 2: Implement `ExcelExporter` with XSSF**

Use `try (Workbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(path))`. Create one reusable header style and one date style. Freeze the first row, add auto-filter, and cap computed column widths to avoid pathological values.

Filename format:

```text
library-export-yyyyMMdd-HHmmss.xlsx
```

Create the target directory with `Files.createDirectories`. Translate I/O failures to `ExportException`.

- [ ] **Step 3: Run exporter test**

Run `mvn -Dtest=ExcelExporterTest test`; expect PASS and no files outside the temporary test directory.

---

### Task 8: Resilient Console UI and Application Wiring

**Owner:** UI agent
**Depends on:** Task 7

**Files:**

- Create: `ui/ConsoleInput.java`
- Create: `ui/ConsolePrinter.java`
- Create: `ui/ConsoleApplication.java`
- Create: `Main.java`
- Create: `ui/ConsoleInputTest.java`

**Interfaces:**

- Consumes all services and `DataExporter` by constructor injection.
- Produces the interactive menus defined in the spec.

- [ ] **Step 1: Test safe input parsing**

With `ByteArrayInputStream`, assert `readLong` re-prompts after `abc`, `readIntInRange` rejects out-of-range choices, `readDate` re-prompts after invalid ISO date, and EOF exits cleanly rather than looping forever.

- [ ] **Step 2: Implement `ConsoleInput`**

Read full lines through one `BufferedReader`; never mix `Scanner.nextInt()` and line reads. Parsing methods own their retry loops and print exact Russian error messages such as `Ошибка: ID должен быть целым положительным числом.`

- [ ] **Step 3: Implement reusable output formatting**

`ConsolePrinter` prints readers, books, request details and statistics in aligned text tables. Empty collections print `Записи не найдены.`; null dates print `—`.

- [ ] **Step 4: Implement all menus as loops**

Implement main menu and submenus for readers, books, request CRUD/status, search, filters and sorting. Every submenu handles `0` with `return`; only main-menu `0` terminates the application.

At the boundary of each operation catch:

```java
EntityNotFoundException | BusinessRuleException
DataAccessException
ExportException
```

Print a user-safe message and continue the loop. Do not catch `Throwable` or silently ignore exceptions.

- [ ] **Step 5: Wire dependencies in `Main`**

Instantiate `DatabaseConfig`, `DatabaseManager`, JDBC repositories, services with `Clock.systemDefaultZone()`, `ExcelExporter`, then `ConsoleApplication`. Before opening the menu execute `SELECT 1`; on connection failure print a clear startup message and return with no stack-trace crash.

- [ ] **Step 6: Run UI tests and a scripted smoke session**

Run `mvn -Dtest=ConsoleInputTest test`. Then pipe a script that enters an invalid menu value, lists data and exits; assert the invalid input message appears and the process exits with code 0.

---

### Task 9: ER Diagram, Runbook, Full Verification, and Example Export

**Owner:** release/documentation agent
**Depends on:** Task 8

**Files:**

- Create: `docs/er-diagram.puml`
- Create: `README.md`
- Create after running application: `exports/library-export-example.xlsx`
- Modify only if verification finds a defect: files owned by Tasks 1–8

**Interfaces:**

- Produces every artifact required for defence.

- [ ] **Step 1: Create the ER source**

The PlantUML diagram must show all columns, PK/UK/FK markers, cardinalities `Reader 1 -> many Requests` and `Book 1 -> many Requests`, and the request status note. Compare it column-for-column with `01-schema.sql`.

- [ ] **Step 2: Write the launch instruction**

`README.md` must contain:

```bash
cp .env.example .env
docker compose up -d --wait
mvn clean package
java -jar target/library-information-system.jar
```

Document environment variables, database reset (`docker compose down -v` destroys local DB data), test command, export location, architecture, business rules and a defence checklist explaining encapsulation, interfaces/polymorphism, collections, exceptions, JDBC and `Statement` versus `PreparedStatement`.

- [ ] **Step 3: Run the complete automated verification**

```bash
docker compose up -d --wait
mvn clean test
mvn clean package
```

Expected: zero failures, shaded JAR exists, no compiler warnings caused by project code.

- [ ] **Step 4: Run the manual acceptance scenario**

Perform in order:

1. create a reader and book;
2. create a request;
3. search it by title and reader;
4. filter by `CREATED` and creation date;
5. sort by request date and due date;
6. transition `CREATED -> APPROVED -> ISSUED`;
7. verify stock decreased and due date is issue date + 30;
8. transition `ISSUED -> RETURNED` and verify stock restored;
9. try a forbidden transition and invalid ID;
10. print statistics and all database tables;
11. export Excel and open it.

- [ ] **Step 5: Save the example export and audit deliverables**

Copy the verified export to `exports/library-export-example.xlsx`. Confirm the project contains source code, `pom.xml`, SQL schema/seed, ER diagram source, Excel example, `README.md`, and working console application.

- [ ] **Step 6: Final clean-state check**

Run `git status --short` if the directory is a Git repository. Verify no passwords other than documented local-development defaults, IDE metadata, database volumes, `target/`, or accidental exports are tracked. Do not commit or push without explicit owner approval.

## Requirement Traceability

| ТЗ | Реализация | Проверка |
|---|---|---|
| Java 21, `pom.xml`, JDBC | Tasks 1–3 | `mvn clean package`, integration tests |
| Минимум две связанные сущности | `Reader`, `Book`, `BookIssueRequest` | schema FK tests |
| CRUD основной сущности | Tasks 4–5 | service and repository tests |
| enum | `RequestStatus` | `RequestStatusTest` |
| интерфейс и полиморфизм | repository interfaces, `DataExporter` | wiring in `Main` |
| коллекции и Stream API | request sorting, `EnumMap` statistics | `RequestSortingTest` |
| собственные исключения | Task 2 | service/UI tests |
| минимум 5 бизнес-правил | 10 rules in spec, Tasks 4–5 | unit/integration tests |
| два поиска | title and reader name | repository integration tests |
| два фильтра | status and date range | repository integration tests |
| два способа сортировки | requested date and due date | sorting tests |
| минимум 5 показателей | 7 indicators | statistics integration test |
| Excel `.xlsx` | Task 7 | workbook reopen test |
| начальные данные | Task 1 | SQL count checks |
| обработка некорректного ввода/SQL | Tasks 3, 4, 8 | automated and smoke tests |
| SQL вне UI, слоистая архитектура | all tasks | source review |
