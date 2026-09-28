INSERT INTO
  customers (
    id,
    full_name,
    email,
    phone,
    active,
    registered_at
  )
VALUES
  (
    1,
    'Анна Петрова',
    'anna@example.com',
    '+7-900-000-00-01',
    true,
    TIMESTAMP '2026-01-10 10:00'
  ),
  (
    2,
    'Борис Соколов',
    'boris@example.com',
    '+7-900-000-00-02',
    true,
    TIMESTAMP '2026-01-11 11:00'
  ),
  (
    3,
    'Вера Морозова',
    'vera@example.com',
    NULL,
    true,
    TIMESTAMP '2026-01-12 12:00'
  ),
  (
    4,
    'Глеб Орлов',
    'gleb@example.com',
    '+7-900-000-00-04',
    true,
    TIMESTAMP '2026-01-13 13:00'
  ),
  (
    5,
    'Дарья Волкова',
    'daria@example.com',
    NULL,
    false,
    TIMESTAMP '2026-01-14 14:00'
  );

INSERT INTO
  products (id, sku, name, price, stock_quantity, active)
VALUES
  (1, 'PHONE-01', 'Смартфон', 49990.00, 8, true),
  (2, 'CASE-01', 'Чехол', 1490.00, 28, true),
  (3, 'LAPTOP-01', 'Ноутбук', 89990.00, 3, true),
  (4, 'MOUSE-01', 'Мышь', 2490.00, 15, true),
  (5, 'KEYBOARD-01', 'Клавиатура', 5990.00, 10, true),
  (6, 'DISPLAY-01', 'Монитор', 24990.00, 6, true),
  (7, 'CABLE-01', 'Кабель USB-C', 990.00, 40, true),
  (8, 'OLD-01', 'Архивный товар', 1990.00, 0, false);

INSERT INTO
  customer_orders (
    id,
    customer_id,
    status,
    created_at,
    paid_at,
    shipped_at,
    delivered_at,
    cancelled_at,
    note
  )
VALUES
  (
    1,
    1,
    'NEW',
    TIMESTAMP '2026-09-20 10:00',
    NULL,
    NULL,
    NULL,
    NULL,
    'Новый заказ'
  ),
  (
    2,
    2,
    'PAID',
    TIMESTAMP '2026-09-19 10:00',
    TIMESTAMP '2026-09-19 11:00',
    NULL,
    NULL,
    NULL,
    NULL
  ),
  (
    3,
    3,
    'SHIPPED',
    TIMESTAMP '2026-09-18 10:00',
    TIMESTAMP '2026-09-18 11:00',
    TIMESTAMP '2026-09-18 18:00',
    NULL,
    NULL,
    NULL
  ),
  (
    4,
    4,
    'DELIVERED',
    TIMESTAMP '2026-09-15 10:00',
    TIMESTAMP '2026-09-15 11:00',
    TIMESTAMP '2026-09-16 09:00',
    TIMESTAMP '2026-09-17 15:00',
    NULL,
    NULL
  ),
  (
    5,
    1,
    'CANCELLED',
    TIMESTAMP '2026-09-14 10:00',
    NULL,
    NULL,
    NULL,
    TIMESTAMP '2026-09-14 12:00',
    'Отменён до оплаты'
  ),
  (
    6,
    2,
    'CANCELLED',
    TIMESTAMP '2026-09-13 10:00',
    TIMESTAMP '2026-09-13 11:00',
    NULL,
    NULL,
    TIMESTAMP '2026-09-13 12:00',
    'Отменён после оплаты'
  ),
  (
    7,
    3,
    'NEW',
    TIMESTAMP '2026-09-21 10:00',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
  ),
  (
    8,
    4,
    'DELIVERED',
    TIMESTAMP '2026-09-10 10:00',
    TIMESTAMP '2026-09-10 11:00',
    TIMESTAMP '2026-09-11 09:00',
    TIMESTAMP '2026-09-12 15:00',
    NULL,
    NULL
  );

INSERT INTO
  order_items (id, order_id, product_id, quantity, unit_price)
VALUES
  (1, 1, 1, 1, 49990.00),
  (2, 1, 2, 2, 1490.00),
  (3, 2, 3, 1, 89990.00),
  (4, 2, 4, 1, 2490.00),
  (5, 3, 5, 1, 5990.00),
  (6, 3, 7, 2, 990.00),
  (7, 4, 1, 1, 49990.00),
  (8, 4, 6, 1, 24990.00),
  (9, 5, 4, 1, 2490.00),
  (10, 6, 2, 1, 1490.00),
  (11, 7, 7, 3, 990.00),
  (12, 8, 4, 2, 2490.00),
  (13, 8, 5, 1, 5990.00);

SELECT
  setval (
    pg_get_serial_sequence ('customers', 'id'),
    (
      SELECT
        max(id)
      FROM
        customers
    )
  );

SELECT
  setval (
    pg_get_serial_sequence ('products', 'id'),
    (
      SELECT
        max(id)
      FROM
        products
    )
  );

SELECT
  setval (
    pg_get_serial_sequence ('customer_orders', 'id'),
    (
      SELECT
        max(id)
      FROM
        customer_orders
    )
  );

SELECT
  setval (
    pg_get_serial_sequence ('order_items', 'id'),
    (
      SELECT
        max(id)
      FROM
        order_items
    )
  );
