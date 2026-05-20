package com.trafficsim.view.ui;

import com.trafficsim.controller.TrafficController;
import com.trafficsim.model.SimScene;
import com.trafficsim.model.TrafficLight;
import com.trafficsim.model.intersection.Intersection;
import com.trafficsim.service.SceneBuilder;
import com.trafficsim.service.SoundService;
import com.trafficsim.view.renderer.BasicRenderer;
import com.trafficsim.view.renderer.GraphicRenderer;
import com.trafficsim.view.renderer.Renderer3D;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

public class ControlPanel extends ScrollPane {

    private final SimulationCanvas simCanvas;
    private final SoundService     soundService;
    private TrafficController      controller;
    private Label                  statsLabel;
    private SpeedControlPanel      speedPanel;
    private VBox                   content;

    public ControlPanel(SimulationCanvas simCanvas, SoundService soundService) {
        this.simCanvas   = simCanvas;
        this.soundService = soundService;
        setFitToWidth(true);
        setPrefWidth(300);
        setStyle("-fx-background-color:#13131f; -fx-background:#13131f;");
        buildUI();
    }

    private void buildUI() {
        content = new VBox(8);
        content.setPadding(new Insets(12));
        content.setBackground(new Background(new BackgroundFill(
            Color.rgb(19,19,31), new CornerRadii(0), Insets.EMPTY)));

        content.getChildren().addAll(
            sectionLabel("🗺 CẢNH MÔ PHỎNG"),
            buildSceneSelector(),
            sep(),
            sectionLabel("🚗 LƯU LƯỢNG"),
            buildDensitySelector(),
            sep(),
            sectionLabel("🎮 ĐIỀU KHIỂN"),
            buildControlMode(),
            sep(),
            sectionLabel("🔍 ZOOM"),
            buildZoomControls(),
            sep(),
            sectionLabel("⚡ TỐC ĐỘ"),
            buildSpeedSection(),
            sep(),
            sectionLabel("🖼 HIỂN THỊ"),
            buildRenderMode(),
            sep(),
            sectionLabel("🔦 LOẠI ĐÈN"),
            buildLightType(),
            sep(),
            sectionLabel("🔊 ÂM THANH"),
            buildSoundToggle(),
            sep(),
            sectionLabel("➕ THÊM XE"),
            buildAddVehicle(),
            sep(),
            sectionLabel("📊 THỐNG KÊ"),
            buildStats()
        );

        setContent(content);
        loadScene(SimScene.SceneType.FOUR_WAY);
    }

    // ===== SCENE SELECTOR =====
    private VBox buildSceneSelector() {
        String[] names = {"Ngã Ba","Ngã Tư","Ngã Năm","Mạng lưới"};
        SimScene.SceneType[] types = {
            SimScene.SceneType.THREE_WAY, SimScene.SceneType.FOUR_WAY,
            SimScene.SceneType.FIVE_WAY,  SimScene.SceneType.NETWORK
        };
        ToggleGroup tg = new ToggleGroup();
        RadioButton[] rbs = new RadioButton[4];
        for (int i=0;i<4;i++) {
            final int idx=i;
            rbs[i] = styledRadio(names[i], tg);
            rbs[i].setOnAction(e -> loadScene(types[idx]));
        }
        rbs[1].setSelected(true);
        return new VBox(4,
            new HBox(6, rbs[0], rbs[1]),
            new HBox(6, rbs[2], rbs[3]));
    }

    private CheckBox toggleSpawn;
    private CheckBox autoPolice;

