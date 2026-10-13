package ru.mirea.store.fx.controller;

import java.time.LocalDate;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import ru.mirea.store.export.StoreExportData;
import ru.mirea.store.fx.StoreServices;
import ru.mirea.store.fx.util.Ui;
import ru.mirea.store.model.*;

public final class MainController {
  private final StoreServices services;
  private final BooleanProperty busy = new SimpleBooleanProperty();
  private List<CustomerOrderDetails> allOrders = List.of();
  private List<Customer> customers = List.of();
  private List<Product> products = List.of();

  @FXML private BorderPane root;
  @FXML private TableView<CustomerOrderDetails> ordersTable;
  @FXML private TableView<Customer> customersTable;
  @FXML private TableView<Product> productsTable;
  @FXML private TextField search;
  @FXML private ComboBox<String> searchField;
  @FXML private ComboBox<String> status;
  @FXML private DatePicker fromDate;
  @FXML private DatePicker toDate;
  @FXML private ComboBox<String> sortField;
  @FXML private ComboBox<String> direction;
  @FXML private ComboBox<OrderStatus> targetStatus;
  @FXML private Button editButton;
  @FXML private Button deleteButton;
  @FXML private Button detailsButton;
  @FXML private Button statusButton;
  @FXML private Label count;

  public MainController(StoreServices services) {
    this.services = services;
  }

  @FXML
  private void initialize() {
    root.disableProperty().bind(busy);
    searchField.getItems().setAll("ФИО покупателя", "Название товара");
    searchField.getSelectionModel().selectFirst();
    status.getItems().add("Все статусы");
    for (OrderStatus value : OrderStatus.values()) {
      status.getItems().add(Ui.status(value));
    }
    status.getSelectionModel().selectFirst();
    sortField.getItems().setAll("Дата создания", "Общая сумма");
    sortField.getSelectionModel().selectFirst();
    direction.getItems().setAll("По возрастанию", "По убыванию");
    direction.getSelectionModel().select(1);
    targetStatus.setConverter(Ui.converter(Ui::status));
    Ui.column(ordersTable, "№ заказа", v -> "" + v.getOrder().getId());
    Ui.column(ordersTable, "Покупатель", CustomerOrderDetails::getCustomerName);
    Ui.column(ordersTable, "Статус", v -> Ui.status(v.getOrder().getStatus()));
    Ui.column(ordersTable, "Создан", v -> Ui.date(v.getOrder().getCreatedAt()));
    Ui.column(ordersTable, "Сумма", v -> v.getTotal().toPlainString());
    Ui.column(ordersTable, "Примечание", v -> v.getOrder().getNote());
    Ui.column(customersTable, "ФИО", Customer::getFullName);
    Ui.column(customersTable, "Email", Customer::getEmail);
    Ui.column(customersTable, "Телефон", Customer::getPhone);
    Ui.column(customersTable, "Активен", v -> v.isActive() ? "Да" : "Нет");
    Ui.column(customersTable, "Регистрация", v -> Ui.date(v.getRegisteredAt()));
    Ui.column(productsTable, "SKU", Product::getSku);
    Ui.column(productsTable, "Название", Product::getName);
    Ui.column(productsTable, "Цена", v -> v.getPrice().toPlainString());
    Ui.column(productsTable, "Остаток", v -> "" + v.getStockQuantity());
    Ui.column(productsTable, "Активен", v -> v.isActive() ? "Да" : "Нет");
    ordersTable.getSelectionModel().selectedItemProperty().addListener(
      (observable, before, after) -> selectionChanged());
    selectionChanged();
  }

  private Window owner() {
    return root.getScene().getWindow();
  }

  @FXML
  public void refresh() {
    busy.set(true);
    Ui.run(owner(), () -> new Snapshot(services.orders().getAll(),
      services.customers().getAll(), services.products().getAll()), value -> {
        allOrders = value.orders();
        customers = value.customers();
        products = value.products();
        customersTable.setItems(FXCollections.observableArrayList(customers));
        productsTable.setItems(FXCollections.observableArrayList(products));
        applyFilters();
      }, () -> busy.set(false));
  }

  @FXML
  private void applyFilters() {
    try {
      int statusIndex = status.getSelectionModel().getSelectedIndex() - 1;
      OrderStatus selectedStatus = statusIndex < 0 ? null
        : OrderStatus.values()[statusIndex];
      LocalDate from = readDate(fromDate);
      LocalDate to = readDate(toDate);
      var selected = services.query().select(allOrders, search.getText(),
        searchField.getSelectionModel().getSelectedIndex() == 1,
        selectedStatus, from, to,
        sortField.getSelectionModel().getSelectedIndex() == 0
          ? OrderSortField.CREATED_AT : OrderSortField.TOTAL_AMOUNT,
        direction.getSelectionModel().getSelectedIndex() == 0
          ? SortDirection.ASCENDING : SortDirection.DESCENDING);
      ordersTable.setItems(FXCollections.observableArrayList(selected));
      count.setText("Показано заказов: " + selected.size());
    } catch (RuntimeException exception) {
      Ui.error(owner(), exception);
    }
  }

