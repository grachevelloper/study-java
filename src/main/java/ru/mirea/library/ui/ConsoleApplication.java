package ru.mirea.library.ui;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import ru.mirea.library.exception.BusinessRuleException;
import ru.mirea.library.exception.DataAccessException;
import ru.mirea.library.exception.EntityNotFoundException;
import ru.mirea.library.exception.ExportException;
import ru.mirea.library.export.DataExporter;
import ru.mirea.library.export.LibraryExportData;
import ru.mirea.library.model.BookIssueRequestDetails;
import ru.mirea.library.model.RequestSortField;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.model.SortDirection;
import ru.mirea.library.service.BookIssueRequestService;
import ru.mirea.library.service.BookService;
import ru.mirea.library.service.ReaderService;
import ru.mirea.library.service.StatisticsService;

public final class ConsoleApplication {
    private final ReaderService readers;
    private final BookService books;
    private final BookIssueRequestService requests;
    private final StatisticsService statistics;
    private final DataExporter exporter;
    private final ConsoleInput input;
    private final ConsolePrinter printer;

    public ConsoleApplication(ReaderService readers, BookService books,
                              BookIssueRequestService requests, StatisticsService statistics,
                              DataExporter exporter, ConsoleInput input, ConsolePrinter printer) {
        this.readers = Objects.requireNonNull(readers);
        this.books = Objects.requireNonNull(books);
        this.requests = Objects.requireNonNull(requests);
        this.statistics = Objects.requireNonNull(statistics);
        this.exporter = Objects.requireNonNull(exporter);
        this.input = Objects.requireNonNull(input);
        this.printer = Objects.requireNonNull(printer);
    }

    public void run() {
        while (true) {
            printer.println("""
                ================ БИБЛИОТЕЧНАЯ ИНФОРМАЦИОННАЯ СИСТЕМА ================
                1. Читатели
                2. Книги
                3. Заявки на выдачу
                4. Поиск заявок
                5. Фильтрация заявок
                6. Сортировка заявок
                7. Статистика
                8. Экспорт в Excel
                9. Вывести таблицы базы данных
                0. Выход
                """);
            Integer choice = input.readIntInRange("Выберите действие: ", 0, 9);
            if (choice == null || choice == 0) return;
            switch (choice) {
                case 1 -> readerMenu();
                case 2 -> bookMenu();
                case 3 -> requestMenu();
                case 4 -> searchMenu();
                case 5 -> filterMenu();
                case 6 -> sortMenu();
                case 7 -> safely(() -> printer.printStatistics(statistics.getStatistics()));
                case 8 -> safely(this::exportData);
                case 9 -> safely(this::printAllTables);
                default -> throw new IllegalStateException("Unexpected menu choice");
            }
        }
    }

    private void readerMenu() {
        while (true) {
            printer.println("\n1. Добавить  2. Вывести все  3. Найти по ID  0. Назад");
            Integer choice = input.readIntInRange("Выбор: ", 0, 3);
            if (choice == null || choice == 0) return;
            switch (choice) {
                case 1 -> safely(this::createReader);
                case 2 -> safely(() -> printer.printReaders(readers.getAll()));
                case 3 -> safely(this::findReader);
                default -> throw new IllegalStateException("Unexpected reader choice");
            }
        }
    }

    private void bookMenu() {
        while (true) {
            printer.println("\n1. Добавить  2. Вывести все  3. Найти по ID  0. Назад");
            Integer choice = input.readIntInRange("Выбор: ", 0, 3);
            if (choice == null || choice == 0) return;
            switch (choice) {
                case 1 -> safely(this::createBook);
                case 2 -> safely(() -> printer.printBooks(books.getAll()));
                case 3 -> safely(this::findBook);
                default -> throw new IllegalStateException("Unexpected book choice");
            }
        }
    }

    private void requestMenu() {
        while (true) {
            printer.println("""

                1. Создать  2. Вывести все  3. Найти по ID
                4. Изменить  5. Удалить  6. Изменить статус  0. Назад
                """);
            Integer choice = input.readIntInRange("Выбор: ", 0, 6);
            if (choice == null || choice == 0) return;
            switch (choice) {
                case 1 -> safely(this::createRequest);
                case 2 -> safely(() -> printer.printRequests(requests.getAll()));
                case 3 -> safely(this::findRequest);
                case 4 -> safely(this::updateRequest);
                case 5 -> safely(this::deleteRequest);
                case 6 -> safely(this::changeRequestStatus);
                default -> throw new IllegalStateException("Unexpected request choice");
            }
        }
    }

    private void searchMenu() {
        while (true) {
            printer.println("1. По названию книги  2. По ФИО читателя  0. Назад");
            Integer choice = input.readIntInRange("Выбор: ", 0, 2);
            if (choice == null || choice == 0) return;
            String query = input.readRequired("Поиск: ");
            if (query == null) return;
            safely(() -> printer.printRequests(choice == 1
                ? requests.searchByBookTitle(query) : requests.searchByReaderName(query)));
        }
    }

