package com.trafficsim.model.intersection;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.Direction;
import com.trafficsim.model.TrafficLight;
import java.util.*;

/**
 * T-junction with 3 arms: NORTH arm (top), EAST arm (right), WEST arm (left).
 * Vehicles from the NORTH arm MUST turn left (WEST) or right (EAST) — never straight.
 * Vehicles from EAST arm can go WEST or turn NORTH.
 * Vehicles from WEST arm can go EAST or turn NORTH.
 */
public class ThreeWayIntersection extends Intersection {
    private final Set<Direction> arms = EnumSet.of(Direction.NORTH, Direction.EAST, Direction.WEST);

    // Which turns are allowed from each entry arm:
    // NORTH arm → can exit EAST or WEST (no straight = no SOUTH arm)
    // EAST  arm → can exit WEST or NORTH
    // WEST  arm → can exit EAST or NORTH
    private static final Map<Direction, List<Direction>> ALLOWED_EXITS = new EnumMap<>(Direction.class);
    static {
        ALLOWED_EXITS.put(Direction.SOUTH, List.of(Direction.EAST, Direction.WEST)); // from NORTH arm, moving SOUTH → exit E or W
        ALLOWED_EXITS.put(Direction.WEST,  List.of(Direction.WEST, Direction.NORTH)); // from EAST arm, moving WEST -> exit W or N
        ALLOWED_EXITS.put(Direction.EAST,  List.of(Direction.EAST, Direction.NORTH)); // from WEST arm, moving EAST -> exit E or N
        ALLOWED_EXITS.put(Direction.NORTH, List.of(Direction.EAST, Direction.WEST)); // shouldn't happen (no SOUTH arm)
    }

    private double phaseTimer = 0;
    private int currentPhase = 0; // 0: N Green/EW Red, 1: N Yellow/EW Red, 2: EW Green/N Red, 3: EW Yellow/N Red

    public ThreeWayIntersection(double cx, double cy) {
        super(cx, cy);
        buildLights();
        trafficLights.forEach(tl -> tl.setAutoMode(false));
        applyPhaseStates();
    }

    private void buildLights() {
        double r = getRadius();
        // Light for NORTH arm: vehicles moving SOUTH toward intersection, placed on right curb (West side)
        TrafficLight n = new TrafficLight(cx - 72, cy - (r + 27), Direction.SOUTH, TrafficLight.Phase.GREEN);
        // Light for EAST arm: vehicles moving WEST, placed on right curb (North side)
        TrafficLight e = new TrafficLight(cx + (r + 27), cy - 72, Direction.WEST, TrafficLight.Phase.RED);
        // Light for WEST arm: vehicles moving EAST, placed on right curb (South side)
        TrafficLight w = new TrafficLight(cx - (r + 27), cy + 72, Direction.EAST, TrafficLight.Phase.RED);

        n.setDisplayType(TrafficLight.DisplayType.LATE_COUNTDOWN);
        e.setDisplayType(TrafficLight.DisplayType.LATE_COUNTDOWN);
        w.setDisplayType(TrafficLight.DisplayType.LATE_COUNTDOWN);
        trafficLights.addAll(List.of(n, e, w));
    }

    @Override
    public void update(double dt) {
        phaseTimer += dt;
        double duration = getCurrentPhaseDuration();
        if (phaseTimer >= duration) {
            phaseTimer = 0;
            currentPhase = (currentPhase + 1) % 4;
        }
        applyPhaseStates();
    }

    private double getCurrentPhaseDuration() {
        if (currentPhase == 0 || currentPhase == 2) {
            return SimConfig.GREEN_DURATION;
        } else {
            return SimConfig.YELLOW_DURATION;
        }
    }

    private void applyPhaseStates() {
        double green = SimConfig.GREEN_DURATION;
        double yellow = SimConfig.YELLOW_DURATION;
        double red = green + yellow;

        TrafficLight n = getLightForDirection(Direction.SOUTH);
        TrafficLight e = getLightForDirection(Direction.WEST);
        TrafficLight w = getLightForDirection(Direction.EAST);

        switch (currentPhase) {
            case 0 -> { // N Green, EW Red
                if (n != null) { n.setPhase(TrafficLight.Phase.GREEN); n.setGreenDuration(green); n.setTimer(phaseTimer); }
                if (e != null) { e.setPhase(TrafficLight.Phase.RED); e.setRedDuration(red); e.setTimer(phaseTimer); }
                if (w != null) { w.setPhase(TrafficLight.Phase.RED); w.setRedDuration(red); w.setTimer(phaseTimer); }
            }
            case 1 -> { // N Yellow, EW Red
                if (n != null) { n.setPhase(TrafficLight.Phase.YELLOW); n.setYellowDuration(yellow); n.setTimer(phaseTimer); }
                if (e != null) { e.setPhase(TrafficLight.Phase.RED); e.setRedDuration(red); e.setTimer(green + phaseTimer); }
                if (w != null) { w.setPhase(TrafficLight.Phase.RED); w.setRedDuration(red); w.setTimer(green + phaseTimer); }
            }
            case 2 -> { // EW Green, N Red
                if (e != null) { e.setPhase(TrafficLight.Phase.GREEN); e.setGreenDuration(green); e.setTimer(phaseTimer); }
                if (w != null) { w.setPhase(TrafficLight.Phase.GREEN); w.setGreenDuration(green); w.setTimer(phaseTimer); }
                if (n != null) { n.setPhase(TrafficLight.Phase.RED); n.setRedDuration(red); n.setTimer(phaseTimer); }
            }
            case 3 -> { // EW Yellow, N Red
                if (e != null) { e.setPhase(TrafficLight.Phase.YELLOW); e.setYellowDuration(yellow); e.setTimer(phaseTimer); }
                if (w != null) { w.setPhase(TrafficLight.Phase.YELLOW); w.setYellowDuration(yellow); w.setTimer(phaseTimer); }
                if (n != null) { n.setPhase(TrafficLight.Phase.RED); n.setRedDuration(red); n.setTimer(green + phaseTimer); }
            }
        }
    }

    @Override
    public void manualAdvance() {
        phaseTimer = 0;
        currentPhase = (currentPhase + 1) % 4;
        applyPhaseStates();
    }

    @Override
    public Direction randomExitDirection(Direction movingDir, Random rng) {
        List<Direction> exits = ALLOWED_EXITS.get(movingDir);
        if (exits == null || exits.isEmpty()) return movingDir;
        return exits.get(rng.nextInt(exits.size()));
    }

    @Override public Set<Direction> getArms()   { return arms; }
    @Override public double getRadius()         { return 70; }
    @Override public String getTypeName()       { return "Ngã Ba"; }
    @Override public Type   getType()           { return Type.THREE_WAY; }
}
