package com.trafficsim.view.ui;

import com.trafficsim.config.SimConfig;
import com.trafficsim.service.SoundService;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class MainWindow {
    private final Stage stage;

    public MainWindow(Stage stage) { this.stage = stage; }

    public void show() {
        SoundService      sound   = new SoundService();
        SimulationCanvas  canvas  = new SimulationCanvas();
        ControlPanel      control = new ControlPanel(canvas, sound);

        BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setRight(control);
        root.setStyle("-fx-background-color:#0d0d1a;");

        Scene scene = new Scene(root, SimConfig.CANVAS_WIDTH + 308, SimConfig.CANVAS_HEIGHT);
        scene.setFill(Color.rgb(13,13,26));

        try {
            var css = getClass().getResource("/css/app.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
        } catch (Exception ignored) {}

        stage.setTitle("🚦 Smart City Traffic Simulation");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMinWidth(900); stage.setMinHeight(600);
        stage.show();
    }
}