    private void filterMenu() {
        while (true) {
            printer.println("1. По статусу  2. По диапазону дат  0. Назад");
            Integer choice = input.readIntInRange("Выбор: ", 0, 2);
            if (choice == null || choice == 0) return;
            if (choice == 1) {
                RequestStatus status = readStatus();
                if (status == null) return;
                safely(() -> printer.printRequests(requests.filterByStatus(status)));
            } else {
                LocalDate from = input.readDate("Дата с (ГГГГ-ММ-ДД): ");
                LocalDate to = input.readDate("Дата по (ГГГГ-ММ-ДД): ");
                if (from == null || to == null) return;
                safely(() -> printer.printRequests(requests.filterByRequestedAt(from, to)));
            }
        }
    }

    private void sortMenu() {
        while (true) {
            printer.println("1. По дате заявки  2. По сроку возврата  0. Назад");
            Integer fieldChoice = input.readIntInRange("Выбор: ", 0, 2);
            if (fieldChoice == null || fieldChoice == 0) return;
            Integer directionChoice = input.readIntInRange("1. По возрастанию  2. По убыванию: ", 1, 2);
            if (directionChoice == null) return;
            safely(() -> printer.printRequests(requests.sort(requests.getAll(),
                fieldChoice == 1 ? RequestSortField.REQUESTED_AT : RequestSortField.DUE_ON,
                directionChoice == 1 ? SortDirection.ASCENDING : SortDirection.DESCENDING)));
        }
    }

    private void createReader() {
        String card = input.readRequired("Номер билета: ");
        String name = input.readRequired("ФИО: ");
        String email = input.readRequired("Email: ");
        if (card != null && name != null && email != null) printer.success("Читатель создан, ID " + readers.create(card, name, email).getId());
    }

    private void findReader() {
        Long id = input.readLong("ID: ");
        if (id != null) printer.printReaders(List.of(readers.getById(id)));
    }

    private void createBook() {
        String isbn = input.readRequired("ISBN: ");
        String title = input.readRequired("Название: ");
        String author = input.readRequired("Автор: ");
        Integer year = input.readInt("Год: ");
        Integer copies = input.readInt("Количество: ");
        if (isbn != null && title != null && author != null && year != null && copies != null) {
            printer.success("Книга создана, ID " + books.create(isbn, title, author, year, copies).getId());
        }
    }

    private void findBook() {
        Long id = input.readLong("ID: ");
        if (id != null) printer.printBooks(List.of(books.getById(id)));
    }

    private void createRequest() {
        Long readerId = input.readLong("ID читателя: ");
        Long bookId = input.readLong("ID книги: ");
        String note = input.readOptional("Примечание: ");
        if (readerId != null && bookId != null && !input.isEndOfInput()) {
            printer.success("Заявка создана, ID " + requests.createRequest(readerId, bookId, note).getId());
        }
    }

    private void findRequest() {
        Long id = input.readLong("ID: ");
        if (id != null) printer.printRequests(List.of(requests.getById(id)));
    }

    private void updateRequest() {
        Long id = input.readLong("ID заявки: ");
        Long readerId = input.readLong("Новый ID читателя: ");
        Long bookId = input.readLong("Новый ID книги: ");
        String note = input.readOptional("Новое примечание: ");
        if (id != null && readerId != null && bookId != null && !input.isEndOfInput()) {
            requests.updateDraft(id, readerId, bookId, note);
            printer.success("Заявка изменена.");
        }
    }

    private void deleteRequest() {
        Long id = input.readLong("ID: ");
        if (id != null) { requests.delete(id); printer.success("Заявка удалена."); }
    }

    private void changeRequestStatus() {
        Long id = input.readLong("ID: ");
        if (id == null) return;
        RequestStatus status = readStatus();
        if (status != null) {
            requests.changeStatus(id, status);
            printer.success("Статус изменён.");
        }
    }

    private RequestStatus readStatus() {
        RequestStatus[] statuses = RequestStatus.values();
        for (int index = 0; index < statuses.length; index++) printer.println((index + 1) + ". " + statuses[index]);
        Integer choice = input.readIntInRange("Статус: ", 1, statuses.length);
        return choice == null ? null : statuses[choice - 1];
    }

    private void exportData() {
        Path path = exporter.export(new LibraryExportData(
            readers.getAll(), books.getAll(), requests.getAll(), statistics.getStatistics()), Path.of("exports"));
        printer.success("Файл создан: " + path.toAbsolutePath());
    }

    private void printAllTables() {
        printer.println("\nЧИТАТЕЛИ");
        printer.printReaders(readers.getAll());
        printer.println("\nКНИГИ");
        printer.printBooks(books.getAll());
        printer.println("\nЗАЯВКИ");
        printer.printRequests(requests.getAll());
    }

    private void safely(Runnable operation) {
        try {
            operation.run();
        } catch (EntityNotFoundException | BusinessRuleException exception) {
            printer.error(exception.getMessage());
        } catch (DataAccessException exception) {
            printer.error("Ошибка доступа к данным: " + exception.getMessage());
        } catch (ExportException exception) {
            printer.error("Ошибка экспорта: " + exception.getMessage());
        }
    }
}
