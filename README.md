# Интернет-магазин

## КР №3 — сервер и веб-интерфейс

Из корня проекта скопируйте `.env.example` в `.env`. Задайте
`POSTGRES_PASSWORD`, `JWT_SECRET_BASE64` и `BOOTSTRAP_ADMIN_PASSWORD`.
Для JWT нужен случайный ключ из 32 байт в Base64. В macOS или Linux его
можно получить командой `openssl rand -base64 32`, а временный пароль —
командой `printf 'Adm1!%s\n' "$(openssl rand -hex 16)"`.
В Windows скопируйте `.env.example` в `.env`, задайте пароль PostgreSQL
и выполните `.\backend\init-env.ps1` для заполнения двух остальных параметров.

```bash
docker compose --env-file .env -f backend/docker-compose.yml up -d --build --wait
```

В Windows эту же команду запускает `.\backend\start-compose.ps1`.

- Веб-интерфейс: [localhost:8080](http://localhost:8080/).
- Состояние приложения: [localhost:8080/actuator/health](http://localhost:8080/actuator/health).
- Swagger открывается из меню администратора после смены временного пароля.
- Основные ресурсы API: `/api/orders`, `/api/customers`, `/api/analytics/summary`,
  `/api/admin/users` и `/api/admin/audit`.

При первом входе используйте `BOOTSTRAP_ADMIN_EMAIL` и `BOOTSTRAP_ADMIN_PASSWORD`.
В пустой базе Flyway создаёт схему и начальные данные предыдущей работы.
Для подключения **уже заполненной базы КР №1** сделайте резервную копию,
укажите её в параметрах `SPRING_DATASOURCE_*` и запустите сервер с
`FLYWAY_BASELINE_ON_MIGRATE=true`. Flyway отметит схему как версию 1
и добавит поля и таблицы КР №3 миграцией V2. В V2 сохранены прежние числовые
ключи, а API адресует заказы по UUID.

CSV использует UTF-8 и разделитель «запятая». Возьмите экспорт из списка
заказов как образец. В столбце `items` позиции записываются в формате
`SKU:количество` и разделяются символом `|`. В режиме обновления заказ
находится по `external_id`. Значения суммы, приоритета и архива в экспорте
справочные. Цены позиций берутся из каталога при добавлении товара в заказ.

Тесты сервера и остановка приложения:

```bash
mvn -f backend/pom.xml test
docker compose --env-file .env -f backend/docker-compose.yml down
```

Данные PostgreSQL остаются в томе Docker после остановки.

## КР №2 — настольное приложение


Настольное JavaFX-приложение на Java 21. Работает с покупателями,
товарами и заказами БД КР №1 напрямую через JDBC.

## Возможности

- таблицы и добавление покупателей и товаров;
- создание и изменение заказов;
- поиск, фильтрация и сортировка заказов;
- изменение статуса заказа;
- просмотр статистики;
- экспорт текущей выборки в Excel через FileChooser;
- FXML-формы, CSS и понятные сообщения об ошибках.

## Требования

- Java 21;
- Maven 3.9+;
- Docker.

## Запуск

При первом запуске создайте файл настроек:

```bash
cp .env.example .env
```

Запустите базу данных и приложение:

```bash
docker compose up -d --wait
mvn clean compile
mvn javafx:run
```

## Проверка

```bash
mvn clean verify
mvn "-Dstore.fx.tests=true" verify
```

## Остановка

```bash
docker compose down
```

Не удаляйте Docker volume, если нужно сохранить данные.

## Windows и IntelliJ IDEA

Если Maven отсутствует в PATH:

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

Скрипт может найти Java 21 и Maven внутри установленной IntelliJ IDEA.
В IDEA выберите JDK 21 и запустите Maven goal `javafx:run`.
Рабочий каталог — корень проекта, чтобы загрузился `.env`.

Параметры JDBC задаются через `.env` или окружение. Пароль в Java-коде
не хранится. `POSTGRES_PORT` должен совпадать с портом в `DB_URL` и
`TEST_DB_URL`. На этом компьютере в `.env` выбран `15432`, потому что
`5432` занят другой PostgreSQL; шаблон сохраняет стандартный `5432`.

Тесты используют отдельную БД `TEST_DB_*`, которую очищают перед
проверками. Никогда не указывайте в `TEST_DB_URL` рабочую БД магазина.
При отсутствии `TEST_DB_*` используется отдельный Testcontainers.
JavaFX-тест включается параметром `store.fx.tests=true` и требует
графического сеанса. Он проверяет формы, создание и редактирование
заказа; снимок окна сохраняется в `target/javafx-preview.png`.

## Учебный пример

ТЗ также требует один полный запуск `student-hub-suite-rebuilt`:
PostgreSQL/backend через Docker, проверка веб-интерфейса и health,
затем запуск его `javafx-client` отдельно. Магазин использует свою БД
и JDBC; REST учебного примера ему не нужен.

В найденном архиве `.env` лежит в `backend`, опубликованный порт БД
ошибочно направлен на `5433` вместо `5432`, а штатный скрипт проверяет
отсутствующий маркер Compose. Эти особенности нужно учесть при запуске
примера, сохранив его структуру и данные.
