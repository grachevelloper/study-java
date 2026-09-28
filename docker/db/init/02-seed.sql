INSERT INTO readers (id, library_card_number, full_name, email, active, registered_at) VALUES
    (1, 'CARD-001', 'Анна Петрова', 'anna.petrova@example.com', true, DATE '2024-01-15'),
    (2, 'CARD-002', 'Борис Соколов', 'boris.sokolov@example.com', true, DATE '2024-02-20'),
    (3, 'CARD-003', 'Вера Морозова', 'vera.morozova@example.com', true, DATE '2024-03-10'),
    (4, 'CARD-004', 'Глеб Орлов', 'gleb.orlov@example.com', true, DATE '2024-04-05'),
    (5, 'CARD-005', 'Дарья Волкова', 'daria.volkova@example.com', false, DATE '2024-05-12');

INSERT INTO books (id, isbn, title, author, publication_year, total_copies, available_copies) VALUES
    (1, '978-5-17-118366-9', 'Мастер и Маргарита', 'Михаил Булгаков', 1967, 3, 2),
    (2, '978-5-389-07435-4', 'Преступление и наказание', 'Фёдор Достоевский', 1866, 2, 1),
    (3, '978-5-389-06256-6', 'Война и мир', 'Лев Толстой', 1869, 1, 0),
    (4, '978-5-17-090539-2', 'Евгений Онегин', 'Александр Пушкин', 1833, 4, 4),
    (5, '978-5-389-01686-6', 'Отцы и дети', 'Иван Тургенев', 1862, 2, 2),
    (6, '978-5-17-087885-6', 'Герой нашего времени', 'Михаил Лермонтов', 1840, 1, 1),
    (7, '978-5-389-08782-8', 'Обломов', 'Иван Гончаров', 1859, 3, 3),
    (8, '978-5-17-090172-1', 'Мы', 'Евгений Замятин', 1924, 2, 2);

INSERT INTO book_issue_requests
    (id, reader_id, book_id, status, requested_at, issued_on, due_on, returned_on, note)
VALUES
    (1, 1, 1, 'CREATED', CURRENT_TIMESTAMP - INTERVAL '2 days', NULL, NULL, NULL, 'Обычная заявка'),
    (2, 2, 2, 'APPROVED', CURRENT_TIMESTAMP - INTERVAL '3 days', NULL, NULL, NULL, NULL),
    (3, 3, 1, 'ISSUED', CURRENT_TIMESTAMP - INTERVAL '6 days', CURRENT_DATE - 5, CURRENT_DATE + 25, NULL, NULL),
    (4, 4, 2, 'ISSUED', CURRENT_TIMESTAMP - INTERVAL '46 days', CURRENT_DATE - 45, CURRENT_DATE - 15, NULL, 'Просрочена'),
    (5, 5, 3, 'ISSUED', CURRENT_TIMESTAMP - INTERVAL '50 days', CURRENT_DATE - 49, CURRENT_DATE - 19, NULL, 'Просрочена'),
    (6, 1, 4, 'REJECTED', CURRENT_TIMESTAMP - INTERVAL '7 days', NULL, NULL, NULL, 'Отклонена'),
    (7, 2, 5, 'CANCELLED', CURRENT_TIMESTAMP - INTERVAL '8 days', NULL, NULL, NULL, NULL),
    (8, 3, 6, 'RETURNED', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_DATE - 19, CURRENT_DATE + 11, CURRENT_DATE - 10, NULL),
    (9, 4, 7, 'CREATED', CURRENT_TIMESTAMP - INTERVAL '1 day', NULL, NULL, NULL, NULL),
    (10, 5, 8, 'APPROVED', CURRENT_TIMESTAMP - INTERVAL '4 days', NULL, NULL, NULL, NULL);

SELECT setval(pg_get_serial_sequence('readers', 'id'), (SELECT max(id) FROM readers));
SELECT setval(pg_get_serial_sequence('books', 'id'), (SELECT max(id) FROM books));
SELECT setval(pg_get_serial_sequence('book_issue_requests', 'id'), (SELECT max(id) FROM book_issue_requests));
