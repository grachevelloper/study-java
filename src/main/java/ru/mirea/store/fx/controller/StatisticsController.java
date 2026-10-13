package ru.mirea.store.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import ru.mirea.store.fx.util.Ui;
import ru.mirea.store.model.OrderStatus;
import ru.mirea.store.model.SystemStatistics;

public final class StatisticsController {
  private final SystemStatistics statistics;
  @FXML private VBox root;
  @FXML private Label customers;
  @FXML private Label products;
  @FXML private Label stock;
  @FXML private Label orders;
  @FXML private Label statuses;
  @FXML private Label revenue;
  @FXML private Label emptyStock;

  public StatisticsController(SystemStatistics statistics) {
    this.statistics = statistics;
  }

  @FXML
  private void initialize() {
    customers.setText("Покупателей: " + statistics.getCustomerCount());
    products.setText("Товаров: " + statistics.getProductCount());
    stock.setText("Единиц на складе: " + statistics.getStockUnitCount());
    orders.setText("Заказов: " + statistics.getOrderCount());
    StringBuilder text = new StringBuilder("Заказы по статусам:\n");
    for (OrderStatus status : OrderStatus.values()) {
      text.append(Ui.status(status)).append(": ").append(
        statistics.getOrdersByStatus().getOrDefault(status, 0L)).append('\n');
    }
    statuses.setText(text.toString());
    revenue.setText("Выручка доставленных заказов: "
      + statistics.getDeliveredRevenue().toPlainString());
    emptyStock.setText("Товаров без остатка: "
      + statistics.getOutOfStockProductCount());
  }

  @FXML
  private void close() {
    Ui.close(root);
  }
}
