package ru.mirea.store.fx.controller;

import java.math.BigDecimal;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import ru.mirea.store.fx.StoreServices;
import ru.mirea.store.fx.util.Ui;
import ru.mirea.store.model.*;
import ru.mirea.store.service.CustomerOrderService.OrderItemInput;
import ru.mirea.store.validation.InputValidator;

public final class OrderFormController {
  private final StoreServices services;
  private final List<Customer> customers;
  private final List<Product> products;
  private final CustomerOrderDetails existing;
  private final Runnable saved;
  @FXML private VBox root;
  @FXML private ComboBox<Customer> customer;
  @FXML private ComboBox<Product> product;
  @FXML private TextField quantity;
  @FXML private TextArea note;
  @FXML private TableView<Line> items;
  @FXML private Label total;

  public OrderFormController(
    StoreServices services, List<Customer> customers, List<Product> products,
    CustomerOrderDetails existing, Runnable saved
  ) {
    this.services = services;
    this.customers = customers;
    this.products = products;
    this.existing = existing;
    this.saved = saved;
  }

  @FXML
  private void initialize() {
    customer.setConverter(Ui.converter(v -> v.getFullName()
      + " (" + v.getEmail() + ")"));
    product.setConverter(Ui.converter(v -> v.getSku() + " — "
      + v.getName() + " / " + v.getPrice().toPlainString()));
    customer.setItems(FXCollections.observableArrayList(
      customers.stream().filter(Customer::isActive).toList()));
    product.setItems(FXCollections.observableArrayList(
      products.stream().filter(Product::isActive).toList()));
    Ui.column(items, "Товар", v -> v.product().getName());
    Ui.column(items, "Количество", v -> "" + v.quantity());
    Ui.column(items, "Цена позиции", v -> v.price().toPlainString());
    Ui.column(items, "Сумма", v -> v.price()
      .multiply(BigDecimal.valueOf(v.quantity())).toPlainString());
    items.setItems(FXCollections.observableArrayList());
    if (existing != null) {
      customers.stream().filter(v ->
        v.getId() == existing.getOrder().getCustomerId()).findFirst()
        .ifPresent(customer::setValue);
      note.setText(existing.getOrder().getNote());
      for (var value : existing.getItems()) {
        products.stream().filter(v -> v.getId()
          == value.getItem().getProductId()).findFirst().ifPresent(v ->
            items.getItems().add(new Line(v, value.getItem().getQuantity(),
              v.getPrice())));
      }
    }
    items.getSelectionModel().selectedItemProperty().addListener(
      (observable, before, after) -> {
        if (after != null) {
          product.setValue(after.product());
          quantity.setText("" + after.quantity());
        }
      });
    updateTotal();
  }

  @FXML
  private void putItem() {
    try {
      Product value = product.getValue();
      if (value == null) {
        throw new IllegalArgumentException("Выберите товар из списка.");
      }
      int amount;
      try {
        amount = Integer.parseInt(quantity.getText().trim());
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException(
          "Количество должно быть целым числом.");
      }
      InputValidator.quantity(amount, "Количество");
      var rows = items.getItems();
      int index = -1;
      for (int i = 0; i < rows.size(); i++) {
        if (rows.get(i).product().getId() == value.getId()) index = i;
      }
      Line replacement = new Line(value, amount, value.getPrice());
      if (index < 0) rows.add(replacement);
      else rows.set(index, replacement);
      updateTotal();
    } catch (RuntimeException exception) {
      Ui.error(root.getScene().getWindow(), exception);
    }
  }

  @FXML
  private void removeItem() {
    var selected = items.getSelectionModel().getSelectedItem();
    if (selected != null) items.getItems().remove(selected);
    updateTotal();
  }

  private void updateTotal() {
    BigDecimal value = items.getItems().stream().map(v -> v.price()
      .multiply(BigDecimal.valueOf(v.quantity())))
      .reduce(BigDecimal.ZERO, BigDecimal::add);
    total.setText("Итого: " + value.toPlainString());
  }

  @FXML
  private void save() {
    try {
      Customer value = customer.getValue();
      if (value == null) {
        throw new IllegalArgumentException("Выберите покупателя из списка.");
      }
      List<OrderItemInput> inputs = items.getItems().stream().map(v ->
        new OrderItemInput(v.product().getId(), v.quantity())).toList();
      String text = note.getText();
      root.setDisable(true);
      Ui.run(root.getScene().getWindow(), () -> existing == null
        ? services.orders().createOrder(value.getId(), inputs, text)
        : services.orders().updateNew(existing.getOrder().getId(),
          value.getId(), inputs, text), ignored -> {
            Ui.close(root);
            saved.run();
          }, () -> root.setDisable(false));
    } catch (RuntimeException exception) {
      Ui.error(root.getScene().getWindow(), exception);
    }
  }

  @FXML
  private void cancel() {
    Ui.close(root);
  }

  private record Line(Product product, int quantity, BigDecimal price) {}
}
