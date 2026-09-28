package ru.mirea.library;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import ru.mirea.library.config.DatabaseConfig;
import ru.mirea.library.config.DatabaseManager;
import ru.mirea.library.export.ExcelExporter;
import ru.mirea.library.repository.jdbc.JdbcBookIssueRequestRepository;
import ru.mirea.library.repository.jdbc.JdbcBookRepository;
import ru.mirea.library.repository.jdbc.JdbcReaderRepository;
import ru.mirea.library.repository.jdbc.JdbcStatisticsRepository;
import ru.mirea.library.service.BookIssueRequestService;
import ru.mirea.library.service.BookService;
import ru.mirea.library.service.ReaderService;
import ru.mirea.library.service.StatisticsService;
import ru.mirea.library.ui.ConsoleApplication;
import ru.mirea.library.ui.ConsoleInput;
import ru.mirea.library.ui.ConsolePrinter;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        DatabaseManager database = new DatabaseManager(DatabaseConfig.fromEnvironment());
        if (!databaseIsAvailable(database)) {
            System.err.println("Не удалось подключиться к PostgreSQL. Проверьте DB_URL, DB_USER, DB_PASSWORD и Docker Compose.");
            return;
        }

        Clock clock = Clock.systemDefaultZone();
        JdbcReaderRepository readerRepository = new JdbcReaderRepository(database);
        JdbcBookRepository bookRepository = new JdbcBookRepository(database);
        JdbcBookIssueRequestRepository requestRepository = new JdbcBookIssueRequestRepository(database);
        ConsoleApplication application = new ConsoleApplication(
            new ReaderService(readerRepository, clock),
            new BookService(bookRepository),
            new BookIssueRequestService(requestRepository, readerRepository, bookRepository, clock),
            new StatisticsService(new JdbcStatisticsRepository(database), clock),
            new ExcelExporter(),
            new ConsoleInput(System.in, System.out),
            new ConsolePrinter(System.out)
        );
        application.run();
    }

    private static boolean databaseIsAvailable(DatabaseManager database) {
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }
}
