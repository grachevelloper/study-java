package ru.mirea.store.fx.controller;

import java.math.BigDecimal;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import ru.mirea.store.fx.StoreServices;
import ru.mirea.store.fx.util.Ui;

public final class CatalogFormController {
  private final StoreServices services;
  private final boolean product;
  private final Runnable saved;
  @FXML private VBox root;
  @FXML private Label title;
  @FXML private Label firstLabel;
  @FXML private Label secondLabel;
  @FXML private Label thirdLabel;
  @FXML private TextField first;
  @FXML private TextField second;
  @FXML private TextField third;
  @FXML private VBox stockBox;
  @FXML private TextField stock;

  public CatalogFormController(
    StoreServices services, boolean product, Runnable saved
  ) {
    this.services = services;
    this.product = product;
    this.saved = saved;
  }

  @FXML
  private void initialize() {
    title.setText(product ? "Новый товар" : "Новый покупатель");
    firstLabel.setText(product ? "SKU *" : "ФИО *");
    secondLabel.setText(product ? "Название *" : "Email *");
    thirdLabel.setText(product ? "Цена *" : "Телефон");
    stockBox.setVisible(product);
    stockBox.setManaged(product);
  }

  @FXML
  private void save() {
    try {
      String a = first.getText();
      String b = second.getText();
      String c = third.getText();
      BigDecimal price;
      int quantity;
      if (product) {
        try {
          price = new BigDecimal(c.trim().replace(',', '.'));
          quantity = Integer.parseInt(stock.getText().trim());
        } catch (NumberFormatException exception) {
          throw new IllegalArgumentException(
            "Введите числовую цену и целый остаток.");
        }
      } else {
        price = null;
        quantity = 0;
      }
      root.setDisable(true);
      Ui.run(root.getScene().getWindow(), () -> {
        if (product) services.products().create(a, b, price, quantity);
        else services.customers().create(a, b, c);
        return true;
      }, ignored -> {
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
}
