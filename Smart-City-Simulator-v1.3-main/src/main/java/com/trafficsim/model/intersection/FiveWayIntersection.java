package com.trafficsim.model.intersection;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.Direction;
import com.trafficsim.model.TrafficLight;
import java.util.*;

/**
 * True 5-way star intersection: 5 arms at 72° apart.
 * A vehicle entering from one arm exits through one of the OTHER 4 arms randomly.
 * Arms are indexed 0-4; each arm has both inbound and outbound lanes.
 */
public class FiveWayIntersection extends Intersection {
    /** Arm angles in degrees (measured from East=0, CCW) */
    private static final double[] ARM_ANGLES = {90, 162, 234, 306, 18};

    // We map each arm to a Direction for traffic light assignment
    private static final Direction[] ARM_DIRS = {
        Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST, Direction.EAST
    };

    private double phaseTimer = 0;
    private int currentPhase = 0; // 0 to 9

    public FiveWayIntersection(double cx, double cy) {
        super(cx, cy);
        buildLights();
        trafficLights.forEach(tl -> tl.setAutoMode(false));
        applyPhaseStates();
    }

    private void buildLights() {
        double r = getRadius();
        for (int i = 0; i < 5; i++) {
            double rad = Math.toRadians(ARM_ANGLES[i]);
            double tlX = cx + r * Math.cos(rad);
            double tlY = cy - r * Math.sin(rad); // screen Y flipped
            TrafficLight tl = new TrafficLight(tlX, tlY, ARM_DIRS[i], TrafficLight.Phase.RED);
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
            currentPhase = (currentPhase + 1) % 10;
        }
        applyPhaseStates();
    }

    private void applyPhaseStates() {
        int activeArm = currentPhase / 2;
        boolean isYellow = (currentPhase % 2 != 0);

        for (int i = 0; i < 5; i++) {
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
            state = (state + 1) % 10;
        }
        return totalTime;
    }

    @Override
    public void manualAdvance() {
        phaseTimer = 0;
        currentPhase = (currentPhase + 1) % 10;
        applyPhaseStates();
    }

    /** Returns the world-space endpoint of arm i at given distance from center. */
    public double[] getArmEndPoint(int armIndex, double length) {
        double rad = Math.toRadians(ARM_ANGLES[armIndex]);
        return new double[]{ cx + length * Math.cos(rad), cy - length * Math.sin(rad) };
    }

    /** Returns arm angle degrees for arm i */
    public double getArmAngle(int armIndex) { return ARM_ANGLES[armIndex]; }

    /**
     * Given entry direction, return a random one of the OTHER 4 arms' directions.
     * Because the 5 arms don't map cleanly to 4 cardinal directions, we assign
     * each arm an angle and pick a random different arm.
     */
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
    @Override public String getTypeName() { return "Vòng Xuyến"; }
    @Override public Type   getType()     { return Type.FIVE_WAY; }
}
