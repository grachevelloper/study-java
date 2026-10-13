package ru.mirea.store.export;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mirea.store.model.*;

class ExcelExporterTest {

  @TempDir
  Path directory;

  @Test
  void writesFiveReopenableSheetsWithNumericMoney() throws Exception {
    Customer customer = new Customer(
      1,
      "Покупатель",
      "a@b.ru",
      "123",
      true,
      LocalDateTime.now()
    );
    Product product = new Product(
      1,
      "SKU",
      "Товар",
      new BigDecimal("12.50"),
      3,
      true
    );
    CustomerOrder order = new CustomerOrder(
      1,
      1,
      OrderStatus.DELIVERED,
      LocalDateTime.now(),
      LocalDateTime.now(),
      LocalDateTime.now(),
      LocalDateTime.now(),
      null,
      "note"
    );
    OrderItem item = new OrderItem(1, 1, 1, 2, new BigDecimal("12.50"));
    CustomerOrderDetails details = new CustomerOrderDetails(
      order,
      "Покупатель",
      List.of(new OrderItemDetails(item, "SKU", "Товар"))
    );
    EnumMap<OrderStatus, Long> statuses = new EnumMap<>(OrderStatus.class);
    for (OrderStatus status : OrderStatus.values())
      statuses.put(status, status == OrderStatus.DELIVERED ? 1L : 0L);
    StoreExportData data = new StoreExportData(
      List.of(customer),
      List.of(product),
      List.of(details),
      new SystemStatistics(1, 1, 3, 1, statuses, new BigDecimal("25.00"), 0)
    );
    Path file = new ExcelExporter().export(data, directory);
    assertTrue(
      file.getFileName().toString().startsWith("online-store-export-")
    );
    Path selectedFile = directory.resolve("selected-by-user.xlsx");
    assertEquals(selectedFile,
      new ExcelExporter().exportToFile(data, selectedFile));
    assertTrue(Files.isRegularFile(selectedFile));
    try (
      InputStream input = Files.newInputStream(file);
      Workbook workbook = WorkbookFactory.create(input)
    ) {
      assertEquals(
        List.of("Customers", "Products", "Orders", "Order Items", "Statistics"),
        java.util.stream.IntStream.range(0, workbook.getNumberOfSheets())
          .mapToObj(i -> workbook.getSheetAt(i).getSheetName())
          .toList()
      );
      assertEquals(
        CellType.NUMERIC,
        workbook.getSheet("Products").getRow(1).getCell(3).getCellType()
      );
      assertEquals(
        12.5,
        workbook.getSheet("Products").getRow(1).getCell(3).getNumericCellValue()
      );
      assertEquals(
        25.0,
        workbook.getSheet("Orders").getRow(1).getCell(9).getNumericCellValue()
      );
      assertEquals(1, workbook.getSheet("Order Items").getLastRowNum());
    }
  }
}
