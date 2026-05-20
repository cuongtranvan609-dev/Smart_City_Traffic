package com.trafficsim.model.vehicle;

import com.trafficsim.model.Direction;
import com.trafficsim.model.driver.CautiousDriver;
import com.trafficsim.model.driver.DrivingBehavior;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class Bus extends Vehicle {
    public Bus(double x, double y, Direction dir) { this(x, y, dir, new CautiousDriver()); }
    public Bus(double x, double y, Direction dir, DrivingBehavior b) {
        super(x, y, dir, 55, 30, 13, b);
    }
    @Override public String getShortName()  { return "Bus"; }
    @Override public String getColor()      { return "#CE93D8"; }
    @Override public String getSpritePath() { return "/images/vehicles/bus.png"; }

    @Override
    public void drawShape(GraphicsContext gc, double scale) {
        double L = getLength()*scale, W = getWidth()*scale;
        gc.setFill(Color.rgb(180,90,210));
        gc.fillRoundRect(-L/2, -W/2, L, W, 3*scale, 3*scale);
        // Windows row
        gc.setFill(Color.rgb(200,230,255,0.8));
        for (int i = 0; i < 4; i++) {
            double wx = -L*0.35 + i*(L*0.18);
            gc.fillRect(wx, -W*0.36, L*0.14, W*0.32);
            gc.fillRect(wx, W*0.04,  L*0.14, W*0.32);
        }
        // Front windshield
        gc.fillRect(L*0.3, -W*0.38, L*0.18, W*0.76);
        // Wheels
        gc.setFill(Color.rgb(30,30,30));
        double wr=W*0.16, wl=W*0.1;
        gc.fillOval( L*0.32-wl/2, -W/2-wr/2, wl, wr);
        gc.fillOval( L*0.32-wl/2,  W/2-wr/2, wl, wr);
        gc.fillOval(-L*0.35-wl/2, -W/2-wr/2, wl, wr);
        gc.fillOval(-L*0.35-wl/2,  W/2-wr/2, wl, wr);
    }
}
