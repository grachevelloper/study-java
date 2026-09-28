package ru.mirea.library.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mirea.library.model.Book;
import ru.mirea.library.model.BookIssueRequest;
import ru.mirea.library.model.BookIssueRequestDetails;
import ru.mirea.library.model.Reader;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.model.SystemStatistics;

class ExcelExporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesReopenableWorkbookWithFourPopulatedSheetsAndDates() throws Exception {
        EnumMap<RequestStatus, Long> byStatus = new EnumMap<>(RequestStatus.class);
        for (RequestStatus status : RequestStatus.values()) byStatus.put(status, 0L);
        byStatus.put(RequestStatus.ISSUED, 1L);
        Reader reader = new Reader(1, "CARD-1", "Reader", "reader@example.com", true,
            LocalDate.of(2025, 1, 2));
        Book book = new Book(1, "ISBN-1", "Book", "Author", 2020, 2, 1);
        BookIssueRequest request = new BookIssueRequest(
            1, 1, 1, RequestStatus.ISSUED, LocalDateTime.of(2026, 9, 27, 9, 30),
            LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 27), null, "note");
        LibraryExportData data = new LibraryExportData(
            List.of(reader), List.of(book),
            List.of(new BookIssueRequestDetails(request, "Reader", "Book", "Author")),
            new SystemStatistics(1, 1, 2, 1, 1, byStatus, 0));

        Path file = new ExcelExporter().export(data, temporaryDirectory);

        assertTrue(Files.isRegularFile(file));
        try (InputStream input = Files.newInputStream(file);
             Workbook workbook = WorkbookFactory.create(input)) {
            assertEquals(List.of("Readers", "Books", "Requests", "Statistics"),
                java.util.stream.IntStream.range(0, workbook.getNumberOfSheets())
                    .mapToObj(index -> workbook.getSheetAt(index).getSheetName()).toList());
            assertEquals("ID", workbook.getSheet("Readers").getRow(0).getCell(0).getStringCellValue());
            assertEquals(1, workbook.getSheet("Readers").getLastRowNum());
            assertEquals(1, workbook.getSheet("Books").getLastRowNum());
            assertEquals(1, workbook.getSheet("Requests").getLastRowNum());
            assertEquals(CellType.NUMERIC, workbook.getSheet("Readers").getRow(1).getCell(5).getCellType());
            assertTrue(DateUtil.isCellDateFormatted(workbook.getSheet("Readers").getRow(1).getCell(5)));
            assertTrue(DateUtil.isCellDateFormatted(workbook.getSheet("Requests").getRow(1).getCell(8)));
        }
    }
}
