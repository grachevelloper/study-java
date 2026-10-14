package ru.mirea.store.fx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.mirea.store.fx.controller.MainController;
import ru.mirea.store.fx.util.Ui;

public final class StoreApplication extends Application {
  @Override
  public void start(Stage stage) {
    try {
      var controller = new MainController(StoreServices.connect());
      var scene = new Scene(Ui.load("main.fxml", controller), 1100, 730);
      Ui.style(scene);
      stage.setTitle("Интернет-магазин — КР №2");
      stage.setMinWidth(850);
      stage.setMinHeight(620);
      stage.setScene(scene);
      stage.show();
      controller.refresh();
    } catch (Exception exception) {
      Ui.error(stage, exception);
    }
  }

  @Override
  public void stop() {
    Ui.shutdown();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
