package com.trafficsim.model.road;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.Direction;
import java.util.*;

/**
 * Two-way road with 2 lanes per direction (4 total).
 * laneIndex 0 = inner (cars/buses), laneIndex 1 = outer (motorbikes/bicycles).
 * Lanes are offset perpendicular to road direction.
 */
public class Road {
    private final double x1, y1, x2, y2;
    private final boolean horizontal;
    private final double dirX, dirY;
    private final double rightX, rightY;
    private final List<Lane> lanes = new ArrayList<>();

    public Road(double x1, double y1, double x2, double y2) {
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 0.001) {
            this.dirX = 1;
            this.dirY = 0;
        } else {
            this.dirX = dx / len;
            this.dirY = dy / len;
        }
        this.rightX = -this.dirY;
        this.rightY =  this.dirX;
        horizontal = Math.abs(y2 - y1) < Math.abs(x2 - x1);
        buildLanes();
    }

    private void buildLanes() {
        double lw = SimConfig.LANE_WIDTH;
        Direction aDir = Direction.fromVector(dirX, dirY);
        Direction bDir = Direction.fromVector(-dirX, -dirY);

        addLanePair(x1, y1, x2, y2, rightX, rightY, aDir);
        addLanePair(x2, y2, x1, y1, -rightX, -rightY, bDir);
    }

    private void addLanePair(double sx, double sy, double ex, double ey,
                             double rx, double ry, Direction dir) {
        lanes.add(new Lane(sx + rx * 9.0, sy + ry * 9.0,
                           ex + rx * 9.0, ey + ry * 9.0,
                           18.0, dir, 0));
        lanes.add(new Lane(sx + rx * 27.0, sy + ry * 27.0,
                           ex + rx * 27.0, ey + ry * 27.0,
                           18.0, dir, 1));
        lanes.add(new Lane(sx + rx * 45.0, sy + ry * 45.0,
                           ex + rx * 45.0, ey + ry * 45.0,
                           18.0, dir, 2));
        lanes.add(new Lane(sx + rx * 59.0, sy + ry * 59.0,
                           ex + rx * 59.0, ey + ry * 59.0,
                           10.0, dir, 3));
    }

    public List<Lane> getLanesForDirection(Direction dir) {
        List<Lane> res = new ArrayList<>();
        for (Lane l : lanes) if (l.getDirection() == dir) res.add(l);
        return res;
    }

    /** laneIndex 0=inner, 1=outer */
    public Lane getLane(Direction dir, int laneIndex) {
        for (Lane l : lanes)
            if (l.getDirection() == dir && l.getLaneIndex() == laneIndex) return l;
        return null;
    }

    public List<Lane> getLanes() { return lanes; }
    public double getX1()        { return x1; }
    public double getY1()        { return y1; }
    public double getX2()        { return x2; }
    public double getY2()        { return y2; }
    public boolean isHorizontal(){ return horizontal; }
    public double getDirX()      { return dirX; }
    public double getDirY()      { return dirY; }
    public double getRightX()    { return rightX; }
    public double getRightY()    { return rightY; }
    public double getHalfWidth() { return SimConfig.ROAD_HALF_WIDTH; }
}
