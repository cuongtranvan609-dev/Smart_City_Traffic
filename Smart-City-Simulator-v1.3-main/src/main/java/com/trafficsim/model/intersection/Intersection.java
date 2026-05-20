package com.trafficsim.model.intersection;

import com.trafficsim.model.Direction;
import com.trafficsim.model.TrafficLight;
import com.trafficsim.model.vehicle.Vehicle;
import java.util.*;

/**
 * Abstract base for all intersection types.
 * Subclasses define shape, traffic light placement, and turn routing.
 */
public abstract class Intersection {
    protected final double cx, cy;
    protected final List<TrafficLight> trafficLights = new ArrayList<>();

    public enum Type { THREE_WAY, FOUR_WAY, FIVE_WAY }

    protected Intersection(double cx, double cy) {
        this.cx=cx; this.cy=cy;
    }

    public void update(double dt) {
        trafficLights.forEach(tl -> tl.update(dt));
    }

    public void manualAdvance() {}

    public TrafficLight getLightForDirection(Direction dir) {
        return trafficLights.stream().filter(tl->tl.getDirection()==dir).findFirst().orElse(null);
    }

    public boolean contains(Vehicle v) {
        double r = getRadius(), dx=v.getX()-cx, dy=v.getY()-cy;
        return dx*dx+dy*dy < r*r;
    }

    /** Which directions this intersection has arms for */
    public abstract Set<Direction> getArms();

    /**
     * Given entry direction, return a random valid exit direction
     * (not the opposite = no U-turn).
     */
    public Direction randomExitDirection(Direction entryDir, Random rng) {
        List<Direction> exits = new ArrayList<>();
        Direction back = entryDir.opposite();
        for (Direction d : getArms()) {
            if (d != back) exits.add(d);
        }
        if (exits.isEmpty()) return entryDir;
        return exits.get(rng.nextInt(exits.size()));
    }

    public abstract double getRadius();
    public abstract String getTypeName();
    public abstract Type   getType();

    public List<TrafficLight> getTrafficLights() { return trafficLights; }
    public double getCx() { return cx; }
    public double getCy() { return cy; }
}
