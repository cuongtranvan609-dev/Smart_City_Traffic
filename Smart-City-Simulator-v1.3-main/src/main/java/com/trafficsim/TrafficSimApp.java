package com.trafficsim;

import com.trafficsim.view.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Điểm khởi động ứng dụng mô phỏng giao thông đô thị.
 */
public class TrafficSimApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainWindow mainWindow = new MainWindow(primaryStage);
        mainWindow.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
