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
        spawnCount++;
        boolean isPriority = (spawnCount % PRIORITY_RATIO == 0);

        List<Lane> eligibleLanes = new ArrayList<>();
        for (Road road : scene.getRoads()) {
            for (Lane lane : road.getLanes()) {
                if (isClearSpawnStart(lane)) {
                    eligibleLanes.add(lane);
                }
            }
        }

        if (eligibleLanes.isEmpty()) return;

        Lane lane = eligibleLanes.get(rng.nextInt(eligibleLanes.size()));
        double x = lane.getStartX(), y = lane.getStartY();

        // Create vehicle based on lane index:
        Vehicle v;
        if (lane.getLaneIndex() == 3) {
            // Lane 3 is the brown lane: only bicycles allowed!
            v = VehicleFactory.create(VehicleFactory.Type.BICYCLE, x, y, lane.getDirection());
        } else {
            // Lanes 0, 1, 2: motorized vehicles
            if (isPriority) {
                var type = rng.nextBoolean() ? VehicleFactory.Type.AMBULANCE : VehicleFactory.Type.FIRE_TRUCK;
                v = VehicleFactory.create(type, x, y, lane.getDirection());
            } else {
                int r = rng.nextInt(10);
                if (r < 5) {
                    v = VehicleFactory.create(VehicleFactory.Type.CAR, x, y, lane.getDirection());
                } else if (r < 7) {
                    v = VehicleFactory.create(VehicleFactory.Type.MOTORBIKE, x, y, lane.getDirection());
                } else {
                    v = VehicleFactory.create(VehicleFactory.Type.BUS, x, y, lane.getDirection());
                }
            }
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
