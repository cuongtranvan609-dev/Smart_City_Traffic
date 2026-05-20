package com.trafficsim.service;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.Direction;
import com.trafficsim.model.SimScene;
import com.trafficsim.model.intersection.*;
import com.trafficsim.model.road.Lane;
import com.trafficsim.model.road.Road;
import java.util.List;

/**
 * Builds all simulation scenes and links lane stop-lines, traffic lights,
 * and sibling/opposite lane references.
 */
public class SceneBuilder {

    public static SimScene buildThreeWay() {
        SimScene s = new SimScene(SimScene.SceneType.THREE_WAY);
        double cx = SimConfig.CANVAS_WIDTH / 2, cy = SimConfig.CANVAS_HEIGHT / 2;
        s.addRoad(new Road(0,  cy, cx, cy));
        s.addRoad(new Road(cx, cy, SimConfig.CANVAS_WIDTH, cy));
        s.addRoad(new Road(cx, 0,  cx, cy));
        s.addIntersection(new ThreeWayIntersection(cx, cy));
        finalize(s);
        return s;
    }

    public static SimScene buildFourWay() {
        SimScene s = new SimScene(SimScene.SceneType.FOUR_WAY);
        double cx = SimConfig.CANVAS_WIDTH / 2, cy = SimConfig.CANVAS_HEIGHT / 2;
        s.addRoad(new Road(0, cy, cx, cy));
        s.addRoad(new Road(cx, cy, SimConfig.CANVAS_WIDTH, cy));
        s.addRoad(new Road(cx, 0, cx, cy));
        s.addRoad(new Road(cx, cy, cx, SimConfig.CANVAS_HEIGHT));
        s.addIntersection(new FourWayIntersection(cx, cy));
        finalize(s);
        return s;
    }

    public static SimScene buildFiveWay() {
        SimScene s = new SimScene(SimScene.SceneType.FIVE_WAY);
        double cx = SimConfig.CANVAS_WIDTH / 2, cy = SimConfig.CANVAS_HEIGHT / 2;
        FiveWayIntersection inter = new FiveWayIntersection(cx, cy, false);
        s.addIntersection(inter);
        double len = 280;
        for (int i = 0; i < 5; i++) {
            double[] ep = inter.getArmEndPoint(i, len);
            s.addRoad(new Road(cx, cy, ep[0], ep[1]));
        }
        finalize(s);
        return s;
    }

    /**
     * Network: 10 intersections, genuinely mixed types.
     * Grid: 3×3 (9 nodes) + 1 extra diagonal node = 10 total.
     * Only FourWayIntersection at proper 4-road crossings,
     * ThreeWayIntersection at T-junctions, FiveWayIntersection at one special node.
     */
    public static SimScene buildNetwork() {
        SimScene s = new SimScene(SimScene.SceneType.NETWORK);
        double sx = SimConfig.NETWORK_SPACING_X;
        double sy = SimConfig.NETWORK_SPACING_Y;
        double ox = 120, oy = 100;

        // 9 node positions (pure 3x3 grid)
        double[][] n = {
            {ox,       oy},          // 0
            {ox+sx,    oy},          // 1
            {ox+sx*2,  oy},          // 2
            {ox,       oy+sy},       // 3
            {ox+sx,    oy+sy},       // 4  ← FiveWay Roundabout (center)
            {ox+sx*2,  oy+sy},       // 5
            {ox,       oy+sy*2},     // 6
            {ox+sx,    oy+sy*2},     // 7
            {ox+sx*2,  oy+sy*2},     // 8
        };

        // Connections: (a, b) means road between node a and node b
        int[][] edges = {
            {0,1},{1,2},{3,4},{4,5},{6,7},{7,8}, // horizontal main
            {0,3},{1,4},{2,5},{3,6},{4,7},{5,8}, // vertical main
        };

        // Build intersections
        Intersection[] inters = new Intersection[9];
        for (int i = 0; i < 9; i++) {
            if (i == 4) {
                inters[i] = new FiveWayIntersection(n[i][0], n[i][1], true); // true = 4-way roundabout
            } else {
                inters[i] = new FourWayIntersection(n[i][0], n[i][1]);
            }
            s.addIntersection(inters[i]);
        }

        // Build roads
        for (int[] e : edges)
            s.addRoad(new Road(n[e[0]][0], n[e[0]][1], n[e[1]][0], n[e[1]][1]));

        // Perimeter stubs for spawning/exiting
        double stub = 90;
        addStub(s, n[0], -1, 0, stub); addStub(s, n[0], 0, -1, stub);
        addStub(s, n[1], 0, -1, stub);
        addStub(s, n[2], 1, 0, stub);  addStub(s, n[2], 0, -1, stub);
        addStub(s, n[3], -1, 0, stub);
        addStub(s, n[5], 1, 0, stub);
        addStub(s, n[6], -1, 0, stub); addStub(s, n[6], 0, 1, stub);
        addStub(s, n[7], 0, 1, stub);
        addStub(s, n[8], 1, 0, stub);  addStub(s, n[8], 0, 1, stub);

        finalize(s);
        return s;
    }