    // ===== DENSITY =====
    private VBox buildDensitySelector() {
        ToggleGroup tg = new ToggleGroup();
        RadioButton low=styledRadio("Ít xe",tg), mid=styledRadio("Vừa",tg), high=styledRadio("Đông",tg);
        mid.setSelected(true);
        low .setOnAction(e -> { if(controller!=null) controller.setTrafficDensity(TrafficController.TrafficDensity.LOW); });
        mid .setOnAction(e -> { if(controller!=null) controller.setTrafficDensity(TrafficController.TrafficDensity.MEDIUM); });
        high.setOnAction(e -> { if(controller!=null) controller.setTrafficDensity(TrafficController.TrafficDensity.HIGH); });
        
        toggleSpawn = new CheckBox("Dừng Spawn Xe");
        toggleSpawn.setTextFill(Color.LIGHTGRAY);
        toggleSpawn.setOnAction(e -> { if(controller!=null) controller.setSpawnEnabled(!toggleSpawn.isSelected()); });
        
        Button clearBtn = btn("🗑 Xóa tất cả xe");
        clearBtn.setOnAction(e -> { if(controller!=null) controller.clearAllVehicles(); });
        
        autoPolice = new CheckBox("Cảnh sát tự động");
        autoPolice.setTextFill(Color.LIGHTGRAY);
        autoPolice.setSelected(true);
        autoPolice.setOnAction(e -> { if(controller!=null) controller.setAutoPoliceEnabled(autoPolice.isSelected()); });
        
        Button policeBtn = btn("🚨 Điều tiết Cảnh sát");
        policeBtn.setOnAction(e -> { if(controller!=null) controller.triggerManualPolice(); });
        
        return new VBox(8, 
            new HBox(6, low, mid, high), 
            new HBox(12, toggleSpawn, clearBtn),
            new HBox(12, autoPolice, policeBtn)
        );
    }

    // ===== CONTROL MODE =====
    private VBox buildControlMode() {
        ToggleGroup tg = new ToggleGroup();
        RadioButton auto=styledRadio("Tự động",tg), manual=styledRadio("Thủ công (click đèn)",tg);
        auto.setSelected(true);
        auto  .setOnAction(e -> simCanvas.setManualLightMode(false));
        manual.setOnAction(e -> simCanvas.setManualLightMode(true));

        Button pauseBtn = btn("⏸ Tạm dừng");
        pauseBtn.setOnAction(e -> {
            boolean nowPaused = !simCanvas.isPaused();
            simCanvas.setPaused(nowPaused);
            pauseBtn.setText(nowPaused ? "▶ Tiếp tục" : "⏸ Tạm dừng");
        });
        return new VBox(4, new HBox(6,auto,manual), pauseBtn);
    }

    // ===== ZOOM =====
    private HBox buildZoomControls() {
        Button zIn  = btn("🔍+");
        Button zOut = btn("🔍−");
        Button zRst = btn("↺ Reset");
        Label  zLbl = new Label("100%");
        zLbl.setTextFill(Color.LIGHTGREEN); zLbl.setFont(Font.font(11));
        zIn .setOnAction(e -> { simCanvas.zoomIn();  updateZoomLabel(zLbl); });
        zOut.setOnAction(e -> { simCanvas.zoomOut(); updateZoomLabel(zLbl); });
        zRst.setOnAction(e -> { simCanvas.resetZoom(); updateZoomLabel(zLbl); });
        Timeline updater = new Timeline(new KeyFrame(Duration.millis(200), e -> updateZoomLabel(zLbl)));
        updater.setCycleCount(Timeline.INDEFINITE); updater.play();
        return new HBox(4, zOut, zIn, zRst, zLbl);
    }
    private void updateZoomLabel(Label l) {
        l.setText(String.format("%.0f%%", simCanvas.getZoom()*100));
    }

    // ===== SPEED =====
    private VBox buildSpeedSection() {
        speedPanel = new SpeedControlPanel(
            controller != null ? controller.getScene() : new SimScene(SimScene.SceneType.FOUR_WAY));
        return new VBox(4, speedPanel);
    }

    // ===== RENDER MODE =====
    private HBox buildRenderMode() {
        ToggleGroup tg = new ToggleGroup();
        RadioButton basic=styledRadio("Basic",tg), gfx=styledRadio("Đồ họa",tg), r3d=styledRadio("3D",tg);
        basic.setSelected(true);
        basic.setOnAction(e -> simCanvas.setRenderer(new BasicRenderer()));
        gfx  .setOnAction(e -> simCanvas.setRenderer(new GraphicRenderer()));
        r3d  .setOnAction(e -> simCanvas.setRenderer(new Renderer3D()));
        return new HBox(6, basic, gfx, r3d);
    }

