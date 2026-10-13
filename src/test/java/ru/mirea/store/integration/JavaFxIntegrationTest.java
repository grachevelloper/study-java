package ru.mirea.store.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ru.mirea.store.export.ExcelExporter;
import ru.mirea.store.fx.StoreServices;
import ru.mirea.store.fx.controller.*;
import ru.mirea.store.fx.util.Ui;
import ru.mirea.store.model.Customer;
import ru.mirea.store.model.CustomerOrderDetails;
import ru.mirea.store.model.Product;
import ru.mirea.store.repository.jdbc.*;
import ru.mirea.store.service.*;

/** Uses only the isolated test database, never the store database. */
@EnabledIfSystemProperty(named = "store.fx.tests", matches = "true")
class JavaFxIntegrationTest {
  private static StoreServices services;

  @BeforeAll
  static void prepare() throws Exception {
    TestDatabase.reset();
    var provider = TestDatabase.start();
    var customers = new JdbcCustomerRepository(provider);
    var products = new JdbcProductRepository(provider);
    var orders = new CustomerOrderService(
      new JdbcCustomerOrderRepository(provider), customers, products,
      Clock.systemDefaultZone());
    services = new StoreServices(
      new CustomerService(customers, Clock.systemDefaultZone()),
      new ProductService(products), orders,
      new StatisticsService(new JdbcStatisticsRepository(provider)),
      new OrderQueryService(orders), new ExcelExporter());
    CountDownLatch started = new CountDownLatch(1);
    Platform.startup(() -> {
      Platform.setImplicitExit(false);
      started.countDown();
    });
    assertTrue(started.await(10, TimeUnit.SECONDS));
  }

  @Test
  void loadsEveryFxmlAndCreatesAndEditsOrderThroughTheForm() throws Exception {
    var customers = services.customers().getAll();
    var products = services.products().getAll();
    long before = services.orders().getAll().size();
    CountDownLatch saved = new CountDownLatch(1);
    fx(() -> {
      var controller = new OrderFormController(services, customers, products,
        null, saved::countDown);
      Stage stage = open("order-form.fxml", controller);
      Parent root = stage.getScene().getRoot();
      combo(root, "#customer", Customer.class).setValue(
        customers.stream().filter(Customer::isActive)
          .findFirst().orElseThrow());
      combo(root, "#product", Product.class).setValue(
        products.stream().filter(Product::isActive).findFirst().orElseThrow());
      ((TextField) root.lookup("#quantity")).setText("2");
      fire(root, "Добавить / заменить");
      assertEquals(1, ((TableView<?>) root.lookup("#items")).getItems().size());
      fire(root, "Сохранить");
      return null;
    });
    assertTrue(saved.await(15, TimeUnit.SECONDS));
    var all = services.orders().getAll();
    assertEquals(before + 1, all.size());
    CustomerOrderDetails created = all.stream().max(
      java.util.Comparator.comparingLong(v -> v.getOrder().getId()))
      .orElseThrow();
    assertEquals(2, created.getItems().getFirst().getItem().getQuantity());
    CountDownLatch edited = new CountDownLatch(1);
    fx(() -> {
      Stage stage = open("order-form.fxml", new OrderFormController(
        services, customers, products, created, edited::countDown));
      Parent root = stage.getScene().getRoot();
      ((TextArea) root.lookup("#note")).setText("Изменено через JavaFX");
      fire(root, "Сохранить");
      return null;
    });
    assertTrue(edited.await(15, TimeUnit.SECONDS));
    assertEquals("Изменено через JavaFX", services.orders().getById(
      created.getOrder().getId()).getOrder().getNote());

    var stats = services.statistics().getStatistics();
    fx(() -> {
      open("catalog-form.fxml", new CatalogFormController(
        services, false, () -> {})).close();
      open("catalog-form.fxml", new CatalogFormController(
        services, true, () -> {})).close();
      open("order-details.fxml", new OrderDetailsController(created,
        services.customers().getById(created.getOrder().getCustomerId())))
        .close();
      open("statistics.fxml", new StatisticsController(stats)).close();
      return null;
    });

    Stage main = fx(() -> {
      var controller = new MainController(services);
      Stage stage = open("main.fxml", controller);
      controller.refresh();
      return stage;
    });
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (fx(() -> ((TableView<?>) main.getScene().lookup("#ordersTable"))
      .getItems().isEmpty()) && System.nanoTime() < deadline) {
      Thread.sleep(100);
    }
    fx(() -> {
      var table = (TableView<?>) main.getScene().lookup("#ordersTable");
      assertEquals(before + 1, table.getItems().size());
      table.getSelectionModel().selectFirst();
      main.getScene().getRoot().applyCss();
      main.getScene().getRoot().layout();
      var image = main.getScene().snapshot(null);
      BufferedImage png = new BufferedImage((int) image.getWidth(),
        (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < png.getHeight(); y++) {
        for (int x = 0; x < png.getWidth(); x++) {
          png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
      }
      ImageIO.write(png, "png", Path.of("target/javafx-preview.png").toFile());
      main.close();
      return null;
    });
  }

  private static Stage open(String fxml, Object controller) throws Exception {
    Stage stage = new Stage();
    Scene scene = new Scene(Ui.load(fxml, controller));
    Ui.style(scene);
    stage.setScene(scene);
    stage.show();
    return stage;
  }

  private static void fire(Parent root, String text) {
    root.lookupAll(".button").stream().filter(Button.class::isInstance)
      .map(Button.class::cast).filter(v -> text.equals(v.getText()))
      .findFirst().orElseThrow().fire();
  }

  @SuppressWarnings("unchecked")
  private static <T> ComboBox<T> combo(Parent root, String id, Class<T> type) {
    return (ComboBox<T>) root.lookup(id);
  }

  private static <T> T fx(Callable<T> action) throws Exception {
    FutureTask<T> task = new FutureTask<>(action);
    Platform.runLater(task);
    return task.get(15, TimeUnit.SECONDS);
  }
}
