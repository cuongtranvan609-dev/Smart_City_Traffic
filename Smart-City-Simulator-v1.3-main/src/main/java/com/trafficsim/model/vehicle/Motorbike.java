package com.trafficsim.model.vehicle;

import com.trafficsim.model.Direction;
import com.trafficsim.model.driver.AggressiveDriver;
import com.trafficsim.model.driver.DrivingBehavior;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class Motorbike extends Vehicle {
    public Motorbike(double x, double y, Direction dir) { this(x, y, dir, new AggressiveDriver()); }
    public Motorbike(double x, double y, Direction dir, DrivingBehavior b) {
        super(x, y, dir, 100, 11, 5, b); preferredLaneIndex = 1;
    }
    @Override public String getShortName()  { return "Moto"; }
    @Override public String getColor()      { return "#FFB300"; }
    @Override public String getSpritePath() { return "/images/vehicles/motorbike.png"; }

    @Override
    public void drawShape(GraphicsContext gc, double scale) {
        double L = getLength()*scale, W = getWidth()*scale;
        // Frame
        gc.setFill(Color.rgb(60,60,60));
        gc.fillRoundRect(-L/2, -W*0.2, L, W*0.4, 2*scale, 2*scale);
        // Tank
        gc.setFill(Color.rgb(220,120,30));
        gc.fillRoundRect(-L*0.1, -W/2, L*0.35, W, 3*scale, 3*scale);
        // Wheels
        gc.setFill(Color.rgb(20,20,20));
        double wr=W*0.55;
        gc.fillOval(L*0.28-wr/2, -wr/2, wr, wr);
        gc.fillOval(-L*0.38-wr/2, -wr/2, wr, wr);
        // Rider silhouette
        gc.setFill(Color.rgb(50,50,120,0.7));
        gc.fillOval(-L*0.05-W*0.35, -W*0.7, W*0.7, W*0.9);
    }
}
