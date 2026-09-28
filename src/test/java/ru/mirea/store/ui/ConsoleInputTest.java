package ru.mirea.store.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ConsoleInputTest {

  @Test
  void retriesInvalidPositiveIntegerAndMoney() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ConsoleInput input = new ConsoleInput(
      new ByteArrayInputStream(
        "abc\n0\n3\nno\n12.50\n".getBytes(StandardCharsets.UTF_8)
      ),
      new PrintStream(output, true, StandardCharsets.UTF_8)
    );
    assertEquals(3, input.readPositiveInt("Количество: "));
    assertEquals(new BigDecimal("12.50"), input.readPositiveDecimal("Цена: "));
  }

  @Test
  void rejectsNumericTricksOverflowAndUnsupportedMoneyFormats() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    String source =
      "-0\n+1\n1e0\n9223372036854775808\n7\n" +
      "NaN\nInfinity\n1e3\n.5\n1.234\n10000000000.00\n12,50\n";
    ConsoleInput input = input(source, output);

    assertEquals(7L, input.readLong("ID: "));
    assertEquals(new BigDecimal("12.50"), input.readPositiveDecimal("Цена: "));
  }

  @Test
  void retriesMalformedTextFieldsAndNormalizesAcceptedValues() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ConsoleInput input = input(
      "bad email\nUser@Example.COM\n" +
        "abc<script>\n+7 (999) 123-45-67\n" +
        "bad sku!\nabc-_1.2\n" +
        "bad\tname\n  Нормальное   имя  \n" +
        "bad\u200Bnote\n  обычное примечание  \n",
      output
    );

    assertEquals("user@example.com", input.readEmail("Email: "));
    assertEquals("+7 (999) 123-45-67", input.readPhone("Телефон: "));
    assertEquals("ABC-_1.2", input.readSku("SKU: "));
    assertEquals("Нормальное имя", input.readRequiredText("Имя: ", "Имя", 30));
    assertEquals(
      "обычное примечание",
      input.readOptionalText("Примечание: ", "Примечание", 30)
    );
  }

  @Test
  void emptyOptionalPhoneAndNoteRemainEmpty() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ConsoleInput input = input("\t\n\n\n", output);
    assertNull(input.readPhone("Телефон: "));
    assertNull(input.readOptionalText("Примечание: ", "Примечание", 500));
    assertTrue(
      output
        .toString(StandardCharsets.UTF_8)
        .contains("недопустимый управляющий")
    );
  }

  @Test
  void rejectsNonCanonicalAndOutOfDatabaseRangeDates() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ConsoleInput input = input(
      "+999999999-12-31\n2026-02-30\n10000-01-01\n2026-01-31\n",
      output
    );
    assertEquals(java.time.LocalDate.of(2026, 1, 31), input.readDate("Дата: "));
  }

  @Test
  void discardsOversizedRawLineBeforeParsingIt() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ConsoleInput input = input("x".repeat(5_000) + "\nнормально\n", output);
    assertEquals("нормально", input.readRequiredText("Текст: ", "Текст", 20));
    assertTrue(
      output
        .toString(StandardCharsets.UTF_8)
        .contains("строка ввода слишком длинная")
    );
  }

  private static ConsoleInput input(
    String source,
    ByteArrayOutputStream output
  ) {
    return new ConsoleInput(
      new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),
      new PrintStream(output, true, StandardCharsets.UTF_8)
    );
  }
}
