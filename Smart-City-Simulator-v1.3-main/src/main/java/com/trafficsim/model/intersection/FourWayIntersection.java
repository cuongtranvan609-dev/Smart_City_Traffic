package com.trafficsim.model.intersection;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.Direction;
import com.trafficsim.model.TrafficLight;
import java.util.*;

public class FourWayIntersection extends Intersection {
    private final Set<Direction> arms = EnumSet.allOf(Direction.class);
    private double phaseTimer = 0;
    private int currentPhase = 0; // 0: NS Green/EW Red, 1: NS Yellow/EW Red, 2: EW Green/NS Red, 3: EW Yellow/NS Red

    public FourWayIntersection(double cx, double cy) {
        super(cx, cy);
        buildLights();
        trafficLights.forEach(tl -> tl.setAutoMode(false));
        applyPhaseStates();
    }

    private void buildLights() {
        double r = getRadius();
        // NS green first, EW red, all placed on right curb of their incoming direction
        TrafficLight n = new TrafficLight(cx - 72,  cy - (r + 27),  Direction.SOUTH, TrafficLight.Phase.GREEN);
        TrafficLight s = new TrafficLight(cx + 72,  cy + (r + 27),  Direction.NORTH, TrafficLight.Phase.GREEN);
        TrafficLight e = new TrafficLight(cx + (r + 27),   cy - 72, Direction.WEST,  TrafficLight.Phase.RED);
        TrafficLight w = new TrafficLight(cx - (r + 27),   cy + 72, Direction.EAST,  TrafficLight.Phase.RED);
        n.setDisplayType(TrafficLight.DisplayType.LATE_COUNTDOWN);
        s.setDisplayType(TrafficLight.DisplayType.LATE_COUNTDOWN);
        e.setDisplayType(TrafficLight.DisplayType.ALWAYS_COUNTDOWN);
        w.setDisplayType(TrafficLight.DisplayType.LATE_COUNTDOWN);
        trafficLights.addAll(List.of(n, s, e, w));
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
        TrafficLight s = getLightForDirection(Direction.NORTH);
        TrafficLight e = getLightForDirection(Direction.WEST);
        TrafficLight w = getLightForDirection(Direction.EAST);

        switch (currentPhase) {
            case 0 -> { // NS Green, EW Red
                if (n != null) { n.setPhase(TrafficLight.Phase.GREEN); n.setGreenDuration(green); n.setTimer(phaseTimer); }
                if (s != null) { s.setPhase(TrafficLight.Phase.GREEN); s.setGreenDuration(green); s.setTimer(phaseTimer); }
                if (e != null) { e.setPhase(TrafficLight.Phase.RED); e.setRedDuration(red); e.setTimer(phaseTimer); }
                if (w != null) { w.setPhase(TrafficLight.Phase.RED); w.setRedDuration(red); w.setTimer(phaseTimer); }
            }
            case 1 -> { // NS Yellow, EW Red
                if (n != null) { n.setPhase(TrafficLight.Phase.YELLOW); n.setYellowDuration(yellow); n.setTimer(phaseTimer); }
                if (s != null) { s.setPhase(TrafficLight.Phase.YELLOW); s.setYellowDuration(yellow); s.setTimer(phaseTimer); }
                if (e != null) { e.setPhase(TrafficLight.Phase.RED); e.setRedDuration(red); e.setTimer(green + phaseTimer); }
                if (w != null) { w.setPhase(TrafficLight.Phase.RED); w.setRedDuration(red); w.setTimer(green + phaseTimer); }
            }
            case 2 -> { // EW Green, NS Red
                if (e != null) { e.setPhase(TrafficLight.Phase.GREEN); e.setGreenDuration(green); e.setTimer(phaseTimer); }
                if (w != null) { w.setPhase(TrafficLight.Phase.GREEN); w.setGreenDuration(green); w.setTimer(phaseTimer); }
                if (n != null) { n.setPhase(TrafficLight.Phase.RED); n.setRedDuration(red); n.setTimer(phaseTimer); }
                if (s != null) { s.setPhase(TrafficLight.Phase.RED); s.setRedDuration(red); s.setTimer(phaseTimer); }
            }
            case 3 -> { // EW Yellow, NS Red
                if (e != null) { e.setPhase(TrafficLight.Phase.YELLOW); e.setYellowDuration(yellow); e.setTimer(phaseTimer); }
                if (w != null) { w.setPhase(TrafficLight.Phase.YELLOW); w.setYellowDuration(yellow); w.setTimer(phaseTimer); }
                if (n != null) { n.setPhase(TrafficLight.Phase.RED); n.setRedDuration(red); n.setTimer(green + phaseTimer); }
                if (s != null) { s.setPhase(TrafficLight.Phase.RED); s.setRedDuration(red); s.setTimer(green + phaseTimer); }
            }
        }
    }

    @Override
    public void manualAdvance() {
        phaseTimer = 0;
        currentPhase = (currentPhase + 1) % 4;
        applyPhaseStates();
    }

    @Override public Set<Direction> getArms()   { return arms; }
    @Override public double getRadius()         { return 70; }
    @Override public String getTypeName()       { return "Ngã Tư"; }
    @Override public Type   getType()           { return Type.FOUR_WAY; }
}
