package ru.mirea.library.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

public final class ConsoleInput {
    private final BufferedReader reader;
    private final PrintStream output;
    private boolean endOfInput;

    public ConsoleInput(InputStream input, PrintStream output) {
        this.reader = new BufferedReader(new InputStreamReader(
            Objects.requireNonNull(input), StandardCharsets.UTF_8));
        this.output = Objects.requireNonNull(output);
    }

    public Long readLong(String prompt) {
        while (true) {
            String value = readRaw(prompt);
            if (value == null) return null;
            try {
                long parsed = Long.parseLong(value.trim());
                if (parsed > 0) return parsed;
            } catch (NumberFormatException ignored) {
                // The common user-facing message is printed below.
            }
            output.println("Ошибка: ID должен быть целым положительным числом.");
        }
    }

    public Integer readInt(String prompt) {
        while (true) {
            String value = readRaw(prompt);
            if (value == null) return null;
            try {
                return Integer.valueOf(value.trim());
            } catch (NumberFormatException ignored) {
                output.println("Ошибка: введите целое число.");
            }
        }
    }

    public Integer readIntInRange(String prompt, int minimum, int maximum) {
        while (true) {
            String value = readRaw(prompt);
            if (value == null) return null;
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed >= minimum && parsed <= maximum) return parsed;
            } catch (NumberFormatException ignored) {
                // The common range message is printed below.
            }
            output.printf("Ошибка: введите число от %d до %d.%n", minimum, maximum);
        }
    }

    public LocalDate readDate(String prompt) {
        while (true) {
            String value = readRaw(prompt);
            if (value == null) return null;
            try {
                return LocalDate.parse(value.trim());
            } catch (DateTimeParseException ignored) {
                output.println("Ошибка: дата должна быть в формате ГГГГ-ММ-ДД.");
            }
        }
    }

    public String readRequired(String prompt) {
        while (true) {
            String value = readRaw(prompt);
            if (value == null) return null;
            if (!value.trim().isEmpty()) return value.trim();
            output.println("Ошибка: значение не может быть пустым.");
        }
    }

    public String readOptional(String prompt) {
        String value = readRaw(prompt);
        return value == null ? null : value.trim();
    }

    public boolean isEndOfInput() {
        return endOfInput;
    }

    private String readRaw(String prompt) {
        output.print(prompt);
        output.flush();
        try {
            String value = reader.readLine();
            if (value == null) endOfInput = true;
            return value;
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось прочитать ввод.", exception);
        }
    }
}
