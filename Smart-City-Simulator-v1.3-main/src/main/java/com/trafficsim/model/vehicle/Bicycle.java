package com.trafficsim.model.vehicle;

import com.trafficsim.model.Direction;
import com.trafficsim.model.driver.CautiousDriver;
import com.trafficsim.model.driver.DrivingBehavior;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class Bicycle extends Vehicle {
    public Bicycle(double x, double y, Direction dir) { this(x, y, dir, new CautiousDriver()); }
    public Bicycle(double x, double y, Direction dir, DrivingBehavior b) {
        super(x, y, dir, 25, 9, 4, b); preferredLaneIndex = 1;
    }
    @Override public String getShortName()  { return "Bike"; }
    @Override public String getColor()      { return "#A5D6A7"; }
    @Override public String getSpritePath() { return "/images/vehicles/bicycle.png"; }

    @Override
    public void drawShape(GraphicsContext gc, double scale) {
        double L = getLength()*scale, W = getWidth()*scale;
        double wr = W*0.55;
        gc.setFill(Color.rgb(20,20,20));
        gc.fillOval( L*0.3-wr/2, -wr/2, wr, wr);
        gc.fillOval(-L*0.3-wr/2, -wr/2, wr, wr);
        gc.setStroke(Color.rgb(60,160,60));
        gc.setLineWidth(1.5*scale);
        gc.strokeLine(-L*0.3, 0, L*0.3, 0);
        gc.strokeLine(0, 0, L*0.05, -W*0.55);
        // Rider
        gc.setFill(Color.rgb(80,80,180,0.8));
        gc.fillOval(-W*0.3, -W*0.8, W*0.6, W*0.7);
    }
}
