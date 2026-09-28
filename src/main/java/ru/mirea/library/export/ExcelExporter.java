package ru.mirea.library.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.mirea.library.exception.ExportException;
import ru.mirea.library.model.Book;
import ru.mirea.library.model.BookIssueRequest;
import ru.mirea.library.model.BookIssueRequestDetails;
import ru.mirea.library.model.Reader;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.model.SystemStatistics;

public final class ExcelExporter implements DataExporter {
    private static final int MAX_COLUMN_WIDTH = 50 * 256;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Override
    public Path export(LibraryExportData data, Path targetDirectory) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        try {
            Files.createDirectories(targetDirectory);
            Path path = targetDirectory.resolve(
                "library-export-" + LocalDateTime.now().format(FILE_TIME) + ".xlsx");
            try (Workbook workbook = new XSSFWorkbook();
                 OutputStream output = Files.newOutputStream(path)) {
                Styles styles = createStyles(workbook);
                writeReaders(workbook, data, styles);
                writeBooks(workbook, data, styles);
                writeRequests(workbook, data, styles);
                writeStatistics(workbook, data.statistics(), styles);
                workbook.write(output);
            }
            return path;
        } catch (IOException exception) {
            throw new ExportException("Не удалось создать Excel-файл.", exception);
        }
    }

    private static Styles createStyles(Workbook workbook) {
        CellStyle header = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        header.setFont(font);
        header.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle date = workbook.createCellStyle();
        date.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
        CellStyle dateTime = workbook.createCellStyle();
        dateTime.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        return new Styles(header, date, dateTime);
    }

    private static void writeReaders(Workbook workbook, LibraryExportData data, Styles styles) {
        Sheet sheet = workbook.createSheet("Readers");
        String[] headers = {"ID", "Library card", "Full name", "Email", "Active", "Registered at"};
        writeHeader(sheet, headers, styles.header());
        int rowIndex = 1;
        for (Reader reader : data.readers()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(reader.getId());
            row.createCell(1).setCellValue(reader.getLibraryCardNumber());
            row.createCell(2).setCellValue(reader.getFullName());
            row.createCell(3).setCellValue(reader.getEmail());
            row.createCell(4).setCellValue(reader.isActive());
            setDate(row.createCell(5), reader.getRegisteredAt(), styles.date());
        }
        finishSheet(sheet, headers.length);
    }

    private static void writeBooks(Workbook workbook, LibraryExportData data, Styles styles) {
        Sheet sheet = workbook.createSheet("Books");
        String[] headers = {"ID", "ISBN", "Title", "Author", "Publication year", "Total copies", "Available copies"};
        writeHeader(sheet, headers, styles.header());
        int rowIndex = 1;
        for (Book book : data.books()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(book.getId());
            row.createCell(1).setCellValue(book.getIsbn());
            row.createCell(2).setCellValue(book.getTitle());
            row.createCell(3).setCellValue(book.getAuthor());
            row.createCell(4).setCellValue(book.getPublicationYear());
            row.createCell(5).setCellValue(book.getTotalCopies());
            row.createCell(6).setCellValue(book.getAvailableCopies());
        }
        finishSheet(sheet, headers.length);
    }

    private static void writeRequests(Workbook workbook, LibraryExportData data, Styles styles) {
        Sheet sheet = workbook.createSheet("Requests");
        String[] headers = {"ID", "Reader ID", "Reader", "Book ID", "Book", "Author", "Status",
            "Requested at", "Issued on", "Due on", "Returned on", "Note"};
        writeHeader(sheet, headers, styles.header());
        int rowIndex = 1;
        for (BookIssueRequestDetails details : data.requests()) {
            BookIssueRequest request = details.getRequest();
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(request.getId());
            row.createCell(1).setCellValue(request.getReaderId());
            row.createCell(2).setCellValue(details.getReaderName());
            row.createCell(3).setCellValue(request.getBookId());
            row.createCell(4).setCellValue(details.getBookTitle());
            row.createCell(5).setCellValue(details.getBookAuthor());
            row.createCell(6).setCellValue(request.getStatus().name());
            setDateTime(row.createCell(7), request.getRequestedAt(), styles.dateTime());
            setDate(row.createCell(8), request.getIssuedOn(), styles.date());
            setDate(row.createCell(9), request.getDueOn(), styles.date());
            setDate(row.createCell(10), request.getReturnedOn(), styles.date());
            row.createCell(11).setCellValue(request.getNote() == null ? "" : request.getNote());
        }
        finishSheet(sheet, headers.length);
    }

    private static void writeStatistics(Workbook workbook, SystemStatistics statistics, Styles styles) {
        Sheet sheet = workbook.createSheet("Statistics");
        writeHeader(sheet, new String[] {"Indicator", "Value"}, styles.header());
        int row = 1;
        row = statistic(sheet, row, "Readers", statistics.getReaderCount());
        row = statistic(sheet, row, "Book titles", statistics.getBookTitleCount());
        row = statistic(sheet, row, "Total copies", statistics.getTotalCopyCount());
        row = statistic(sheet, row, "Available copies", statistics.getAvailableCopyCount());
        row = statistic(sheet, row, "Requests", statistics.getRequestCount());
        row = statistic(sheet, row, "Overdue requests", statistics.getOverdueRequestCount());
        for (RequestStatus status : RequestStatus.values()) {
            row = statistic(sheet, row, "Status " + status.name(),
                statistics.getRequestsByStatus().getOrDefault(status, 0L));
        }
        finishSheet(sheet, 2);
    }

    private static int statistic(Sheet sheet, int rowIndex, String name, long value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(name);
        row.createCell(1).setCellValue(value);
        return rowIndex + 1;
    }

    private static void writeHeader(Sheet sheet, String[] headers, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(style);
        }
    }

    private static void finishSheet(Sheet sheet, int columnCount) {
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, sheet.getLastRowNum()), 0, columnCount - 1));
        for (int index = 0; index < columnCount; index++) {
            int longest = 10;
            for (Row row : sheet) {
                Cell cell = row.getCell(index);
                if (cell != null) longest = Math.max(longest, visibleLength(cell));
            }
            sheet.setColumnWidth(index, Math.min((longest + 2) * 256, MAX_COLUMN_WIDTH));
        }
    }

    private static int visibleLength(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().length();
            case BOOLEAN -> 5;
            case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? 16 : 12;
            default -> 10;
        };
    }

    private static void setDate(Cell cell, LocalDate value, CellStyle style) {
        if (value == null) return;
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void setDateTime(Cell cell, LocalDateTime value, CellStyle style) {
        if (value == null) return;
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private record Styles(CellStyle header, CellStyle date, CellStyle dateTime) { }
}