    // ===== LIGHT TYPE =====
    private VBox buildLightType() {
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().addAll("Luôn đếm ngược","Không đếm ngược","Đếm khi ≤10s");
        cb.setValue("Đếm khi ≤10s");
        cb.setStyle("-fx-background-color:#2a2a40;-fx-text-fill:white;");
        cb.setOnAction(e -> {
            if (controller==null) return;
            TrafficLight.DisplayType dt = switch(cb.getSelectionModel().getSelectedIndex()) {
                case 0 -> TrafficLight.DisplayType.ALWAYS_COUNTDOWN;
                case 1 -> TrafficLight.DisplayType.NO_COUNTDOWN;
                default-> TrafficLight.DisplayType.LATE_COUNTDOWN;
            };
            controller.getScene().getIntersections().forEach(i ->
                i.getTrafficLights().forEach(tl -> tl.setDisplayType(dt)));
        });
        return new VBox(4, cb);
    }

    // ===== SOUND =====
    private HBox buildSoundToggle() {
        CheckBox cb = new CheckBox("Bật âm thanh");
        cb.setSelected(true); cb.setTextFill(Color.LIGHTGRAY);
        cb.setOnAction(e -> soundService.setEnabled(cb.isSelected()));
        return new HBox(cb);
    }

    // ===== ADD VEHICLE =====
    private VBox buildAddVehicle() {
        Button addBtn = btn("➕ Thêm xe tùy chỉnh...");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setOnAction(e -> {
            if (controller == null) return;
            AddVehicleDialog dlg = new AddVehicleDialog(controller.getScene());
            dlg.showAndWait().ifPresent(v -> {
                if (v != null) controller.getScene().addVehicle(v);
            });
        });
        Label hint = new Label("Hoặc cuộn chuột để zoom,\nchuột phải để kéo màn hình,\nclick chuột trái vào xe để xóa xe");
        hint.setTextFill(Color.rgb(120,120,140)); hint.setFont(Font.font(10));
        return new VBox(4, addBtn, hint);
    }

    // ===== STATS =====
    private VBox buildStats() {
        statsLabel = new Label("Đang tải...");
        statsLabel.setTextFill(Color.LIGHTGREEN); statsLabel.setFont(Font.font("Monospaced",10));
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(500), e -> updateStats()));
        tl.setCycleCount(Timeline.INDEFINITE); tl.play();
        return new VBox(4, statsLabel);
    }

    private void updateStats() {
        if (controller==null) return;
        var scene = controller.getScene();
        long pri = scene.getVehicles().stream().filter(v->v.isPriorityVehicle()).count();
        long yld = scene.getVehicles().stream().filter(v->v.isYieldingForPriority()).count();
        statsLabel.setText(String.format(
            "Tổng xe:  %d\nƯu tiên:  %d\nNhường:   %d\nZoom:     %.0f%%\nCảnh:     %s",
            scene.getVehicleCount(), pri, yld,
            simCanvas.getZoom()*100,
            scene.getSceneType().name()));
    }

    private void loadScene(SimScene.SceneType type) {
        SimScene scene = switch(type) {
            case THREE_WAY -> SceneBuilder.buildThreeWay();
            case FOUR_WAY  -> SceneBuilder.buildFourWay();
            case FIVE_WAY  -> SceneBuilder.buildFiveWay();
            case NETWORK   -> SceneBuilder.buildNetwork();
        };
        controller = new TrafficController(scene, soundService);
        if (toggleSpawn != null) {
            controller.setSpawnEnabled(!toggleSpawn.isSelected());
        }
        if (autoPolice != null) {
            controller.setAutoPoliceEnabled(autoPolice.isSelected());
        }
        simCanvas.setController(controller);
        simCanvas.resetZoom();
        // Refresh speed panel with new scene
        if (speedPanel != null) {
            content.getChildren().remove(speedPanel);
            speedPanel = new SpeedControlPanel(scene);
        }
    }

    // ===== Helpers =====
    private Label sectionLabel(String t) {
        Label l=new Label(t); l.setFont(Font.font("Arial",FontWeight.BOLD,11));
        l.setTextFill(Color.LIGHTYELLOW); return l;
    }
    private RadioButton styledRadio(String t, ToggleGroup tg) {
        RadioButton rb=new RadioButton(t); rb.setToggleGroup(tg);
        rb.setTextFill(Color.LIGHTGRAY); rb.setFont(Font.font(11)); return rb;
    }
    private Button btn(String t) {
        Button b=new Button(t);
        b.setStyle("-fx-background-color:#2a3a5a;-fx-text-fill:white;-fx-font-size:11;");
        return b;
    }
    private Separator sep() { Separator s=new Separator(); s.setStyle("-fx-background-color:#333355;"); return s; }
}
