package com.trafficsim.model.intersection;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.Direction;
import com.trafficsim.model.TrafficLight;
import java.util.*;

/**
 * Roundabout intersection supporting both 4-way (90 deg grid) and 5-way (star) modes.
 */
public class FiveWayIntersection extends Intersection {
    private final boolean isFourWayRoundabout;
    private final double[] armAngles;
    private final Direction[] armDirs;
    private final int numArms;
    private final int totalPhases;

    private double phaseTimer = 0;
    private int currentPhase = 0; // 0 to (totalPhases - 1)

    public FiveWayIntersection(double cx, double cy) {
        this(cx, cy, false);
    }

    public FiveWayIntersection(double cx, double cy, boolean isFourWayRoundabout) {
        super(cx, cy);
        this.isFourWayRoundabout = isFourWayRoundabout;
        if (isFourWayRoundabout) {
            this.armAngles = new double[]{90, 180, 270, 0};
            this.armDirs = new Direction[]{Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST};
            this.numArms = 4;
            this.totalPhases = 8;
        } else {
            this.armAngles = new double[]{90, 162, 234, 306, 18};
            this.armDirs = new Direction[]{Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST, Direction.EAST};
            this.numArms = 5;
            this.totalPhases = 10;
        }
        buildLights();
        trafficLights.forEach(tl -> tl.setAutoMode(false));
        applyPhaseStates();
    }

    private void buildLights() {
        double r = getRadius();
        for (int i = 0; i < numArms; i++) {
            double rad = Math.toRadians(armAngles[i]);
            // Position on right curb of incoming arm, aligned with stop line at r + 27
            double tlX = cx + (r + 27) * Math.cos(rad) - 72 * Math.sin(rad);
            double tlY = cy - (r + 27) * Math.sin(rad) - 72 * Math.cos(rad);
            TrafficLight tl = new TrafficLight(tlX, tlY, armDirs[i], TrafficLight.Phase.RED);
            tl.setAngleDeg(armAngles[i]); // Set custom angle matching the arm angle
            tl.setDisplayType(TrafficLight.DisplayType.LATE_COUNTDOWN);
            trafficLights.add(tl);
        }
    }

    @Override
    public void update(double dt) {
        phaseTimer += dt;
        double duration = (currentPhase % 2 == 0) ? SimConfig.GREEN_DURATION : SimConfig.YELLOW_DURATION;
        if (phaseTimer >= duration) {
            phaseTimer = 0;
            currentPhase = (currentPhase + 1) % totalPhases;
        }
        applyPhaseStates();
    }

    private void applyPhaseStates() {
        int activeArm = currentPhase / 2;
        boolean isYellow = (currentPhase % 2 != 0);

        for (int i = 0; i < numArms; i++) {
            if (i >= trafficLights.size()) continue;
            TrafficLight tl = trafficLights.get(i);
            if (i == activeArm) {
                if (isYellow) {
                    tl.setPhase(TrafficLight.Phase.YELLOW);
                    tl.setYellowDuration(SimConfig.YELLOW_DURATION);
                    tl.setTimer(phaseTimer);
                } else {
                    tl.setPhase(TrafficLight.Phase.GREEN);
                    tl.setGreenDuration(SimConfig.GREEN_DURATION);
                    tl.setTimer(phaseTimer);
                }
            } else {
                double remainingRed = getRemainingRedTime(currentPhase, phaseTimer, i);
                tl.setPhase(TrafficLight.Phase.RED);
                tl.setRedDuration(remainingRed);
                tl.setTimer(0);
            }
        }
    }

    private double getRemainingRedTime(int currentState, double phaseTimer, int targetArm) {
        int targetState = 2 * targetArm;
        if (currentState == targetState) return 0;
        
        double totalTime = 0;
        int state = currentState;
        while (state != targetState) {
            if (state == currentState) {
                double dur = (state % 2 == 0) ? SimConfig.GREEN_DURATION : SimConfig.YELLOW_DURATION;
                totalTime += (dur - phaseTimer);
            } else {
                totalTime += (state % 2 == 0) ? SimConfig.GREEN_DURATION : SimConfig.YELLOW_DURATION;
            }
            state = (state + 1) % totalPhases;
        }
        return totalTime;
    }

    @Override
    public void manualAdvance() {
        phaseTimer = 0;
        currentPhase = (currentPhase + 1) % totalPhases;
        applyPhaseStates();
    }

    /** Returns the world-space endpoint of arm i at given distance from center. */
    public double[] getArmEndPoint(int armIndex, double length) {
        double rad = Math.toRadians(armAngles[armIndex]);
        return new double[]{ cx + length * Math.cos(rad), cy - length * Math.sin(rad) };
    }

    /** Returns arm angle degrees for arm i */
    public double getArmAngle(int armIndex) { return armAngles[armIndex]; }

    public int getNumArms() { return numArms; }
    public boolean isFourWayRoundabout() { return isFourWayRoundabout; }

    @Override
    public Direction randomExitDirection(Direction entryDir, Random rng) {
        Direction[] choices = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        List<Direction> valid = new ArrayList<>();
        for (Direction d : choices) {
            if (d != entryDir.opposite()) valid.add(d);
        }
        return valid.get(rng.nextInt(valid.size()));
    }

    @Override public Set<Direction> getArms() {
        return EnumSet.allOf(Direction.class);
    }
    @Override public double getRadius()   { return 110; }
    @Override public String getTypeName() { return isFourWayRoundabout ? "Vòng Xuyến 4 Ngả" : "Vòng Xuyến 5 Ngả"; }
    @Override public Type   getType()     { return Type.FIVE_WAY; }
}
