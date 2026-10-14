package ru.mirea.store.fx.util;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import ru.mirea.store.exception.*;
import ru.mirea.store.model.OrderStatus;

public final class Ui {
  private static final ExecutorService WORK = Executors.newSingleThreadExecutor(
    runnable -> {
      Thread thread = new Thread(runnable, "store-database");
      thread.setDaemon(true);
      return thread;
    }
  );

  private Ui() {}

  public static Parent load(String name, Object controller) throws IOException {
    var loader = new FXMLLoader(Ui.class.getResource("/ru/mirea/store/fx/"
      + name));
    loader.setController(controller);
    return loader.load();
  }

  public static void style(Scene scene) {
    scene.getStylesheets().add(Ui.class.getResource(
      "/ru/mirea/store/fx/store.css").toExternalForm());
  }

  public static void dialog(
    Window owner, String title, String fxml, Object controller
  ) {
    try {
      Stage stage = new Stage();
      stage.initOwner(owner);
      stage.initModality(Modality.WINDOW_MODAL);
      stage.setTitle(title);
      Scene scene = new Scene(load(fxml, controller));
      style(scene);
      stage.setScene(scene);
      stage.showAndWait();
    } catch (IOException exception) {
      error(owner, exception);
    }
  }

  public static <T> void run(
    Window owner, Callable<T> action, Consumer<T> success, Runnable finish
  ) {
    Task<T> task = new Task<>() {
      @Override
      protected T call() throws Exception {
        return action.call();
      }
    };
    task.setOnSucceeded(event -> {
      finish.run();
      try {
        success.accept(task.getValue());
      } catch (RuntimeException exception) {
        error(owner, exception);
      }
    });
    task.setOnFailed(event -> {
      finish.run();
      error(owner, task.getException());
    });
    WORK.submit(task);
  }

  public static void error(Window owner, Throwable exception) {
    System.getLogger(Ui.class.getName()).log(
      System.Logger.Level.ERROR, "Операция не выполнена", exception);
    String message;
    if (exception instanceof BusinessRuleException
      || exception instanceof EntityNotFoundException
      || exception instanceof IllegalArgumentException
      || exception instanceof IllegalStateException) {
      message = exception.getMessage();
    } else if (exception instanceof DataAccessException) {
      message = "Ошибка базы данных. Проверьте подключение, уникальность "
        + "email/SKU и наличие связанных записей.";
    } else if (exception instanceof ExportException) {
      message = "Не удалось сохранить Excel-файл. Проверьте путь и права "
        + "доступа; закройте файл, если он открыт в Excel.";
    } else {
      message = "Не удалось выполнить действие. Проверьте подключение "
        + "к PostgreSQL и настройки приложения.";
    }
    alert(owner, Alert.AlertType.ERROR, "Ошибка", message);
  }

  public static boolean confirm(Window owner, String message) {
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
      ButtonType.OK, ButtonType.CANCEL);
    alert.initOwner(owner);
    alert.setTitle("Подтверждение");
    alert.setHeaderText("Удаление заказа");
    return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
  }

  public static void alert(
    Window owner, Alert.AlertType type, String title, String message
  ) {
    Alert alert = new Alert(type);
    alert.initOwner(owner);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }

  public static <T> void column(
    TableView<T> table, String title, Function<T, String> value
  ) {
    TableColumn<T, String> column = new TableColumn<>(title);
    column.setCellValueFactory(cell ->
      new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    column.setSortable(false);
    column.setPrefWidth(150);
    table.getColumns().add(column);
  }

  public static <T> StringConverter<T> converter(Function<T, String> label) {
    return new StringConverter<>() {
      @Override
      public String toString(T value) {
        return value == null ? "" : label.apply(value);
      }

      @Override
      public T fromString(String value) {
        throw new UnsupportedOperationException("Выберите объект из списка.");
      }
    };
  }

  public static String status(OrderStatus status) {
    return switch (status) {
      case NEW -> "Новый";
      case PAID -> "Оплачен";
      case SHIPPED -> "Отправлен";
      case DELIVERED -> "Доставлен";
      case CANCELLED -> "Отменён";
    };
  }

  public static String date(LocalDateTime value) {
    return value == null ? "—" : value.format(
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
  }

  public static void close(javafx.scene.Node node) {
    ((Stage) node.getScene().getWindow()).close();
  }

  public static void shutdown() {
    WORK.shutdownNow();
  }
}
