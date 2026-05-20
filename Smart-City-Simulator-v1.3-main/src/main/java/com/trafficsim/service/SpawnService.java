package com.trafficsim.service;

import com.trafficsim.model.Direction;
import com.trafficsim.model.SimScene;
import com.trafficsim.model.road.Lane;
import com.trafficsim.model.road.Road;
import com.trafficsim.model.vehicle.*;
import java.util.*;

public class SpawnService {
    private final SimScene scene;
    private final Random   rng = new Random();
    private static final int PRIORITY_RATIO = 20;
    private int spawnCount = 0;

    public SpawnService(SimScene scene) { this.scene = scene; }

    public void spawnVehicle() {
        // Prefer outer lanes for motorbikes/bicycles, inner for cars
        List<Lane> allLanes = new ArrayList<>();
        for (Road road : scene.getRoads()) {
            for (Lane lane : road.getLanes()) {
                if (isClearSpawnStart(lane)) allLanes.add(lane);
            }
        }
        if (allLanes.isEmpty()) return;

        Lane lane = allLanes.get(rng.nextInt(allLanes.size()));
        double x = lane.getStartX(), y = lane.getStartY();

        spawnCount++;
        boolean isPriority = (spawnCount % PRIORITY_RATIO == 0);

        // Pick vehicle type matching lane
        Vehicle v;
        if (isPriority) {
            var type = rng.nextBoolean() ? VehicleFactory.Type.AMBULANCE : VehicleFactory.Type.FIRE_TRUCK;
            v = VehicleFactory.create(type, x, y, lane.getDirection());
        } else if (lane.getLaneIndex() >= 1) {
            // Outer lanes (1 and 2): motorbike or bicycle
            v = rng.nextInt(3) == 0
                ? VehicleFactory.create(VehicleFactory.Type.BICYCLE, x, y, lane.getDirection())
                : VehicleFactory.create(VehicleFactory.Type.MOTORBIKE, x, y, lane.getDirection());
        } else {
            // Inner lane: car or bus (occasionally)
            v = rng.nextInt(6) == 0
                ? VehicleFactory.create(VehicleFactory.Type.BUS, x, y, lane.getDirection())
                : VehicleFactory.create(VehicleFactory.Type.CAR, x, y, lane.getDirection());
        }

        if (!lane.hasSpaceNear(x, y, v.getLength() + 10)) return;

        // Random turn intent
        Vehicle.TurnIntent[] intents = Vehicle.TurnIntent.values();
        v.setGlobalTurnPreference(intents[rng.nextInt(intents.length)]);

        lane.addVehicle(v);
        scene.addVehicle(v);
    }

    private boolean isClearSpawnStart(Lane lane) {
        return scene.getIntersections().stream().noneMatch(inter -> {
            double d = Math.hypot(lane.getStartX() - inter.getCx(), lane.getStartY() - inter.getCy());
            return d < inter.getRadius() + 20;
        });
    }
}
