package ru.mirea.library.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ConsoleInputTest {
    @Test
    void readLongRepromptsAfterTextAndNegativeId() {
        Fixture fixture = fixture("abc\n-2\n42\n");

        assertEquals(42L, fixture.input().readLong("ID: "));
        assertTrue(fixture.outputText().contains("Ошибка: ID должен быть целым положительным числом."));
    }

    @Test
    void readIntInRangeRejectsOutOfRangeChoice() {
        Fixture fixture = fixture("9\n2\n");

        assertEquals(2, fixture.input().readIntInRange("Выбор: ", 0, 3));
        assertTrue(fixture.outputText().contains("Ошибка: введите число от 0 до 3."));
    }

    @Test
    void readDateRepromptsAfterInvalidIsoDate() {
        Fixture fixture = fixture("27.09.2026\n2026-09-27\n");

        assertEquals(LocalDate.of(2026, 9, 27), fixture.input().readDate("Дата: "));
        assertTrue(fixture.outputText().contains("Ошибка: дата должна быть в формате ГГГГ-ММ-ДД."));
    }

    @Test
    void eofReturnsNullWithoutRetryLoop() {
        Fixture fixture = fixture("");

        assertNull(fixture.input().readLong("ID: "));
        assertTrue(fixture.input().isEndOfInput());
    }

    private static Fixture fixture(String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleInput consoleInput = new ConsoleInput(
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
            new PrintStream(output, true, StandardCharsets.UTF_8));
        return new Fixture(consoleInput, output);
    }

    private record Fixture(ConsoleInput input, ByteArrayOutputStream output) {
        String outputText() { return output.toString(StandardCharsets.UTF_8); }
    }
}
