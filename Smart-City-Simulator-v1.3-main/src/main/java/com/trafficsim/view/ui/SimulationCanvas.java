package com.trafficsim.view.ui;

import com.trafficsim.config.SimConfig;
import com.trafficsim.controller.TrafficController;
import com.trafficsim.model.SimScene;
import com.trafficsim.view.renderer.BasicRenderer;
import com.trafficsim.view.renderer.SceneRenderer;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;

public class SimulationCanvas extends Pane {
    private final Canvas        canvas;
    private final GraphicsContext gc;
    private TrafficController   controller;
    private SceneRenderer       renderer;
    private AnimationTimer      animTimer;
    private long                lastNano = 0;

    // Zoom & pan
    private double zoom    = SimConfig.DEFAULT_ZOOM;
    private double panX    = 0, panY = 0;
    private double dragStartX, dragStartY, dragPanX, dragPanY;
    private boolean autoFitView = true;

    private boolean manualLightMode = false;
    private boolean paused          = false;

    public SimulationCanvas() {
        canvas = new Canvas(SimConfig.CANVAS_WIDTH, SimConfig.CANVAS_HEIGHT);
        gc     = canvas.getGraphicsContext2D();
        setPrefSize(SimConfig.CANVAS_WIDTH, SimConfig.CANVAS_HEIGHT);
        setMinSize(0, 0);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        widthProperty().addListener((obs, oldV, newV) -> { if (autoFitView) fitViewToCanvas(); });
        heightProperty().addListener((obs, oldV, newV) -> { if (autoFitView) fitViewToCanvas(); });
        getChildren().add(canvas);
        renderer = new BasicRenderer();
        setupInteraction();
        startLoop();
    }

    private void setupInteraction() {
        // Scroll to zoom
        canvas.setOnScroll((ScrollEvent e) -> {
            autoFitView = false;
            double factor = e.getDeltaY() > 0 ? 1.12 : 0.89;
            double newZoom = Math.max(SimConfig.MIN_ZOOM, Math.min(SimConfig.MAX_ZOOM, zoom * factor));
            // Zoom toward mouse pointer
            double mx = e.getX(), my = e.getY();
            panX = mx - (mx - panX) * (newZoom / zoom);
            panY = my - (my - panY) * (newZoom / zoom);
            zoom = newZoom;
        });

        // Right-drag to pan
        canvas.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                dragStartX = e.getX(); dragStartY = e.getY();
                dragPanX = panX; dragPanY = panY;
            }
        });
        canvas.setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                autoFitView = false;
                panX = dragPanX + (e.getX() - dragStartX);
                panY = dragPanY + (e.getY() - dragStartY);
            }
        });

        // Left-click: delete vehicle or manual light control
        canvas.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && controller != null) {
                // Convert screen→world coords
                double wx = (e.getX() - panX) / zoom;
                double wy = (e.getY() - panY) / zoom;
                
                // First check if a vehicle was clicked and delete it
                boolean deleted = controller.handleVehicleClick(wx, wy);
                
                // If no vehicle was clicked and manual mode is active, handle traffic light click
                if (!deleted && manualLightMode) {
                    controller.handleLightClick(wx, wy);
                }
            }
        });
    }

    private void startLoop() {
        animTimer = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNano == 0) { lastNano = now; return; }
                double dt = Math.min((now - lastNano) / 1_000_000_000.0, 0.05);
                lastNano = now;
                if (!paused && controller != null) controller.update(dt);
                if (controller != null)
                    renderer.render(gc, controller.getScene(), zoom, panX, panY);
            }
        };
        animTimer.start();
    }

    public void resetZoom() { autoFitView = true; fitViewToCanvas(); }
    public void zoomIn()    { autoFitView = false; zoom = Math.min(SimConfig.MAX_ZOOM, zoom*1.2); }
    public void zoomOut()   { autoFitView = false; zoom = Math.max(SimConfig.MIN_ZOOM, zoom/1.2); }

    private void fitViewToCanvas() {
        double w = Math.max(1, canvas.getWidth());
        double h = Math.max(1, canvas.getHeight());
        double fit = Math.min(w / SimConfig.CANVAS_WIDTH, h / SimConfig.CANVAS_HEIGHT);
        zoom = Math.max(SimConfig.MIN_ZOOM, Math.min(SimConfig.MAX_ZOOM, fit));
        panX = (w - SimConfig.CANVAS_WIDTH * zoom) * 0.5;
        panY = (h - SimConfig.CANVAS_HEIGHT * zoom) * 0.5;
    }

    public void setController(TrafficController c) { this.controller = c; resetZoom(); }
    public void setRenderer(SceneRenderer r)       { this.renderer   = r; }

    public void setManualLightMode(boolean manual) {
        this.manualLightMode = manual;
        if (controller != null) {
            controller.getScene().getIntersections().forEach(inter ->
                inter.getTrafficLights().forEach(tl -> tl.setAutoMode(!manual))
            );
        }
    }

    public void setPaused(boolean p) { paused = p; if (!p) lastNano = 0; }
    public boolean isPaused()        { return paused; }
    public double  getZoom()         { return zoom; }
}