    private static void addStub(SimScene s, double[] node, double dx, double dy, double len) {
        s.addRoad(new Road(node[0], node[1], node[0]+dx*len, node[1]+dy*len));
    }

    // ================================================================
    // Post-processing: stop lines, traffic lights, sibling/opposite lanes
    // ================================================================
    private static void finalize(SimScene scene) {
        linkStopLinesAndLights(scene);
        linkSiblingLanes(scene);
    }

    private static void linkStopLinesAndLights(SimScene scene) {
        for (Intersection inter : scene.getIntersections()) {
            double r   = inter.getRadius();
            double cx  = inter.getCx(), cy = inter.getCy();

            for (Road road : scene.getRoads()) {
                for (Lane lane : road.getLanes()) {
                    Direction dir = lane.getDirection();
                    // Check if lane end is close to intersection center
                    double ex = lane.getEndX(), ey = lane.getEndY();
                    double dist = Math.hypot(ex - cx, ey - cy);

                    if (dist < r + SimConfig.LANE_WIDTH * 2.5) {
                        // Stop line lies on the lane centerline, r pixels before the intersection.
                        // The longitudinal position is identical for sibling lanes, but the lateral
                        // offset must stay lane-specific so the renderer can draw one continuous
                        // white bar across all stopping lanes.
                        double slx = lane.getEndX() - lane.getDirX() * (r + 27);
                        double sly = lane.getEndY() - lane.getDirY() * (r + 27);
                        lane.setStopLine(slx, sly);

                        // Assign matching traffic light
                        var tl = inter.getType() == Intersection.Type.FIVE_WAY
                                ? nearestLight(inter, slx, sly)
                                : inter.getLightForDirection(dir);
                        if (tl != null) lane.setTrafficLight(tl);
                    }
                }
            }
        }
    }

    private static com.trafficsim.model.TrafficLight nearestLight(Intersection inter, double x, double y) {
        return inter.getTrafficLights().stream()
                .min((a, b) -> Double.compare(Math.hypot(a.getX() - x, a.getY() - y),
                                               Math.hypot(b.getX() - x, b.getY() - y)))
                .orElse(null);
    }

    /**
     * Link each lane to its sibling (other lane, same direction) and
     * opposite lane (same road, opposite direction, inner lane).
     */
    private static void linkSiblingLanes(SimScene scene) {
        for (Road road : scene.getRoads()) {
            List<Lane> eastOrSouth  = road.getLanesForDirection(road.isHorizontal() ? Direction.EAST  : Direction.SOUTH);
            List<Lane> westOrNorth  = road.getLanesForDirection(road.isHorizontal() ? Direction.WEST  : Direction.NORTH);

            // Sibling within same direction
            linkSiblings(eastOrSouth);
            linkSiblings(westOrNorth);

            // Opposite lane: inner lane of each direction gets opposite inner lane
            Lane innerA = findByIndex(eastOrSouth,  0);
            Lane innerB = findByIndex(westOrNorth,  0);
            if (innerA != null && innerB != null) {
                innerA.setOppositeLane(innerB);
                innerB.setOppositeLane(innerA);
            }
        }
    }

    private static void linkSiblings(List<Lane> lanes) {
        Lane lane0 = findByIndex(lanes, 0);
        Lane lane1 = findByIndex(lanes, 1);
        Lane lane2 = findByIndex(lanes, 2);
        Lane lane3 = findByIndex(lanes, 3);
        if (lane0 != null && lane1 != null) {
            lane0.setRightSibling(lane1);
            lane1.setLeftSibling(lane0);
        }
        if (lane1 != null && lane2 != null) {
            lane1.setRightSibling(lane2);
            lane2.setLeftSibling(lane1);
        }
        if (lane2 != null && lane3 != null) {
            lane2.setRightSibling(lane3);
            lane3.setLeftSibling(lane2);
        }
    }

    private static Lane findByIndex(List<Lane> lanes, int idx) {
        return lanes.stream().filter(l -> l.getLaneIndex() == idx).findFirst().orElse(null);
    }
}
