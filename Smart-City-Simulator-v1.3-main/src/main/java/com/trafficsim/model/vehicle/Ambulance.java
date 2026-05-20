package com.trafficsim.model.vehicle;

import com.trafficsim.model.Direction;
import com.trafficsim.model.driver.EmergencyDriver;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class Ambulance extends Vehicle {
    private double flashTimer = 0;
    private boolean flashOn   = false;
    private boolean flashBlue = false; // alternate red/blue

    public Ambulance(double x, double y, Direction dir) {
        super(x, y, dir, 110, 22, 10, new EmergencyDriver());
        this.preferredLaneIndex = 2; // Emergency lane
        honk();
    }
    @Override public boolean isPriorityVehicle() { return true; }
    @Override public String getShortName()  { return "Ambu"; }
    @Override public String getColor()      { return "#EF5350"; }
    @Override public String getSpritePath() { return "/images/vehicles/ambulance.png"; }

    @Override protected void onUpdate(double dt) {
        flashTimer += dt;
        if (flashTimer >= 0.2) { flashTimer=0; flashOn=!flashOn; flashBlue=!flashBlue; }
    }
    public boolean isFlashOn()  { return flashOn; }
    public boolean isFlashBlue(){ return flashBlue; }

    @Override
    public void drawShape(GraphicsContext gc, double scale) {
        double L = getLength()*scale, W = getWidth()*scale;
        // White body
        gc.setFill(Color.WHITE);
        gc.fillRoundRect(-L/2, -W/2, L, W, 3*scale, 3*scale);
        // Red stripe
        gc.setFill(Color.rgb(210,30,30));
        gc.fillRect(-L*0.1, -W/2, L*0.6, W);
        // Cross symbol
        gc.setFill(Color.WHITE);
        gc.fillRect( L*0.05, -W*0.12, L*0.25, W*0.24);
        gc.fillRect( L*0.12, -W*0.3,  L*0.11, W*0.6);
        // Windshield
        gc.setFill(Color.rgb(180,220,255,0.8));
        gc.fillRect(L*0.35, -W*0.36, L*0.13, W*0.72);
        // Rear window
        gc.fillRect(-L*0.48, -W*0.3, L*0.1, W*0.6);
        // Light bar
        if (flashOn) {
            gc.setFill(flashBlue ? Color.rgb(30,80,255,0.95) : Color.rgb(255,30,30,0.95));
        } else {
            gc.setFill(flashBlue ? Color.rgb(255,30,30,0.95) : Color.rgb(30,80,255,0.95));
        }
        gc.fillRoundRect(-L*0.1, -W/2-3*scale, L*0.35, 3*scale, 1*scale, 1*scale);
        // Wheels
        gc.setFill(Color.rgb(30,30,30));
        double wr=W*0.17, wl=W*0.1;
        gc.fillOval( L*0.25-wl/2, -W/2-wr/2, wl, wr);
        gc.fillOval( L*0.25-wl/2,  W/2-wr/2, wl, wr);
        gc.fillOval(-L*0.38-wl/2, -W/2-wr/2, wl, wr);
        gc.fillOval(-L*0.38-wl/2,  W/2-wr/2, wl, wr);
    }
}
