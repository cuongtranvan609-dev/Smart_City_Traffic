package com.trafficsim.model;

import com.trafficsim.model.intersection.Intersection;
import com.trafficsim.model.road.Lane;
import com.trafficsim.model.road.Road;
import com.trafficsim.model.vehicle.Vehicle;
import java.util.*;

public class SimScene {
    public enum SceneType { THREE_WAY, FOUR_WAY, FIVE_WAY, NETWORK }

    private final SceneType sceneType;
    private final List<Road>         roads         = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();
    private final List<Vehicle>      vehicles      = new ArrayList<>();
    private final List<Vehicle>      toRemove      = new ArrayList<>();

    public SimScene(SceneType type) { this.sceneType = type; }

    public void addRoad(Road r)               { roads.add(r); }
    public void addIntersection(Intersection i){ intersections.add(i); }
    public void addVehicle(Vehicle v)          { vehicles.add(v); }

    public void removeVehicle(Vehicle v) {
        toRemove.add(v);
        for (Road road : roads)
            for (Lane lane : road.getLanes())
                lane.removeVehicle(v);
    }

    public void flushRemovals() {
        vehicles.removeAll(toRemove);
        toRemove.clear();
    }

    public List<Vehicle>      getVehicles()         { return Collections.unmodifiableList(vehicles); }
    public List<Road>         getRoads()            { return roads; }
    public List<Intersection> getIntersections()    { return intersections; }
    public SceneType          getSceneType()        { return sceneType; }
    public int                getVehicleCount()     { return vehicles.size(); }

    /** Find the nearest intersection to point (x,y). */
    public Intersection getNearestIntersection(double x, double y) {
        Intersection best = null; double bestD = Double.MAX_VALUE;
        for (Intersection i : intersections) {
            double d = Math.hypot(i.getCx()-x, i.getCy()-y);
            if (d < bestD) { bestD=d; best=i; }
        }
        return best;
    }
}
