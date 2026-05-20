package com.trafficsim.view.ui;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.SimScene;
import com.trafficsim.model.vehicle.Vehicle;
import com.trafficsim.model.vehicle.VehicleFactory;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Expandable panel for per-vehicle-type speed control and global speed.
 */
public class SpeedControlPanel extends VBox {

    private final SimScene scene;

    public SpeedControlPanel(SimScene scene) {
        this.scene = scene;
        setSpacing(6);
        setPadding(new Insets(6,0,6,0));
        build();
    }

    private void build() {
        getChildren().clear();

        // Global speed
        Label gLabel = new Label("Tốc độ chung:");
        gLabel.setTextFill(Color.LIGHTGRAY); gLabel.setFont(Font.font(11));
        Slider global = new Slider(0.1, 4.0, SimConfig.globalSpeedMultiplier);
        global.setShowTickLabels(true); global.setMajorTickUnit(1.0);
        Label gVal = new Label(String.format("×%.1f", global.getValue()));
        gVal.setTextFill(Color.LIGHTGREEN);
        global.valueProperty().addListener((o,ov,nv) -> {
            SimConfig.globalSpeedMultiplier = nv.doubleValue();
            gVal.setText(String.format("×%.1f", nv.doubleValue()));
        });
        getChildren().addAll(gLabel, new HBox(6, global, gVal));

        // Per-type speed sliders (Car, Moto, Bus, etc.)
        String[][] types = {{"Ô tô","Car"},{"Xe máy","Moto"},{"Xe đạp","Bike"},
                            {"Xe buýt","Bus"},{"Cứu thương","Ambu"},{"Cứu hỏa","Fire"}};
        for (String[] t : types) {
            Label lbl = new Label(t[0]+":"); lbl.setTextFill(Color.LIGHTGRAY); lbl.setFont(Font.font(10));
            Slider sl = new Slider(0.2, 3.0, 1.0);
            sl.setPrefWidth(120);
            Label vl = new Label("×1.0"); vl.setTextFill(Color.CYAN); vl.setFont(Font.font(10));
            sl.valueProperty().addListener((o,ov,nv) -> {
                vl.setText(String.format("×%.1f", nv.doubleValue()));
                // Apply to all existing vehicles of this type
                for (Vehicle v : scene.getVehicles()) {
                    if (v.getShortName().equals(t[1])) v.setSpeedMultiplier(nv.doubleValue());
                }
            });
            HBox row = new HBox(4, lbl, sl, vl);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            getChildren().add(row);
        }
    }

    public void refresh() { build(); }
}
