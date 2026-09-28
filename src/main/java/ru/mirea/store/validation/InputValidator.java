package ru.mirea.store.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;
import ru.mirea.store.exception.BusinessRuleException;

public final class InputValidator {

  public static final int MAX_NAME_LENGTH = 200;
  public static final int MAX_EMAIL_LENGTH = 320;
  public static final int MAX_PHONE_LENGTH = 50;
  public static final int MAX_SKU_LENGTH = 64;
  public static final int MAX_PRODUCT_NAME_LENGTH = 300;
  public static final int MAX_NOTE_LENGTH = 500;
  public static final int MAX_SEARCH_LENGTH = 300;
  public static final int MAX_ORDER_ITEMS = 100;
  public static final int MAX_QUANTITY = 1_000_000;
  public static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");

  private static final Pattern EMAIL = Pattern.compile(
    "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@" +
    "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?" +
    "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*" +
    "\\.[A-Za-z]{2,63}$"
  );
  private static final Pattern PHONE = Pattern.compile("^[+()0-9 -]+$");
  private static final Pattern SKU = Pattern.compile(
    "^[A-Z0-9][A-Z0-9._-]{0,63}$"
  );

  private InputValidator() {}

  public static String requiredText(String value, String field, int maxLength) {
    String normalized = normalizeText(value, field);
    if (normalized.isEmpty()) {
      throw new BusinessRuleException(field + " не может быть пустым.");
    }
    if (normalized.codePointCount(0, normalized.length()) > maxLength) {
      throw new BusinessRuleException(
        field + " не может быть длиннее " + maxLength + " символов."
      );
    }
    return normalized;
  }

  public static String optionalText(String value, String field, int maxLength) {
    if (value == null) return null;
    String normalized = normalizeText(value, field);
    if (normalized.isEmpty()) return null;
    if (normalized.codePointCount(0, normalized.length()) > maxLength) {
      throw new BusinessRuleException(
        field + " не может быть длиннее " + maxLength + " символов."
      );
    }
    return normalized;
  }

  public static String email(String value) {
    String normalized = requiredText(
      value,
      "Email",
      MAX_EMAIL_LENGTH
    ).toLowerCase(Locale.ROOT);
    int at = normalized.indexOf('@');
    if (
      !EMAIL.matcher(normalized).matches() ||
      normalized.startsWith(".") ||
      (at > 0 && normalized.charAt(at - 1) == '.') ||
      normalized.contains("..")
    ) {
      throw new BusinessRuleException(
        "Введите корректный email, например user@example.com."
      );
    }
    return normalized;
  }

  public static String phone(String value) {
    String normalized = optionalText(value, "Телефон", MAX_PHONE_LENGTH);
    if (normalized == null) return null;
    long digits = normalized.chars().filter(Character::isDigit).count();
    int plus = normalized.indexOf('+');
    if (
      !PHONE.matcher(normalized).matches() ||
      digits < 5 ||
      digits > 15 ||
      (plus >= 0 && (plus != 0 || normalized.lastIndexOf('+') != plus)) ||
      !balancedParentheses(normalized)
    ) {
      throw new BusinessRuleException(
        "Телефон может содержать 5–15 цифр, пробелы, +, скобки и дефисы."
      );
    }
    return normalized;
  }

  public static String sku(String value) {
    String normalized = requiredText(value, "SKU", MAX_SKU_LENGTH).toUpperCase(
      Locale.ROOT
    );
    if (!SKU.matcher(normalized).matches()) {
      throw new BusinessRuleException(
        "SKU должен начинаться с буквы или цифры и содержать " +
        "только A-Z, 0-9, '.', '_' или '-'."
      );
    }
    return normalized;
  }

  public static BigDecimal money(BigDecimal value) {
    if (value == null || value.signum() <= 0) {
      throw new BusinessRuleException("Цена должна быть положительной.");
    }
    BigDecimal stripped = value.stripTrailingZeros();
    if (Math.max(0, stripped.scale()) > 2 || value.compareTo(MAX_MONEY) > 0) {
      throw new BusinessRuleException(
        "Цена должна быть не больше 9999999999.99 и содержать " +
        "максимум 2 знака после запятой."
      );
    }
    return value.setScale(2, RoundingMode.UNNECESSARY);
  }

  public static int quantity(int value, String field) {
    if (value <= 0 || value > MAX_QUANTITY) {
      throw new BusinessRuleException(
        field + " должно быть от 1 до " + MAX_QUANTITY + "."
      );
    }
    return value;
  }

  public static int stock(int value) {
    if (value < 0 || value > MAX_QUANTITY) {
      throw new BusinessRuleException(
        "Остаток должен быть от 0 до " + MAX_QUANTITY + "."
      );
    }
    return value;
  }

  public static LocalDate date(LocalDate value, String field) {
    if (value == null || value.getYear() < 1 || value.getYear() > 9999) {
      throw new BusinessRuleException(
        field + " должна содержать четырёхзначный год от 0001 до 9999."
      );
    }
    return value;
  }

  private static String normalizeText(String value, String field) {
    if (value == null) return "";
    StringBuilder result = new StringBuilder(value.length());
    boolean pendingSpace = false;
    for (int offset = 0; offset < value.length(); ) {
      int codePoint = value.codePointAt(offset);
      offset += Character.charCount(codePoint);
      if (
        Character.isISOControl(codePoint) ||
        Character.getType(codePoint) == Character.FORMAT
      ) {
        throw new BusinessRuleException(
          field + " содержит недопустимый управляющий или невидимый символ."
        );
      }
      if (
        Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
      ) {
        pendingSpace = result.length() > 0;
      } else {
        if (pendingSpace) result.append(' ');
        result.appendCodePoint(codePoint);
        pendingSpace = false;
      }
    }
    return result.toString();
  }

  private static boolean balancedParentheses(String value) {
    int balance = 0;
    for (int index = 0; index < value.length(); index++) {
      if (value.charAt(index) == '(') balance++;
      if (value.charAt(index) == ')' && --balance < 0) return false;
    }
    return balance == 0;
  }
}
