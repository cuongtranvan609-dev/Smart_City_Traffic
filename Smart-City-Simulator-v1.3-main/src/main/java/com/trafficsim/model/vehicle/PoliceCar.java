package com.trafficsim.model.vehicle;

import com.trafficsim.model.Direction;
import com.trafficsim.model.driver.EmergencyDriver;
import com.trafficsim.model.intersection.Intersection;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class PoliceCar extends Vehicle {
    public enum PoliceState {
        DRIVING_TO_INTERSECTION,
        REGULATING,
        DRIVING_AWAY
    }

    private final Intersection targetIntersection;
    private PoliceState state = PoliceState.DRIVING_TO_INTERSECTION;
    private double regulateTimer = 0.0;
    private double flashTimer = 0.0;
    private boolean flashOn = false;
    private boolean flashBlue = false;
    private double blockedTimer = 0.0;

    public PoliceCar(double x, double y, Direction dir, Intersection targetIntersection) {
        super(x, y, dir, 120, 21, 9.5, new EmergencyDriver());
        this.targetIntersection = targetIntersection;
        this.preferredLaneIndex = 0;
        honk();
    }

    @Override
    public boolean isPriorityVehicle() {
        return true;
    }

    @Override
    public String getShortName() {
        return "Pol";
    }

    @Override
    public String getColor() {
        return "#1A237E";
    }

    @Override
    public String getSpritePath() {
        return "/images/vehicles/police.png";
    }

    @Override
    protected void onUpdate(double dt) {
        flashTimer += dt;
        if (flashTimer >= 0.15) {
            flashTimer = 0;
            flashOn = !flashOn;
            flashBlue = !flashBlue;
        }
    }

    @Override
    public void update(double dt, Vehicle frontVehicle) {
        if (state == PoliceState.REGULATING) {
            setSpeed(0);
            onUpdate(dt);
            return;
        }
        if (state == PoliceState.DRIVING_TO_INTERSECTION) {
            if (getSpeed() < 0.15) {
                blockedTimer += dt;
                if (blockedTimer >= 1.5) {
                    state = PoliceState.REGULATING;
                    blockedTimer = 0.0;
                }
            } else {
                blockedTimer = 0.0;
            }
        }
        super.update(dt, frontVehicle);
    }

    public PoliceState getPoliceState() {
        return state;
    }

    public void setPoliceState(PoliceState s) {
        this.state = s;
    }

    public Intersection getTargetIntersection() {
        return targetIntersection;
    }

    public double getRegulateTimer() {
        return regulateTimer;
    }

    public void setRegulateTimer(double t) {
        this.regulateTimer = t;
    }

    public boolean isFlashOn() {
        return flashOn;
    }

    public boolean isFlashBlue() {
        return flashBlue;
    }

    @Override
    public void drawShape(GraphicsContext gc, double scale) {
        double L = getLength() * scale, W = getWidth() * scale;
        // Black body
        gc.setFill(Color.rgb(20, 20, 30));
        gc.fillRoundRect(-L / 2, -W / 2, L, W, 3 * scale, 3 * scale);
        // White center
        gc.setFill(Color.WHITE);
        gc.fillRect(-L * 0.15, -W / 2, L * 0.3, W);
        // Label
        gc.setFill(Color.rgb(26, 35, 126));
        gc.setFont(javafx.scene.text.Font.font("Arial Bold", Math.max(6, 6.5 * scale)));
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.fillText("Pol", 0, 2 * scale);

        // Flashing Light Bar
        if (flashOn) {
            gc.setFill(flashBlue ? Color.BLUE : Color.RED);
            gc.fillRect(-2 * scale, -W / 2, 4 * scale, W);
        }
    }
}
