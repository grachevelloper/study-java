# Библиотечная информационная система

Консольное приложение на Java 21 для полного жизненного цикла заявок на выдачу книг. Данные хранятся в PostgreSQL, доступ выполняется через JDBC без Spring и ORM. Система поддерживает CRUD заявок, поиск, фильтрацию, сортировку, статистику и экспорт в `.xlsx`.

## Быстрый запуск

Требуются JDK 21+, Maven, Docker и Docker Compose.

```bash
cp .env.example .env
docker compose up -d --wait
mvn clean package
java -jar target/library-information-system.jar
```

Локальные значения по умолчанию:

- `DB_URL=jdbc:postgresql://localhost:5432/library_db`;
- `DB_USER=postgres`;
- `DB_PASSWORD=postgres`;
- `POSTGRES_DB=library_db`;
- `POSTGRES_USER=postgres`;
- `POSTGRES_PASSWORD=postgres`.

Java-приложение автоматически читает `DB_URL`, `DB_USER`, `DB_PASSWORD` из локального `.env`. Переменные окружения процесса имеют приоритет над файлом. Путь к другому env-файлу можно передать через `DB_ENV_FILE`. Docker Compose читает из того же файла параметры `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`. Локальный `.env` исключён из Git, а `.env.example` служит безопасным шаблоном.

## Команды разработки

```bash
mvn clean test
mvn clean package
docker compose logs -f db
docker compose down
```

Полный сброс локальной БД удаляет все её данные:

```bash
docker compose down -v
docker compose up -d --wait
```

Интеграционные тесты по умолчанию используют Testcontainers. Для окружения, в котором Maven не имеет доступа к Docker socket, можно явно запустить их на отдельной тестовой PostgreSQL (её схема `public` будет пересоздана):

```bash
docker compose exec db createdb -U postgres library_test_db
TEST_DB_URL=jdbc:postgresql://localhost:5432/library_test_db \
TEST_DB_USER=postgres \
TEST_DB_PASSWORD=postgres \
mvn clean test
```

## Архитектура

```text
Console UI -> Service -> Repository interface -> JDBC -> PostgreSQL
                              |
                              +-> DataExporter -> Apache POI
```

- `ui` читает ввод и печатает меню, но не содержит SQL;
- `service` проверяет данные и бизнес-правила;
- `repository` задаёт интерфейсы, а `repository.jdbc` содержит параметризованный SQL;
- `model` содержит неизменяемые предметные объекты и enum;
- `export` создаёт Excel-файлы в каталоге `exports`;
- `Main` только проверяет подключение и собирает зависимости.

## Основные бизнес-правила

- заявку создаёт только активный существующий читатель на существующую книгу;
- у читателя не бывает двух активных заявок на одну книгу;
- разрешены только переходы `CREATED -> APPROVED|REJECTED|CANCELLED`, `APPROVED -> ISSUED|CANCELLED`, `ISSUED -> RETURNED`;
- выдача возможна при наличии экземпляра и атомарно уменьшает остаток;
- срок возврата — 30 календарных дней после выдачи;
- возврат атомарно увеличивает остаток;
- редактируется только `CREATED`, удаляются только `CREATED`, `REJECTED`, `CANCELLED`;
- обязательные строки не могут быть пустыми, примечание ограничено 500 символами.

Эти правила проверяет сервисный слой; ключевые инварианты дополнительно защищены `CHECK`, внешними ключами и частичным уникальным индексом PostgreSQL.

## Экспорт

Пункт меню «Экспорт в Excel» создаёт файл `exports/library-export-yyyyMMdd-HHmmss.xlsx` с листами `Readers`, `Books`, `Requests`, `Statistics`. В репозитории также находится проверенный пример `exports/library-export-example.xlsx`.

## Что показать на защите

- **Инкапсуляция:** поля моделей приватные и доступны через getters; объекты создаются конструкторами.
- **Интерфейсы и полиморфизм:** сервисы зависят от `ReaderRepository`, `BookRepository`, `BookIssueRequestRepository`, `StatisticsRepository`; Excel реализует `DataExporter`.
- **Коллекции:** списки используются для выборок, `EnumMap` — для статистики статусов, Stream API и `Comparator` — для сортировки.
- **Исключения:** предметные ошибки, отсутствие сущности, JDBC и экспорт представлены собственными исключениями и обрабатываются на границе UI.
- **JDBC:** ресурсы закрываются через `try-with-resources`, составные операции используют транзакции и row locks.
- **`Statement` и `PreparedStatement`:** `Statement` подходит для заранее известных служебных SQL-скриптов; все значения пользователя передаются только параметрами `PreparedStatement`, что предотвращает SQL injection и корректно кодирует типы.
- **Целостность:** продемонстрировать запрет дубликата активной заявки, выдачу последнего экземпляра, возврат, запрещённый переход и rollback.
- **Артефакты:** `compose.yaml`, SQL schema/seed, [ER-диаграмма](docs/er-diagram.puml), тесты, запускаемый JAR и Excel-пример.
