package ru.mirea.storeapi.service;

import jakarta.validation.Validator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.mirea.storeapi.config.AppProperties;
import ru.mirea.storeapi.domain.OrderStatus;
import ru.mirea.storeapi.domain.Product;
import ru.mirea.storeapi.domain.StoreOrder;
import ru.mirea.storeapi.dto.ImportDtos.*;
import ru.mirea.storeapi.dto.OrderDtos.ItemRequest;
import ru.mirea.storeapi.dto.OrderDtos.OrderRequest;
import ru.mirea.storeapi.error.BadRequestException;
import ru.mirea.storeapi.error.ConflictException;
import ru.mirea.storeapi.error.NotFoundException;
import ru.mirea.storeapi.filter.OrderFilter;
import ru.mirea.storeapi.filter.OrderSpecifications;
import ru.mirea.storeapi.repository.CustomerRepository;
import ru.mirea.storeapi.repository.OrderItemRepository;
import ru.mirea.storeapi.repository.ProductRepository;
import ru.mirea.storeapi.repository.StoreOrderRepository;

@Service
public class CsvOrderService {
    private static final List<String> HEADERS = List.of("external_id",
        "customer_email", "note", "urgent", "tags", "items", "status");
    private static final CSVFormat INPUT =
        CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true).setTrim(true).get();

    private final StoreOrderRepository orders;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final OrderItemRepository items;
    private final OrderService orderService;
    private final Validator validator;
    private final int maxRows;

    public CsvOrderService(StoreOrderRepository orders,
        CustomerRepository customers, ProductRepository products,
        OrderItemRepository items, OrderService orderService,
        Validator validator, AppProperties props) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.items = items;
        this.orderService = orderService;
        this.validator = validator;
        this.maxRows = props.importLimits().maxRows();
    }

    public ImportPreview preview(MultipartFile file, ImportMode mode) {
        PreparedImport prepared = prepare(file, mode);
        return new ImportPreview(prepared.total(), prepared.rows().size(),
            prepared.issues().size(), prepared.issues());
    }

    public ImportResult apply(MultipartFile file, ImportMode mode) {
        PreparedImport prepared = prepare(file, mode);
        int created = 0;
        int updated = 0;
        var issues = new ArrayList<>(prepared.issues());
        for (PreparedRow row : prepared.rows()) {
            try {
                if (row.id() == null) {
                    orderService.create(row.request());
                    created++;
                } else {
                    orderService.update(row.id(), row.request());
                    updated++;
                }
            } catch (BadRequestException | ConflictException
                | NotFoundException ex) {
                issues.add(new RowIssue(row.line(), "order", ex.getMessage()));
            } catch (DataIntegrityViolationException
                | OptimisticLockingFailureException ex) {
                issues.add(new RowIssue(row.line(), "order",
                    "Заказ конфликтует с текущими данными"));
            }
        }
        issues.sort(Comparator.comparingLong(RowIssue::row));
        return new ImportResult(prepared.total(), created, updated,
            issues.size(), issues);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] export(OrderFilter filter) throws IOException {
        var buffer = new StringWriter();
        try (var csv = new CSVPrinter(buffer, CSVFormat.DEFAULT)) {
            var headers = new ArrayList<>(HEADERS);
            headers.addAll(List.of("total", "priority", "archived"));
            csv.printRecord(headers);
            var matching = OrderSpecifications.matching(filter);
            for (var page = orders.findAll(matching,
                PageRequest.of(0, 200, Sort.by("id")));; page =
                    orders.findAll(matching, page.nextPageable())) {
                for (StoreOrder order : page) {
                    String positions =
                        items.findByOrderIdOrderByProductId(order.getId())
                            .stream()
                            .map(i -> i.getProduct().getSku() + ":"
                                + i.getQuantity())
                            .collect(Collectors.joining("|"));
                    String tags =
                        String.join("|", new TreeSet<>(order.getTags()));
                    csv.printRecord(order.getExternalId(),
                        escape(order.getCustomer().getEmail()),
                        escape(order.getNote()), order.isUrgent(), escape(tags),
                        positions, order.getStatus().name(), order.getTotal(),
                        order.getPriority().name(), order.isArchived());
                }
                if (!page.hasNext())
                    break;
            }
        }
        return buffer.toString().getBytes(StandardCharsets.UTF_8);
    }

    private PreparedImport prepare(MultipartFile file, ImportMode mode) {
        if (file.isEmpty())
            throw new BadRequestException("CSV-файл пуст");
        var rows = new ArrayList<PreparedRow>();
        var issues = new ArrayList<RowIssue>();
        var codes = new HashSet<String>();
        int count = 0;
        try (var reader = utf8(file.getInputStream());
            CSVParser parser = INPUT.parse(reader)) {
            if (!parser.getHeaderNames().containsAll(HEADERS)) {
                throw new BadRequestException(
                    "Обязательные столбцы CSV: " + String.join(", ", HEADERS));
            }
            for (CSVRecord csv : parser) {
                if (++count > maxRows)
                    throw new BadRequestException(
                        "В CSV допускается не более " + maxRows + " строк");
                long line = csv.getRecordNumber() + 1;
                try {
                    rows.add(readRow(csv, line, mode, codes));
                } catch (BadRequestException | ConflictException
                    | NotFoundException ex) {
                    issues.add(new RowIssue(line, "order", ex.getMessage()));
                } catch (IllegalArgumentException ex) {
                    issues.add(new RowIssue(line, "format",
                        "Некорректное количество или статус"));
                }
            }
        } catch (IOException | UncheckedIOException ex) {
            throw new BadRequestException(
                "Не удалось прочитать CSV в кодировке UTF-8");
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Некорректный заголовок CSV");
        }
        return new PreparedImport(count, rows, issues);
    }

    private PreparedRow readRow(CSVRecord csv, long line, ImportMode mode,
        Set<String> codes) {
        if (!csv.isConsistent())
            throw new BadRequestException(
                "Число значений отличается от числа столбцов");
        String code = csv.get("external_id");
        if (!codes.add(code))
            throw new BadRequestException("Номер заказа повторяется в файле");
        StoreOrder existing = orders.findByExternalId(code).orElse(null);
        if (existing != null && mode == ImportMode.CREATE_ONLY) {
            throw new BadRequestException(
                "Заказ с таким номером уже существует");
        }
        String email =
            unescape(csv.get("customer_email")).toLowerCase(Locale.ROOT);
        long customerId = customers.findByEmail(email).orElseThrow(
            () -> new BadRequestException("Покупатель с таким email не найден"))
            .getId();
        String urgent = csv.get("urgent").toLowerCase(Locale.ROOT);
        if (!Set.of("true", "false").contains(urgent))
            throw new BadRequestException("urgent: только true или false");
        String tags = unescape(csv.get("tags"));
        var request = new OrderRequest(code, customerId,
            unescape(csv.get("note")), Boolean.parseBoolean(urgent),
            tags.isEmpty()
                ? Set.of()
                : new LinkedHashSet<>(Arrays.asList(tags.split("\\|", -1))),
            parseItems(csv.get("items")),
            csv.get("status").isBlank()
                ? null
                : OrderStatus.valueOf(csv.get("status")),
            existing == null ? null : existing.getVersion());
        var errors = validator.validate(request);
        if (!errors.isEmpty()) {
            String message = errors.stream().map(
                error -> error.getPropertyPath() + ": " + error.getMessage())
                .sorted().collect(Collectors.joining(", "));
            throw new BadRequestException(message);
        }
        UUID id = existing == null ? null : existing.getPublicId();
        orderService.validateImport(request, id);
        return new PreparedRow(line, id, request);
    }

    private List<ItemRequest> parseItems(String value) {
        var positions = new LinkedHashMap<String, Integer>();
        for (String item : value.split("\\|", -1)) {
            var fields = item.split(":", -1);
            if (fields.length != 2 || fields[0].isBlank())
                throw new BadRequestException("Формат позиции: SKU:количество");
            if (positions.putIfAbsent(fields[0],
                Integer.parseInt(fields[1])) != null) {
                throw new BadRequestException("Товар повторяется в заказе");
            }
        }
        if (positions.size() > 100)
            throw new BadRequestException(
                "В заказе допускается не более 100 позиций");
        var catalog = products.findBySkuIn(positions.keySet()).stream()
            .collect(Collectors.toMap(Product::getSku, Function.identity()));
        if (catalog.size() != positions.size())
            throw new BadRequestException("Товар с указанным SKU не найден");
        return positions.entrySet().stream().map(
            e -> new ItemRequest(catalog.get(e.getKey()).getId(), e.getValue()))
            .toList();
    }

    private static BufferedReader utf8(InputStream input) throws IOException {
        var reader = new BufferedReader(
            new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()));
        reader.mark(1);
        if (reader.read() != 0xfeff)
            reader.reset();
        return reader;
    }

    // Экранируем формулы Excel и сохраняем исходный апостроф при повторном
    // импорте.
    private static String escape(String text) {
        if (text == null || text.isEmpty())
            return "";
        return "'=+-@".indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
    }

    private static String unescape(String text) {
        return text.length() > 1 && text.charAt(0) == '\''
            && "'=+-@".indexOf(text.charAt(1)) >= 0 ? text.substring(1) : text;
    }

    private record PreparedImport(int total, List<PreparedRow> rows,
        List<RowIssue> issues) {
    }

    private record PreparedRow(long line, UUID id, OrderRequest request) {
    }
}
