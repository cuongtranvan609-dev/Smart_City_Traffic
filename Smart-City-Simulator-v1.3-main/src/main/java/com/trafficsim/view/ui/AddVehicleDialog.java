package com.trafficsim.view.ui;

import com.trafficsim.model.Direction;
import com.trafficsim.model.driver.*;
import com.trafficsim.model.vehicle.*;
import com.trafficsim.model.SimScene;
import com.trafficsim.model.road.Lane;
import com.trafficsim.model.road.Road;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Dialog to add a custom vehicle with configurable behavior and speed.
 */
public class AddVehicleDialog extends Dialog<Vehicle> {

    public AddVehicleDialog(SimScene scene) {
        setTitle("Thêm phương tiện");
        setHeaderText("Cấu hình phương tiện mới");

        // Vehicle type
        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("Ô tô", "Xe máy", "Xe đạp", "Xe buýt", "Cứu thương", "Cứu hỏa");
        typeBox.setValue("Ô tô");

        // Behavior
        ComboBox<String> behavBox = new ComboBox<>();
        behavBox.getItems().addAll("Bình thường", "Hung hăng", "Thận trọng", "Khẩn cấp");
        behavBox.setValue("Bình thường");

        // Speed multiplier
        Slider speedSlider = new Slider(0.3, 3.0, 1.0);
        speedSlider.setShowTickLabels(true); speedSlider.setShowTickMarks(true);
        speedSlider.setMajorTickUnit(0.5);
        Label speedLabel = new Label("×1.00");
        speedSlider.valueProperty().addListener((o,ov,nv) ->
            speedLabel.setText(String.format("×%.2f", nv.doubleValue())));

        // Direction
        ComboBox<String> dirBox = new ComboBox<>();
        dirBox.getItems().addAll("EAST →", "WEST ←", "SOUTH ↓", "NORTH ↑");
        dirBox.setValue("EAST →");

        // Spawn point (use first suitable lane)
        Label spawnInfo = new Label("Sẽ spawn tại đầu làn phù hợp");
        spawnInfo.setStyle("-fx-text-fill:#888; -fx-font-size:10;");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(14));
        grid.addRow(0, new Label("Loại xe:"),        typeBox);
        grid.addRow(1, new Label("Hành vi lái:"),    behavBox);
        grid.addRow(2, new Label("Tốc độ:"),         new HBox(6, speedSlider, speedLabel));
        grid.addRow(3, new Label("Hướng đi:"),       dirBox);
        grid.addRow(4, spawnInfo);

        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setStyle("-fx-background-color:#1e1e2e;");

        setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            DrivingBehavior behavior = switch (behavBox.getValue()) {
                case "Hung hăng"  -> new AggressiveDriver();
                case "Thận trọng" -> new CautiousDriver();
                case "Khẩn cấp"  -> new EmergencyDriver();
                default           -> new NormalDriver();
            };
            Direction dir = switch (dirBox.getValue()) {
                case "WEST ←" -> Direction.WEST;
                case "SOUTH ↓"-> Direction.SOUTH;
                case "NORTH ↑"-> Direction.NORTH;
                default       -> Direction.EAST;
            };

            // Find a lane for this direction
            Lane targetLane = null;
            boolean isPriorityVal = "Cứu thương".equals(typeBox.getValue()) || "Cứu hỏa".equals(typeBox.getValue());
            outer:
            for (Road road : scene.getRoads()) {
                for (Lane lane : road.getLanesForDirection(dir)) {
                    if (isPriorityVal && lane.getLaneIndex() != 0) continue;
                    if (lane.hasSpaceFor(createDummy(0,0,dir))) {
                        targetLane = lane; break outer;
                    }
                }
            }
            if (targetLane == null) return null;

            double x = targetLane.getStartX(), y = targetLane.getStartY();
            Vehicle v = switch (typeBox.getValue()) {
                case "Xe máy"    -> new Motorbike(x, y, dir, behavior);
                case "Xe đạp"    -> new Bicycle(x, y, dir, behavior);
                case "Xe buýt"   -> new Bus(x, y, dir, behavior);
                case "Cứu thương"-> new Ambulance(x, y, dir);
                case "Cứu hỏa"  -> new FireTruck(x, y, dir);
                default          -> new Car(x, y, dir, behavior);
            };
            v.setSpeedMultiplier(speedSlider.getValue());
            targetLane.addVehicle(v);
            return v;
        });
    }

    private Vehicle createDummy(double x, double y, Direction dir) {
        return new Car(x, y, dir);
    }
}
