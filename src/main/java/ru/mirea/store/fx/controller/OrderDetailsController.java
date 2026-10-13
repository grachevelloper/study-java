package ru.mirea.store.fx.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import ru.mirea.store.fx.util.Ui;
import ru.mirea.store.model.*;

public final class OrderDetailsController {
  private final CustomerOrderDetails details;
  private final Customer customer;
  @FXML private VBox root;
  @FXML private Label heading;
  @FXML private Label customerInfo;
  @FXML private Label dates;
  @FXML private Label note;
  @FXML private Label total;
  @FXML private TableView<OrderItemDetails> items;

  public OrderDetailsController(
    CustomerOrderDetails details, Customer customer
  ) {
    this.details = details;
    this.customer = customer;
  }

  @FXML
  private void initialize() {
    var order = details.getOrder();
    heading.setText("Заказ №" + order.getId() + " · "
      + Ui.status(order.getStatus()));
    customerInfo.setText(customer.getFullName() + "\n" + customer.getEmail()
      + " · " + (customer.getPhone() == null ? "" : customer.getPhone()));
    dates.setText("Создан: " + Ui.date(order.getCreatedAt())
      + "\nОплачен: " + Ui.date(order.getPaidAt())
      + "\nОтправлен: " + Ui.date(order.getShippedAt())
      + "\nДоставлен: " + Ui.date(order.getDeliveredAt())
      + "\nОтменён: " + Ui.date(order.getCancelledAt()));
    note.setText("Примечание: "
      + (order.getNote() == null ? "—" : order.getNote()));
    total.setText("Итого: " + details.getTotal().toPlainString());
    Ui.column(items, "SKU", OrderItemDetails::getProductSku);
    Ui.column(items, "Товар", OrderItemDetails::getProductName);
    Ui.column(items, "Количество", v -> "" + v.getItem().getQuantity());
    Ui.column(items, "Цена", v -> v.getItem().getUnitPrice().toPlainString());
    Ui.column(items, "Сумма", v -> v.getItem().getLineTotal().toPlainString());
    items.setItems(FXCollections.observableArrayList(details.getItems()));
  }

  @FXML
  private void close() {
    Ui.close(root);
  }
}