  private static LocalDate readDate(DatePicker picker) {
    String text = picker.getEditor().getText().trim();
    if (text.isEmpty()) {
      picker.setValue(null);
      return null;
    }
    try {
      LocalDate value = picker.getConverter().fromString(text);
      picker.setValue(value);
      return value;
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Введите корректную дату.");
    }
  }

  @FXML
  private void resetFilters() {
    search.clear();
    status.getSelectionModel().selectFirst();
    fromDate.setValue(null);
    fromDate.getEditor().clear();
    toDate.setValue(null);
    toDate.getEditor().clear();
    applyFilters();
  }

  private void selectionChanged() {
    var selected = ordersTable.getSelectionModel().getSelectedItem();
    detailsButton.setDisable(selected == null);
    editButton.setDisable(selected == null
      || selected.getOrder().getStatus() != OrderStatus.NEW);
    deleteButton.setDisable(selected == null
      || (selected.getOrder().getStatus() != OrderStatus.NEW
        && selected.getOrder().getStatus() != OrderStatus.CANCELLED));
    targetStatus.getItems().clear();
    if (selected != null) {
      for (OrderStatus value : OrderStatus.values()) {
        if (selected.getOrder().getStatus().canTransitionTo(value)) {
          targetStatus.getItems().add(value);
        }
      }
    }
    targetStatus.getSelectionModel().selectFirst();
    statusButton.setDisable(targetStatus.getItems().isEmpty());
  }

  @FXML
  private void addOrder() {
    editOrder(null);
  }

  @FXML
  private void editOrder() {
    var selected = ordersTable.getSelectionModel().getSelectedItem();
    if (selected != null) editOrder(selected);
  }

  private void editOrder(CustomerOrderDetails value) {
    Ui.dialog(owner(), value == null ? "Новый заказ" : "Редактирование заказа",
      "order-form.fxml", new OrderFormController(
        services, customers, products, value, this::refresh));
  }

  @FXML
  private void details() {
    var selected = ordersTable.getSelectionModel().getSelectedItem();
    if (selected == null) return;
    busy.set(true);
    long id = selected.getOrder().getId();
    Ui.run(owner(), () -> {
      var current = services.orders().getById(id);
      var customer = services.customers().getById(
        current.getOrder().getCustomerId());
      return new OrderDetailsController(current, customer);
    }, controller -> Ui.dialog(owner(), "Сведения о заказе",
      "order-details.fxml", controller), () -> busy.set(false));
  }

  @FXML
  private void deleteOrder() {
    var selected = ordersTable.getSelectionModel().getSelectedItem();
    if (selected == null || !Ui.confirm(owner(), "Удалить заказ №"
      + selected.getOrder().getId() + " и его позиции?")) return;
    busy.set(true);
    Ui.run(owner(), () -> {
      services.orders().delete(selected.getOrder().getId());
      return true;
    }, ignored -> refresh(), () -> busy.set(false));
  }

  @FXML
  private void changeStatus() {
    var selected = ordersTable.getSelectionModel().getSelectedItem();
    OrderStatus target = targetStatus.getValue();
    if (selected == null || target == null) return;
    busy.set(true);
    Ui.run(owner(), () -> services.orders().changeStatus(
      selected.getOrder().getId(), target), ignored -> refresh(),
      () -> busy.set(false));
  }

  @FXML
  private void addCustomer() {
    Ui.dialog(owner(), "Добавление покупателя", "catalog-form.fxml",
      new CatalogFormController(services, false, this::refresh));
  }

  @FXML
  private void addProduct() {
    Ui.dialog(owner(), "Добавление товара", "catalog-form.fxml",
      new CatalogFormController(services, true, this::refresh));
  }

  @FXML
  private void statistics() {
    busy.set(true);
    Ui.run(owner(), () -> services.statistics().getStatistics(),
      value -> Ui.dialog(owner(), "Статистика всей базы магазина",
        "statistics.fxml", new StatisticsController(value)),
      () -> busy.set(false));
  }

  @FXML
  private void export() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Сохранить текущую выборку заказов");
    chooser.setInitialFileName("orders.xlsx");
    chooser.getExtensionFilters().add(
      new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));
    var file = chooser.showSaveDialog(owner());
    if (file == null) return;
    List<CustomerOrderDetails> selected = List.copyOf(ordersTable.getItems());
    busy.set(true);
    Ui.run(owner(), () -> services.exporter().exportToFile(new StoreExportData(
      customers, products, selected, services.statistics().getStatistics()),
      file.toPath()), path -> Ui.alert(owner(), Alert.AlertType.INFORMATION,
        "Экспорт завершён", "Файл сохранён: " + path),
      () -> busy.set(false));
  }

  private record Snapshot(
    List<CustomerOrderDetails> orders,
    List<Customer> customers,
    List<Product> products
  ) {}
}
