package ru.mirea.library.ui;

import java.io.PrintStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import ru.mirea.library.model.Book;
import ru.mirea.library.model.BookIssueRequest;
import ru.mirea.library.model.BookIssueRequestDetails;
import ru.mirea.library.model.Reader;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.model.SystemStatistics;

public final class ConsolePrinter {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final PrintStream output;

    public ConsolePrinter(PrintStream output) {
        this.output = Objects.requireNonNull(output);
    }

    public void println(String text) { output.println(text); }
    public void success(String text) { output.println("Успех: " + text); }
    public void error(String text) { output.println("Ошибка: " + text); }

    public void printReaders(List<Reader> readers) {
        if (readers.isEmpty()) { empty(); return; }
        output.printf("%-5s %-15s %-30s %-30s %-8s %-12s%n",
            "ID", "Билет", "ФИО", "Email", "Активен", "Регистрация");
        for (Reader reader : readers) {
            output.printf("%-5d %-15s %-30s %-30s %-8s %-12s%n", reader.getId(),
                crop(reader.getLibraryCardNumber(), 15), crop(reader.getFullName(), 30),
                crop(reader.getEmail(), 30), reader.isActive() ? "да" : "нет", date(reader.getRegisteredAt()));
        }
    }

    public void printBooks(List<Book> books) {
        if (books.isEmpty()) { empty(); return; }
        output.printf("%-5s %-20s %-30s %-25s %-6s %-8s %-8s%n",
            "ID", "ISBN", "Название", "Автор", "Год", "Всего", "Доступно");
        for (Book book : books) {
            output.printf("%-5d %-20s %-30s %-25s %-6d %-8d %-8d%n", book.getId(),
                crop(book.getIsbn(), 20), crop(book.getTitle(), 30), crop(book.getAuthor(), 25),
                book.getPublicationYear(), book.getTotalCopies(), book.getAvailableCopies());
        }
    }

    public void printRequests(List<BookIssueRequestDetails> requests) {
        if (requests.isEmpty()) { empty(); return; }
        output.printf("%-5s %-24s %-28s %-11s %-17s %-12s %-12s %-12s%n",
            "ID", "Читатель", "Книга", "Статус", "Создана", "Выдана", "Срок", "Возврат");
        for (BookIssueRequestDetails details : requests) {
            BookIssueRequest request = details.getRequest();
            output.printf("%-5d %-24s %-28s %-11s %-17s %-12s %-12s %-12s%n",
                request.getId(), crop(details.getReaderName(), 24), crop(details.getBookTitle(), 28),
                request.getStatus(), dateTime(request.getRequestedAt()), date(request.getIssuedOn()),
                date(request.getDueOn()), date(request.getReturnedOn()));
        }
    }

    public void printStatistics(SystemStatistics statistics) {
        output.println("Читателей: " + statistics.getReaderCount());
        output.println("Наименований книг: " + statistics.getBookTitleCount());
        output.println("Экземпляров: " + statistics.getTotalCopyCount());
        output.println("Доступно: " + statistics.getAvailableCopyCount());
        output.println("Заявок: " + statistics.getRequestCount());
        for (RequestStatus status : RequestStatus.values()) {
            output.println("  " + status + ": " + statistics.getRequestsByStatus().getOrDefault(status, 0L));
        }
        output.println("Просрочено: " + statistics.getOverdueRequestCount());
    }

    private void empty() { output.println("Записи не найдены."); }
    private static String date(LocalDate value) { return value == null ? "—" : value.toString(); }
    private static String dateTime(LocalDateTime value) { return value == null ? "—" : value.format(DATE_TIME); }
    private static String crop(String value, int length) {
        if (value == null) return "—";
        return value.length() <= length ? value : value.substring(0, Math.max(1, length - 1)) + "…";
    }
}
