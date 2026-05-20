package com.trafficsim.controller;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.Direction;
import com.trafficsim.model.SimScene;
import com.trafficsim.model.TrafficLight;
import com.trafficsim.model.intersection.FiveWayIntersection;
import com.trafficsim.model.intersection.Intersection;
import com.trafficsim.model.road.Lane;
import com.trafficsim.model.road.Road;
import com.trafficsim.model.vehicle.Vehicle;
import com.trafficsim.service.SoundService;
import com.trafficsim.service.SpawnService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class TrafficController {
    private final SimScene scene;
    private final SpawnService spawnService;
    private final SoundService soundService;
    private final Random rng = new Random();
    private final Map<String, Intersection> routedIntersections = new HashMap<>();

    private double spawnInterval;
    private double spawnTimer = 0;
    private boolean spawnEnabled = true;

    public void setSpawnEnabled(boolean enabled) {
        this.spawnEnabled = enabled;
    }

    public void clearAllVehicles() {
        List<Vehicle> toRemove = new ArrayList<>(scene.getVehicles());
        for (Vehicle v : toRemove) {
            scene.removeVehicle(v);
            routedIntersections.remove(v.getId());
        }
        scene.flushRemovals();
    }

    public enum TrafficDensity { LOW, MEDIUM, HIGH }

    public TrafficController(SimScene scene, SoundService soundService) {
        this.scene = scene;
        this.soundService = soundService;
        this.spawnService = new SpawnService(scene);
        setTrafficDensity(TrafficDensity.MEDIUM);
    }

    public void setTrafficDensity(TrafficDensity d) {
        spawnInterval = switch (d) {
            case LOW -> SimConfig.SPAWN_INTERVAL_LOW;
            case MEDIUM -> SimConfig.SPAWN_INTERVAL_MEDIUM;
            case HIGH -> SimConfig.SPAWN_INTERVAL_HIGH;
        };
    }

    public void update(double dt) {
        scene.getIntersections().forEach(i -> i.update(dt));
        handlePriorityYielding();
        handleIntersectionYielding();
        updateVehicles(dt);
        handleIntersectionRouting();
        handleLaneSelectionForTurning();
        handleOvertaking();

        spawnTimer += dt;
        if (spawnEnabled && spawnTimer >= spawnInterval) {
            spawnTimer = 0;
            spawnService.spawnVehicle();
        }

        removeOutOfBounds();
        scene.flushRemovals();
    }

    private void updateVehicles(double dt) {
        for (Vehicle v : scene.getVehicles()) {
            Lane lane = v.getCurrentLane();
            if (lane == null) continue;

            Vehicle front = lane.getFrontVehicle(v);
            if (v.isFollowingPath()) {
                Intersection inter = routedIntersections.get(v.getId());
                if (inter != null) {
                    Vehicle closest = null;
                    double minDist = Double.MAX_VALUE;
                    for (Vehicle other : scene.getVehicles()) {
                        if (other == v) continue;
                        if (inter.contains(other) || other.isFollowingPath()) {
                            double relX = other.getX() - v.getX();
                            double relY = other.getY() - v.getY();
                            double dot = relX * v.getMoveX() + relY * v.getMoveY();
                            if (dot > 0) {
                                boolean samePath = false;
                                if (inter instanceof FiveWayIntersection) {
                                    Lane myExit = v.getUpcomingExitLane();
                                    Lane otherExit = other.getUpcomingExitLane();
                                    if (myExit != null && otherExit != null && myExit.getLaneIndex() == otherExit.getLaneIndex()) {
                                        samePath = true;
                                    }
                                } else {
                                    double cross = Math.abs(relX * v.getMoveY() - relY * v.getMoveX());
                                    if (cross < SimConfig.LANE_WIDTH * 0.6) samePath = true;
                                }
                                
                                if (samePath) {
                                    double dist = Math.hypot(relX, relY);
                                    if (dist < minDist && dist < 120) {
                                        minDist = dist;
                                        closest = other;
                                    }
                                }
                            }
                        }
                    }
                    if (closest != null) front = closest;
                }
            }

            v.update(dt, front);
            if (v.isHonking()) soundService.playHorn(v);
            if (v.isSignalOn()) soundService.playSignal(v);
        }
    }

    private void handlePriorityYielding() {
        List<Vehicle> all = scene.getVehicles();
        List<Vehicle> priority = all.stream().filter(Vehicle::isPriorityVehicle).toList();

        for (Vehicle v : all) {
            if (v.isPriorityVehicle()) continue;
            boolean must = priority.stream().anyMatch(pv -> shouldYieldToPriority(v, pv));
            if (must) beginPriorityYield(v);
            else releasePriorityYield(v);
        }
    }

    private boolean shouldYieldToPriority(Vehicle v, Vehicle priority) {
        Lane lane = v.getCurrentLane();
        Lane priorityLane = priority.getCurrentLane();
        if (lane == null || priorityLane == null) return false;
        if (v.isFollowingPath()) return false;
        if (lane != priorityLane) return false;

        double dx = v.getX() - priority.getX();
        double dy = v.getY() - priority.getY();
        double ahead = dx * priority.getMoveX() + dy * priority.getMoveY();
        return ahead > -v.getLength() && ahead < SimConfig.PRIORITY_YIELD_RANGE;
    }

    private void beginPriorityYield(Vehicle v) {
        v.rememberPriorityYieldLane();
        v.setYieldingForPriority(true);

        Lane lane = v.getCurrentLane();
        if (lane == null) return;

        Lane rightLane = lane.getRightSibling();
        double offset = SimConfig.SHOULDER_YIELD_OFFSET;
        if (rightLane != null) {
            boolean occupied = false;
            for (Vehicle other : rightLane.getVehicles()) {
                if (Math.abs(v.distanceTo(other)) < v.getLength() + 10) {
                    occupied = true; break;
                }
            }
            if (!occupied) offset = SimConfig.LANE_WIDTH;
            else offset = SimConfig.SHOULDER_YIELD_OFFSET * 0.5;
        }

        v.setOnPriorityShoulder(true);
        v.setPriorityYieldOffsetTarget(offset);
    }

    private void releasePriorityYield(Vehicle v) {
        v.setYieldingForPriority(false);
        v.setPriorityYieldOffsetTarget(0);

        Lane original = v.getPriorityYieldOriginalLane();
        if (original == null) return;
        if (v.getCurrentLane() == original) v.clearPriorityYieldLane();
    }

    private void handleIntersectionYielding() {
        scene.getVehicles().forEach(v -> v.setYieldingAtIntersection(false));

        for (Intersection inter : scene.getIntersections()) {
            List<Vehicle> active = new ArrayList<>();
            List<Vehicle> approaching = new ArrayList<>();
            for (Vehicle v : scene.getVehicles()) {
                if (isInsideConflictZone(v, inter)) {
                    active.add(v);
                    continue;
                }

                Lane lane = v.getCurrentLane();
                if (lane == null || !laneEndsAtIntersection(lane, inter)) continue;

                double frontGap = lane.distanceToStopLine(v) - v.getLength() * 0.5;
                if (frontGap < -SimConfig.MIN_VEHICLE_GAP
                        || frontGap > SimConfig.INTERSECTION_YIELD_RANGE) continue;
                if (isStoppedByLight(v, lane)) continue;
                approaching.add(v);
            }

            List<Vehicle> priorityNear = new ArrayList<>();
            for (Vehicle v : active) {
                if (v.isPriorityVehicle()) priorityNear.add(v);
            }
            for (Vehicle v : approaching) {
                if (v.isPriorityVehicle() && distanceToIntersection(v, inter) <= SimConfig.PRIORITY_YIELD_RANGE) {
                    priorityNear.add(v);
                }
            }
            if (!priorityNear.isEmpty()) {
                for (Vehicle v : active) {
                    if (!v.isPriorityVehicle() && shouldYieldToAnyPriorityAtIntersection(v, priorityNear, inter)) {
                        v.setYieldingAtIntersection(true);
                    }
                }
                for (Vehicle v : approaching) {
                    if (!v.isPriorityVehicle() && shouldYieldToAnyPriorityAtIntersection(v, priorityNear, inter)) {
                        v.setYieldingAtIntersection(true);
                    }
                }
            }

            if (approaching.isEmpty()) continue;

            if (!active.isEmpty()) {
                for (Vehicle v : approaching) {
                    if (!v.isPriorityVehicle() && conflictsWithAny(v, active, inter)) {
                        v.setYieldingAtIntersection(true);
                    }
                }
                continue;
            }

            List<Vehicle> contenders = approaching.stream()
                    .filter(v -> distanceToIntersection(v, inter) <= SimConfig.INTERSECTION_ENTRY_DECISION_RANGE)
                    .toList();
            if (contenders.size() < 2) continue;

            Vehicle winner = highestRightOfWay(contenders, inter);
            if (winner == null) continue;

            for (Vehicle v : contenders) {
                if (v == winner) continue;
                if (!v.isPriorityVehicle() && shouldYieldToIntersectionVehicle(v, winner, inter)) {
                    v.setYieldingAtIntersection(true);
                }
            }
        }
    }

    private boolean isStoppedByLight(Vehicle v, Lane lane) {
        return lane.getTrafficLight() != null
                && v.getBehavior().shouldStopAtRedLight(v, lane)
                && !v.hasPassedStopLine();
    }

    private boolean isInsideConflictZone(Vehicle v, Intersection inter) {
        double d = Math.hypot(v.getX() - inter.getCx(), v.getY() - inter.getCy());
        return d < inter.getRadius() * (v.isFollowingPath() ? 1.8 : 1.1);
    }

    private boolean conflictsWithAny(Vehicle v, List<Vehicle> active, Intersection inter) {
        return active.stream().anyMatch(other -> shouldYieldToIntersectionVehicle(v, other, inter));
    }

    private boolean shouldYieldToAnyPriorityAtIntersection(Vehicle v, List<Vehicle> priority, Intersection inter) {
        return priority.stream().anyMatch(p -> shouldYieldToPriorityAtIntersection(v, p, inter));
    }

    private boolean shouldYieldToPriorityAtIntersection(Vehicle v, Vehicle priority, Intersection inter) {
        if (v == priority || v.isPriorityVehicle() || !priority.isPriorityVehicle()) return false;

        double dist = v.distanceTo(priority);
        boolean vNear = v.isFollowingPath() || inter.contains(v)
                || distanceToIntersection(v, inter) <= SimConfig.INTERSECTION_YIELD_RANGE;
        boolean pNear = priority.isFollowingPath() || inter.contains(priority)
                || distanceToIntersection(priority, inter) <= SimConfig.PRIORITY_YIELD_RANGE;
        if (!vNear || !pNear || dist > SimConfig.PRIORITY_YIELD_RANGE) return false;

        Lane lane = v.getCurrentLane();
        Lane priorityLane = priority.getCurrentLane();
        if (lane != null && priorityLane != null && sameTravelGroup(lane, priorityLane)) {
            double dx = v.getX() - priority.getX();
            double dy = v.getY() - priority.getY();
            double ahead = dx * priority.getMoveX() + dy * priority.getMoveY();
            return ahead > -v.getLength() && ahead < SimConfig.PRIORITY_YIELD_RANGE;
        }

        return shouldYieldToIntersectionVehicle(v, priority, inter) || dist < SimConfig.INTERSECTION_YIELD_RANGE;
    }

    private boolean shouldYieldToIntersectionVehicle(Vehicle v, Vehicle other, Intersection inter) {
        if (v.isPriorityVehicle()) return false;
        if (v == other) return false;
        Lane lane = v.getCurrentLane();
        Lane otherLane = other.getCurrentLane();
        if (lane == null || otherLane == null) return false;
        if (sameTravelGroup(lane, otherLane)) return false;

        double dot = lane.getDirX() * otherLane.getDirX() + lane.getDirY() * otherLane.getDirY();
        if (!other.isPriorityVehicle()
                && bothLightsGreen(lane, otherLane)
                && dot < -0.92
                && canOppositeGreenRunTogether(v, other)) {
            return false;
        }
        if (other.isPriorityVehicle()) return true;
        if (other.isFollowingPath() || inter.contains(other)) return true;
        return compareRightOfWay(v, other, inter) < 0;
    }

    private boolean bothLightsGreen(Lane a, Lane b) {
        return a.getTrafficLight() != null && b.getTrafficLight() != null
                && a.getTrafficLight().isGreen() && b.getTrafficLight().isGreen();
    }

    private boolean canOppositeGreenRunTogether(Vehicle a, Vehicle b) {
        return a.getGlobalTurnPreference() != Vehicle.TurnIntent.LEFT
                && b.getGlobalTurnPreference() != Vehicle.TurnIntent.LEFT;
    }

    private Vehicle highestRightOfWay(List<Vehicle> vehicles, Intersection inter) {
        return vehicles.stream()
                .max((a, b) -> compareRightOfWay(a, b, inter))
                .orElse(null);
    }

    private int compareRightOfWay(Vehicle a, Vehicle b, Intersection inter) {
        int rankA = rightOfWayRank(a);
        int rankB = rightOfWayRank(b);
        if (rankA != rankB) return Integer.compare(rankA, rankB);

        double da = distanceToIntersection(a, inter);
        double db = distanceToIntersection(b, inter);
        if (Math.abs(da - db) > 0.01) return Double.compare(db, da);
        return a.getId().compareTo(b.getId()) <= 0 ? 1 : -1;
    }

    private int rightOfWayRank(Vehicle v) {
        if (v.isPriorityVehicle()) return 50;

        String behaviorName = v.getBehavior() == null ? "" : v.getBehavior().getName();
        if ("EmergencyDriver".equals(behaviorName)) return 50;
        if ("AggressiveDriver".equals(behaviorName)) return 40;
        if ("NormalDriver".equals(behaviorName)) return 30;

        return switch (v.getShortName()) {
            case "Car", "Bus" -> 20;
            case "Moto" -> 10;
            default -> 5;
        };
    }

    private double distanceToIntersection(Vehicle v, Intersection inter) {
        if (v.isFollowingPath() || inter.contains(v)) return 0;
        Lane lane = v.getCurrentLane();
        if (lane != null && laneEndsAtIntersection(lane, inter)) {
            return Math.max(0, lane.distanceToStopLine(v) - v.getLength() * 0.5);
        }
        return Math.max(0, Math.hypot(v.getX() - inter.getCx(), v.getY() - inter.getCy()) - inter.getRadius());
    }

    private void handleIntersectionRouting() {
        for (Vehicle v : scene.getVehicles()) {
            if (!v.isFollowingPath()) {
                Intersection last = routedIntersections.get(v.getId());
                if (last != null && !last.contains(v)) {
                    routedIntersections.remove(v.getId());
                }

                for (Intersection inter : scene.getIntersections()) {
                    if (!inter.contains(v)) continue;
                    if (routedIntersections.get(v.getId()) == inter) continue;

                    Lane lane = v.getCurrentLane();
                    if (lane == null || !laneEndsAtIntersection(lane, inter)) continue;

                    double dist = lane.distanceToStopLine(v) - v.getLength() * 0.5;

                    // 1. Assign exit lane early (35m)
                    if (dist <= 35 && v.getUpcomingIntersection() != inter) {
                        Lane exitLane = chooseExitLane(v, inter);
                        Vehicle.TurnIntent intent = calculateActualTurnIntent(inter, lane, exitLane);
                        v.setUpcomingExit(inter, exitLane, intent);
                    }

                    // 2. Signal logic (30m)
                    if (dist <= 30 && v.getUpcomingIntersection() == inter) {
                        applyApproachSignals(v, inter);
                    }

                    // 3. Actually enter the intersection
                    if (dist <= SimConfig.MIN_VEHICLE_GAP) {
                        Lane exitLane = v.getUpcomingExitLane();
                        if (exitLane == null) {
                            exitLane = chooseExitLane(v, inter);
                            v.setUpcomingExit(inter, exitLane, calculateActualTurnIntent(inter, lane, exitLane));
                        }
                        
                        if (beginCurvedRoute(v, inter, exitLane)) {
                            routedIntersections.put(v.getId(), inter);
                        } else {
                            redirectVehicle(v, exitLane);
                            routedIntersections.put(v.getId(), inter);
                        }
                    }
                }
            } else {
                Intersection inter = routedIntersections.get(v.getId());
                if (inter != null) {
                    applyInsideIntersectionSignals(v, inter);
                }
            }
        }
    }

    private Vehicle.TurnIntent calculateActualTurnIntent(Intersection inter, Lane entryLane, Lane exitLane) {
        if (entryLane == null || exitLane == null) return Vehicle.TurnIntent.STRAIGHT;
        double dot = entryLane.getDirX() * exitLane.getDirX() + entryLane.getDirY() * exitLane.getDirY();
        double cross = entryLane.getDirX() * exitLane.getDirY() - entryLane.getDirY() * exitLane.getDirX();
        if (dot < -0.92) return Vehicle.TurnIntent.UTURN;
        if (dot > 0.92) return Vehicle.TurnIntent.STRAIGHT;
        if (cross > 0) return Vehicle.TurnIntent.RIGHT;
        return Vehicle.TurnIntent.LEFT;
    }

    private void applyApproachSignals(Vehicle v, Intersection inter) {
        if (v.isOvertaking() || v.isYieldingForPriority()) return;

        if (v.getActiveTurnIntent() == null) return;
        
        // Roundabout specific logic based on user rules
        if (inter instanceof FiveWayIntersection) {
            // All vehicles: "vào vòng xuyến xin nhan phải"
            v.setSignalRight(true);
            return;
        }
        
        // Normal logic: "muốn rẽ hướng nào xin nhan hướng đó"
        switch (v.getActiveTurnIntent()) {
            case LEFT, UTURN -> v.setSignalLeft(true);
            case RIGHT -> v.setSignalRight(true);
            case STRAIGHT -> {
                if (!v.isSignalLeftOn() && !v.isSignalRightOn()) v.stopSignal();
            }
        }
    }

    private void applyInsideIntersectionSignals(Vehicle v, Intersection inter) {
        if (v.isOvertaking() || v.isYieldingForPriority()) return;

        if (inter instanceof FiveWayIntersection) {
            // All vehicles: "ra vòng xuyến xin nhan phải"
            v.setSignalRight(true);
            return;
        }
        
        // Inside the intersection, just keep the approach signals on
        applyApproachSignals(v, inter);
    }

    private Lane chooseExitLane(Vehicle v, Intersection inter) {
        if (inter instanceof FiveWayIntersection) {
            return chooseFiveWayExitLane(v, inter);
        }
        Direction exitDir = desiredExitDirection(v, inter);
        return findOutboundLane(inter, exitDir, v.getPreferredLaneIndex(), v.getCurrentLane());
    }

    private Direction desiredExitDirection(Vehicle v, Intersection inter) {
        Direction desired = switch (v.getGlobalTurnPreference()) {
            case LEFT -> v.getDirection().turnLeft();
            case RIGHT -> v.getDirection().turnRight();
            case STRAIGHT -> v.getDirection();
            case UTURN -> v.getDirection().opposite();
        };
        if (inter.getArms().contains(desired) && desired != v.getDirection().opposite()) {
            return desired;
        }
        return inter.randomExitDirection(v.getDirection(), rng);
    }

    private boolean beginCurvedRoute(Vehicle v, Intersection inter, Lane exitLane) {
        if (exitLane == null || exitLane == v.getCurrentLane()) return false;
        List<double[]> path = inter instanceof FiveWayIntersection
                ? buildRoundaboutPath(v, inter, exitLane)
                : buildTurnPath(v, inter, exitLane);
        boolean started = v.beginPath(exitLane, path);
        return started;
    }

    private boolean isTurnRoute(Lane entryLane, Lane exitLane) {
        if (entryLane == null || exitLane == null) return false;
        double dot = entryLane.getDirX() * exitLane.getDirX() + entryLane.getDirY() * exitLane.getDirY();
        return dot < 0.92;
    }

    private List<double[]> buildTurnPath(Vehicle v, Intersection inter, Lane exitLane) {
        List<double[]> pts = new ArrayList<>();
        double sx = v.getX(), sy = v.getY();
        double inDx = v.getMoveX(), inDy = v.getMoveY();
        double outDx = exitLane.getDirX(), outDy = exitLane.getDirY();
        double exitOffset = Math.min(exitLane.getLength(),
                inter.getRadius() + v.getLength() * 1.25 + SimConfig.MIN_VEHICLE_GAP * 2);
        double[] end = exitLane.pointAt(exitOffset);
        double distance = Math.hypot(end[0] - sx, end[1] - sy);
        double dot = inDx * outDx + inDy * outDy;
        double cross = inDx * outDy - inDy * outDx;

        pts.add(new double[]{sx, sy});
        if (dot > 0.92) {
            addLineSamples(pts, sx, sy, end[0], end[1], 20);
            return pts;
        }

        if (cross > 0.25) {
            double leadIn = Math.min(inter.getRadius() * 0.42, distance * 0.24);
            double leadOut = Math.min(inter.getRadius() * 0.46, distance * 0.28);
            double csx = sx + inDx * leadIn;
            double csy = sy + inDy * leadIn;
            double cex = end[0] - outDx * leadOut;
            double cey = end[1] - outDy * leadOut;
            double ctrl = Math.max(10, Math.min(inter.getRadius() * 0.58, distance * 0.32));

            addLineSamples(pts, sx, sy, csx, csy, 4);
            addCubicSamples(pts,
                    csx, csy,
                    csx + inDx * ctrl, csy + inDy * ctrl,
                    cex - outDx * ctrl, cey - outDy * ctrl,
                    cex, cey,
                    28);
            addLineSamples(pts, cex, cey, end[0], end[1], 5);
        } else {
            double ctrl = Math.max(inter.getRadius() * 0.85, distance * 0.42);
            addCubicSamples(pts,
                    sx, sy,
                    sx + inDx * ctrl, sy + inDy * ctrl,
                    end[0] - outDx * ctrl, end[1] - outDy * ctrl,
                    end[0], end[1],
                    34);
        }
        return pts;
    }

    private List<double[]> buildRoundaboutPath(Vehicle v, Intersection inter, Lane exitLane) {
        List<double[]> pts = new ArrayList<>();
        double cx = inter.getCx(), cy = inter.getCy();
        double sx = v.getX(), sy = v.getY();
        double exitOffset = Math.min(exitLane.getLength(),
                inter.getRadius() + v.getLength() * 0.9 + SimConfig.MIN_VEHICLE_GAP);
        double[] end = exitLane.pointAt(exitOffset);
        
        // Lane-specific ring radius so vehicles don't overlap in roundabout
        int laneIdx = exitLane.getLaneIndex();
        double ringR = 64 + laneIdx * SimConfig.LANE_WIDTH;

        double entryA = Math.atan2(sy - cy, sx - cx);
        double exitA = Math.atan2(end[1] - cy, end[0] - cx);
        double delta = entryA - exitA;
        while (delta <= 0.35) delta += Math.PI * 2;

        pts.add(new double[]{sx, sy});
        addLineSamples(pts, sx, sy, cx + Math.cos(entryA) * ringR, cy + Math.sin(entryA) * ringR, 4);

        int arcSteps = Math.max(14, (int)Math.ceil(delta / (Math.PI / 18)));
        for (int i = 1; i <= arcSteps; i++) {
            double a = entryA - delta * i / arcSteps;
            pts.add(new double[]{cx + Math.cos(a) * ringR, cy + Math.sin(a) * ringR});
        }

        double lastX = pts.get(pts.size() - 1)[0];
        double lastY = pts.get(pts.size() - 1)[1];
        addLineSamples(pts, lastX, lastY, end[0], end[1], 5);
        return pts;
    }

    private void addLineSamples(List<double[]> pts, double x1, double y1, double x2, double y2, int steps) {
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            pts.add(new double[]{x1 + (x2 - x1) * t, y1 + (y2 - y1) * t});
        }
    }

    private void addCubicSamples(List<double[]> pts,
                                 double x0, double y0,
                                 double x1, double y1,
                                 double x2, double y2,
                                 double x3, double y3,
                                 int steps) {
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double u = 1 - t;
            double x = u*u*u*x0 + 3*u*u*t*x1 + 3*u*t*t*x2 + t*t*t*x3;
            double y = u*u*u*y0 + 3*u*u*t*y1 + 3*u*t*t*y2 + t*t*t*y3;
            pts.add(new double[]{x, y});
        }
    }

    private void redirectVehicle(Vehicle v, Lane exitLane) {
        if (exitLane == null || exitLane == v.getCurrentLane()) return;

        Lane oldLane = v.getCurrentLane();
        if (oldLane != null) oldLane.removeVehicle(v);
        exitLane.addVehicle(v);
        v.setDirection(exitLane.getDirection());
        v.resetPassedStopLine();

        double startOffset = Math.max(v.getLength() * 0.5 + SimConfig.MIN_VEHICLE_GAP, 1);
        double[] p = exitLane.pointAt(startOffset);
        v.setX(p[0]);
        v.setY(p[1]);
    }

    private Lane findOutboundLane(Intersection inter, Direction exitDir, int preferredIdx, Lane entryLane) {
        Lane bestLane = null;
        double bestScore = Double.MAX_VALUE;
        for (Road road : scene.getRoads()) {
            for (Lane lane : road.getLanesForDirection(exitDir)) {
                if (!laneStartsAtIntersection(lane, inter)) continue;
                if (isUTurn(entryLane, lane)) continue;
                double d = Math.hypot(lane.getStartX() - inter.getCx(), lane.getStartY() - inter.getCy());
                double score = d + (lane.getLaneIndex() == preferredIdx ? 0 : 1000);
                if (score < bestScore) {
                    bestScore = score;
                    bestLane = lane;
                }
            }
        }
        return bestLane;
    }

    private Lane chooseFiveWayExitLane(Vehicle v, Intersection inter) {
        List<Lane> candidates = new ArrayList<>();
        int preferredIdx = v.getPreferredLaneIndex();
        for (Road road : scene.getRoads()) {
            for (Lane lane : road.getLanes()) {
                if (!laneStartsAtIntersection(lane, inter)) continue;
                if (isUTurn(v.getCurrentLane(), lane)) continue;
                if (lane.getLaneIndex() == preferredIdx) candidates.add(lane);
            }
        }

        if (candidates.isEmpty()) {
            for (Road road : scene.getRoads()) {
                for (Lane lane : road.getLanes()) {
                    if (laneStartsAtIntersection(lane, inter) && !isUTurn(v.getCurrentLane(), lane)) {
                        candidates.add(lane);
                    }
                }
            }
        }

        return candidates.isEmpty() ? null : candidates.get(rng.nextInt(candidates.size()));
    }

    private void handleLaneSelectionForTurning() {
        for (Vehicle v : scene.getVehicles()) {
            if (v.isFollowingPath() || v.isOvertaking() || v.isYieldingForPriority() || v.laneChangeSignalTimer > 0) continue;
            Lane lane = v.getCurrentLane();
            if (lane == null) continue;

            double dist = lane.distanceToStopLine(v);
            if (dist > 30 && dist < 150) { // Prep zone
                int desiredLaneIdx = v.getPreferredLaneIndex();
                if (v.getGlobalTurnPreference() == Vehicle.TurnIntent.LEFT) desiredLaneIdx = 0; // Inner lane
                else if (v.getGlobalTurnPreference() == Vehicle.TurnIntent.RIGHT) desiredLaneIdx = 2; // Outer-most lane

                if (lane.getLaneIndex() != desiredLaneIdx) {
                    Lane target = (lane.getLaneIndex() < desiredLaneIdx) ? lane.getRightSibling() : lane.getLeftSibling();
                    if (target != null) {
                        v.performSmoothLaneChange(target);
                    }
                }
            }
        }
    }

    private void handleOvertaking() {
        for (Vehicle v : scene.getVehicles()) {
            if (v.isOvertaking() || v.isFollowingPath() || v.getCurrentLane() == null
                    || v.isYieldingForPriority() || v.isYieldingAtIntersection()) continue;
            if (!canOvertakeByBehavior(v)) continue;
            Vehicle front = v.getCurrentLane().getFrontVehicle(v);
            if (front == null) continue;

            double gap = v.longitudinalGapTo(front);
            boolean blocked = gap < SimConfig.SAFE_FOLLOW_DISTANCE * 1.5
                    && front.getSpeed() < v.getMaxSpeed() * 0.4;
            if (!blocked) continue;

            if (v.isPriorityVehicle()) {
                if (!v.tryOvertake()) v.tryEmergencyOvertake();
            } else if (v.getBehavior().shouldOvertake(v, front, adjacentOvertakeLane(v))) {
                v.tryOvertake();
            }
        }
    }

    private boolean canOvertakeByBehavior(Vehicle v) {
        if (v.isPriorityVehicle()) return true;
        String behaviorName = v.getBehavior() == null ? "" : v.getBehavior().getName();
        return "EmergencyDriver".equals(behaviorName) || "AggressiveDriver".equals(behaviorName);
    }

    private Lane adjacentOvertakeLane(Vehicle v) {
        Lane lane = v.getCurrentLane();
        if (lane == null) return null;
        Lane adj = lane.getLeftSibling();
        return adj != null ? adj : lane.getRightSibling();
    }

    private boolean sameTravelGroup(Lane a, Lane b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        Lane left = a.getLeftSibling();
        while (left != null) { if (left == b) return true; left = left.getLeftSibling(); }
        Lane right = a.getRightSibling();
        while (right != null) { if (right == b) return true; right = right.getRightSibling(); }
        return false;
    }

    private boolean laneEndsAtIntersection(Lane lane, Intersection inter) {
        double d = Math.hypot(lane.getEndX() - inter.getCx(), lane.getEndY() - inter.getCy());
        return d < inter.getRadius() + SimConfig.LANE_WIDTH * 2.5;
    }

    private boolean laneStartsAtIntersection(Lane lane, Intersection inter) {
        double d = Math.hypot(lane.getStartX() - inter.getCx(), lane.getStartY() - inter.getCy());
        return d < inter.getRadius() + SimConfig.LANE_WIDTH * 2.5;
    }

    private boolean isUTurn(Lane entryLane, Lane exitLane) {
        if (entryLane == null || exitLane == null) return false;
        double dot = entryLane.getDirX() * exitLane.getDirX() + entryLane.getDirY() * exitLane.getDirY();
        return dot < -0.92;
    }

    private void removeOutOfBounds() {
        double m = 70, W = SimConfig.CANVAS_WIDTH, H = SimConfig.CANVAS_HEIGHT;
        for (Vehicle v : scene.getVehicles()) {
            if (v.getX() < -m || v.getX() > W + m || v.getY() < -m || v.getY() > H + m) {
                routedIntersections.remove(v.getId());
                scene.removeVehicle(v);
            }
        }
    }

    public void handleLightClick(double wx, double wy) {
        for (Intersection inter : scene.getIntersections()) {
            for (TrafficLight tl : inter.getTrafficLights()) {
                if (Math.hypot(tl.getX() - wx, tl.getY() - wy) < 18) {
                    inter.manualAdvance();
                    return;
                }
            }
        }
    }

    public boolean handleVehicleClick(double wx, double wy) {
        Vehicle clicked = null;
        double minDistance = 20.0; // Click tolerance radius in world pixels
        for (Vehicle v : scene.getVehicles()) {
            double dist = Math.hypot(v.getX() - wx, v.getY() - wy);
            if (dist < minDistance) {
                clicked = v;
                minDistance = dist;
            }
        }
        if (clicked != null) {
            routedIntersections.remove(clicked.getId());
            scene.removeVehicle(clicked);
            return true;
        }
        return false;
    }

    public SimScene getScene() {
        return scene;
    }
}
