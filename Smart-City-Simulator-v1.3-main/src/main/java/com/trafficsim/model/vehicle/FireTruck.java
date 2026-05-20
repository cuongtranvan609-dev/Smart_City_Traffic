package com.trafficsim.model.vehicle;

import com.trafficsim.model.Direction;
import com.trafficsim.model.driver.EmergencyDriver;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class FireTruck extends Vehicle {
    private double flashTimer = 0;
    private boolean flashOn   = false;

    public FireTruck(double x, double y, Direction dir) {
        super(x, y, dir, 100, 28, 12, new EmergencyDriver());
        this.preferredLaneIndex = 0;
        honk();
    }
    @Override public boolean isPriorityVehicle() { return true; }
    @Override public String getShortName()  { return "Fire"; }
    @Override public String getColor()      { return "#FF6D00"; }
    @Override public String getSpritePath() { return "/images/vehicles/firetruck.png"; }

    @Override protected void onUpdate(double dt) {
        flashTimer += dt;
        if (flashTimer >= 0.25) { flashTimer=0; flashOn=!flashOn; }
    }
    public boolean isFlashOn() { return flashOn; }

    @Override
    public void drawShape(GraphicsContext gc, double scale) {
        double L = getLength()*scale, W = getWidth()*scale;
        gc.setFill(Color.rgb(210,35,35));
        gc.fillRoundRect(-L/2, -W/2, L, W, 3*scale, 3*scale);
        // Yellow stripe
        gc.setFill(Color.rgb(240,200,0));
        gc.fillRect(-L*0.45, -W*0.18, L*0.9, W*0.36);
        // Cab
        gc.setFill(Color.rgb(190,25,25));
        gc.fillRoundRect(L*0.2, -W/2, L*0.3, W, 2*scale, 2*scale);
        // Windshield
        gc.setFill(Color.rgb(180,220,255,0.8));
        gc.fillRect(L*0.32, -W*0.36, L*0.16, W*0.72);
        // Hose reel
        gc.setFill(Color.rgb(150,90,20));
        gc.fillOval(-L*0.15-W*0.3, -W*0.3, W*0.6, W*0.6);
        // Light bar
        gc.setFill(flashOn ? Color.rgb(255,30,30,0.95) : Color.rgb(30,80,255,0.95));
        gc.fillRoundRect(L*0.2, -W/2-3*scale, L*0.28, 3*scale, 1*scale, 1*scale);
        // Wheels
        gc.setFill(Color.rgb(30,30,30));
        double wr=W*0.18, wl=W*0.11;
        gc.fillOval( L*0.30-wl/2, -W/2-wr/2, wl, wr);
        gc.fillOval( L*0.30-wl/2,  W/2-wr/2, wl, wr);
        gc.fillOval(-L*0.38-wl/2, -W/2-wr/2, wl, wr);
        gc.fillOval(-L*0.38-wl/2,  W/2-wr/2, wl, wr);
    }
}
