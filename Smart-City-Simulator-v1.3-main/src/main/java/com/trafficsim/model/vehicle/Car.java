package com.trafficsim.model.vehicle;

import com.trafficsim.model.Direction;
import com.trafficsim.model.driver.DrivingBehavior;
import com.trafficsim.model.driver.NormalDriver;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class Car extends Vehicle {
    private final Color bodyColor;

    public Car(double x, double y, Direction dir) { this(x, y, dir, new NormalDriver()); }
    public Car(double x, double y, Direction dir, DrivingBehavior b) {
        super(x, y, dir, 80, 18, 9, b);
        // Random car color
        Color[] colors = {Color.rgb(70,130,200), Color.rgb(200,70,70),
                Color.rgb(200,160,50), Color.rgb(80,160,80), Color.rgb(160,80,200)};
        bodyColor = colors[(int)(Math.random()*colors.length)];
    }

    @Override public String getShortName()  { return "Car"; }
    @Override public String getColor()      { return "#4FC3F7"; }
    @Override public String getSpritePath() { return "/images/vehicles/car.png"; }

    @Override
    public void drawShape(GraphicsContext gc, double scale) {
        double L = getLength()*scale, W = getWidth()*scale;
        // Body
        gc.setFill(bodyColor);
        gc.fillRoundRect(-L/2, -W/2, L, W, 4*scale, 4*scale);
        // Windshield (front)
        gc.setFill(Color.rgb(180,220,255,0.85));
        gc.fillRect(L*0.05, -W*0.35, L*0.25, W*0.7);
        // Rear window
        gc.setFill(Color.rgb(160,200,240,0.75));
        gc.fillRect(-L*0.32, -W*0.33, L*0.2, W*0.66);
        // Roof
        gc.setFill(bodyColor.darker());
        gc.fillRoundRect(-L*0.22, -W*0.38, L*0.44, W*0.76, 3*scale, 3*scale);
        // Wheels
        gc.setFill(Color.rgb(30,30,30));
        double wr=W*0.18, wl=W*0.13;
        gc.fillOval( L*0.22-wl/2, -W/2-wr,   wl, wr*2);
        gc.fillOval( L*0.22-wl/2,  W/2-wr,   wl, wr*2);
        gc.fillOval(-L*0.30-wl/2, -W/2-wr,   wl, wr*2);
        gc.fillOval(-L*0.30-wl/2,  W/2-wr,   wl, wr*2);
        // Headlights
        gc.setFill(Color.rgb(255,250,200,0.9));
        gc.fillOval(L/2-3*scale, -W*0.3, 4*scale, 4*scale);
        gc.fillOval(L/2-3*scale,  W*0.3-4*scale, 4*scale, 4*scale);
        // Tail lights
        gc.setFill(Color.rgb(220,50,50,0.85));
        gc.fillOval(-L/2, -W*0.3, 3*scale, 3*scale);
        gc.fillOval(-L/2,  W*0.3-3*scale, 3*scale, 3*scale);
    }
}
