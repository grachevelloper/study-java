package ru.mirea.store.ui;

import java.io.*; import java.math.BigDecimal; import java.nio.charset.StandardCharsets; import java.time.LocalDate; import java.time.format.DateTimeParseException; import java.util.Objects;
import ru.mirea.store.exception.BusinessRuleException;
import ru.mirea.store.validation.InputValidator;
public final class ConsoleInput {
    private static final int MAX_RAW_LINE_LENGTH = 4096;
    private final BufferedReader reader; private final PrintStream output; private boolean endOfInput;
    public ConsoleInput(InputStream input, PrintStream output) { reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(input), StandardCharsets.UTF_8)); this.output = Objects.requireNonNull(output); }
    public Long readLong(String prompt) { while (true) { String value = raw(prompt); if (value == null) return null; String normalized=value.trim(); try { if(normalized.matches("[0-9]+")){long parsed=Long.parseLong(normalized);if(parsed>0)return parsed;} } catch (NumberFormatException ignored) { } output.println("Ошибка: ID должен быть целым положительным числом без знаков и других символов."); } }
    public Integer readIntInRange(String prompt, int min, int max) { while (true) { String value = raw(prompt); if (value == null) return null; String normalized=value.trim(); try { if(normalized.matches("[0-9]+")){int parsed=Integer.parseInt(normalized);if(parsed>=min&&parsed<=max)return parsed;} } catch (NumberFormatException ignored) { } output.printf("Ошибка: введите число от %d до %d.%n", min, max); } }
    public Integer readPositiveInt(String prompt) { while (true) { String value = raw(prompt); if (value == null) return null; String normalized=value.trim(); try { if(normalized.matches("[0-9]+")){int parsed=Integer.parseInt(normalized);return InputValidator.quantity(parsed,"Количество");} } catch (NumberFormatException|BusinessRuleException ignored) { } output.println("Ошибка: введите целое число от 1 до "+InputValidator.MAX_QUANTITY+"."); } }
    public Integer readNonNegativeInt(String prompt) { while (true) { String value = raw(prompt); if (value == null) return null; String normalized=value.trim(); try { if(normalized.matches("[0-9]+")){int parsed=Integer.parseInt(normalized);return InputValidator.stock(parsed);} } catch (NumberFormatException|BusinessRuleException ignored) { } output.println("Ошибка: введите целое число от 0 до "+InputValidator.MAX_QUANTITY+"."); } }
    public BigDecimal readPositiveDecimal(String prompt) { while (true) { String value = raw(prompt); if (value == null) return null; String normalized=value.trim(); try { if(normalized.matches("(?:0|[1-9][0-9]{0,9})(?:[.,][0-9]{1,2})?"))return InputValidator.money(new BigDecimal(normalized.replace(',','.'))); } catch (NumberFormatException|BusinessRuleException ignored) { } output.println("Ошибка: цена должна быть от 0.01 до 9999999999.99, максимум 2 знака после запятой."); } }
    public LocalDate readDate(String prompt) { while (true) { String value = raw(prompt); if (value == null) return null; String normalized=value.trim(); try { if(normalized.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))return InputValidator.date(LocalDate.parse(normalized),"Дата"); } catch (DateTimeParseException|BusinessRuleException ignored) { } output.println("Ошибка: дата должна быть реальной датой в формате ГГГГ-ММ-ДД с годом от 0001 до 9999."); } }
    public String readRequired(String prompt) { return readRequiredText(prompt,"Значение",InputValidator.MAX_SEARCH_LENGTH); }
    public String readOptional(String prompt) { return readOptionalText(prompt,"Значение",InputValidator.MAX_NOTE_LENGTH); }
    public String readRequiredText(String prompt,String field,int maxLength){return readValidated(prompt,value->InputValidator.requiredText(value,field,maxLength));}
    public String readOptionalText(String prompt,String field,int maxLength){return readValidated(prompt,value->InputValidator.optionalText(value,field,maxLength));}
    public String readEmail(String prompt){return readValidated(prompt,InputValidator::email);}
    public String readPhone(String prompt){return readValidated(prompt,InputValidator::phone);}
    public String readSku(String prompt){return readValidated(prompt,InputValidator::sku);}
    public boolean isEndOfInput() { return endOfInput; }
    private String readValidated(String prompt,TextValidator validator){while(true){String value=raw(prompt);if(value==null)return null;try{return validator.validate(value);}catch(BusinessRuleException exception){output.println("Ошибка: "+exception.getMessage());}}}
    private String raw(String prompt) {
        while (true) {
            output.print(prompt);
            output.flush();
            try {
                Line line = readBoundedLine();
                if (line == null) {
                    endOfInput = true;
                    return null;
                }
                if (!line.tooLong()) return line.value();
                output.println("Ошибка: строка ввода слишком длинная (максимум "
                    + MAX_RAW_LINE_LENGTH + " символов).");
            } catch (IOException exception) {
                throw new IllegalStateException("Не удалось прочитать ввод.", exception);
            }
        }
    }

    private Line readBoundedLine() throws IOException {
        StringBuilder value = new StringBuilder();
        boolean readAnything = false;
        boolean tooLong = false;
        while (true) {
            int character = reader.read();
            if (character == -1) {
                if (!readAnything) return null;
                break;
            }
            readAnything = true;
            if (character == '\n') break;
            if (character == '\r') {
                reader.mark(1);
                int next = reader.read();
                if (next != '\n' && next != -1) reader.reset();
                break;
            }
            if (value.length() < MAX_RAW_LINE_LENGTH) value.append((char) character);
            else tooLong = true;
        }
        return new Line(value.toString(), tooLong);
    }

    private record Line(String value, boolean tooLong) { }
    @FunctionalInterface private interface TextValidator{String validate(String value);}
}
