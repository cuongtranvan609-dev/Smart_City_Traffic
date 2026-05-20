package com.trafficsim.model.road;

import com.trafficsim.model.Direction;
import com.trafficsim.model.TrafficLight;
import com.trafficsim.model.vehicle.Vehicle;
import java.util.*;

/**
 * A single-direction lane.
 * laneIndex 0 = inner lane (cars, buses)
 * laneIndex 1 = outer lane (motorbikes, bicycles)
 */
public class Lane {
    private final Direction dir;
    private final double startX, startY, endX, endY;
    private final double dirX, dirY;
    private final double rightX, rightY;
    private final double length;
    private final double angleDeg;
    private final double width;
    private final int    laneIndex;
    private TrafficLight trafficLight;
    private double       stopLineX, stopLineY;
    private boolean      stopLineSet = false;

    // Sibling lanes: adjacent lanes this vehicle can use for overtaking
    private Lane leftSibling;
    private Lane rightSibling;
    private Lane oppositeLane;

    private final List<Vehicle> vehicles = new ArrayList<>();

    public Lane(double startX, double startY, double endX, double endY,
                double width, Direction dir, int laneIndex) {
        this.startX = startX; this.startY = startY;
        this.endX   = endX;   this.endY   = endY;
        this.width  = width;  this.dir    = dir;
        this.laneIndex = laneIndex;
        double dx = endX - startX;
        double dy = endY - startY;
        double len = Math.hypot(dx, dy);
        if (len < 0.001) {
            this.dirX = dir.dx;
            this.dirY = dir.dy;
            this.length = 0;
        } else {
            this.dirX = dx / len;
            this.dirY = dy / len;
            this.length = len;
        }
        this.rightX = -this.dirY;
        this.rightY =  this.dirX;
        this.angleDeg = Math.toDegrees(Math.atan2(this.dirY, this.dirX));
        this.stopLineX = endX; this.stopLineY = endY;
    }

    /**
     * Signed distance along lane direction from vehicle to stop line.
     * Positive = stop line is ahead; negative = already passed it.
     */
    public double distanceToStopLine(Vehicle v) {
        double dx = stopLineX - v.getX();
        double dy = stopLineY - v.getY();
        return dx * dirX + dy * dirY;
    }

    public double distanceToEnd(Vehicle v) {
        double dx = endX - v.getX();
        double dy = endY - v.getY();
        return dx * dirX + dy * dirY;
    }

    public double signedDistanceAlong(Vehicle from, Vehicle to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        return dx * dirX + dy * dirY;
    }

    public double longitudinalGap(Vehicle self, Vehicle front) {
        return signedDistanceAlong(self, front) - (self.getLength() + front.getLength()) * 0.5;
    }

    public double projectDistance(double x, double y) {
        return (x - startX) * dirX + (y - startY) * dirY;
    }

    public double[] pointAt(double distance) {
        double s = Math.max(0, Math.min(length, distance));
        return new double[]{ startX + dirX * s, startY + dirY * s };
    }

    public boolean hasSpaceFor(Vehicle v) {
        for (Vehicle e : vehicles) {
            if (e == v) continue;
            double gap = e.distanceTo(v) - Math.max(e.getLength(), v.getLength()) * 0.5;
            if (gap < 6) return false;
        }
        return true;
    }

    public boolean hasSpaceNear(double x, double y, double minGap) {
        for (Vehicle e : vehicles) {
            double dx = e.getX()-x, dy = e.getY()-y;
            if (Math.sqrt(dx*dx+dy*dy) < minGap) return false;
        }
        return true;
    }

    public void addVehicle(Vehicle v) {
        if (!vehicles.contains(v)) vehicles.add(v);
        v.setCurrentLane(this);
    }

    public void removeVehicle(Vehicle v) { vehicles.remove(v); }

    /** Nearest vehicle ahead of self in this lane. */
    public Vehicle getFrontVehicle(Vehicle self) {
        Vehicle nearest = null; double minDist = Double.MAX_VALUE;
        for (Vehicle o : vehicles) {
            if (o == self) continue;
            if (o.isOnPriorityShoulder()) continue;
            double relX = o.getX() - self.getX(), relY = o.getY() - self.getY();
            double dot  = relX * self.getMoveX() + relY * self.getMoveY();
            if (dot > 0) {
                double d = Math.sqrt(relX*relX + relY*relY);
                if (d < minDist) { minDist = d; nearest = o; }
            }
        }
        return nearest;
    }

    public List<Vehicle> getVehicles() { return Collections.unmodifiableList(vehicles); }

    // Sibling lane linkage (set up by SceneBuilder)
    public void setLeftSibling(Lane l)    { this.leftSibling = l; }
    public void setRightSibling(Lane l)   { this.rightSibling = l; }
    public void setOppositeLane(Lane l)   { this.oppositeLane = l; }

    public Lane getLeftSibling()          { return leftSibling; }
    public Lane getRightSibling()         { return rightSibling; }
    public Lane getOppositeLane()         { return oppositeLane; }

    // Getters
    public Direction    getDirection()    { return dir; }
    public double       getDirX()         { return dirX; }
    public double       getDirY()         { return dirY; }
    public double       getRightNormalX() { return rightX; }
    public double       getRightNormalY() { return rightY; }
    public double       getLength()       { return length; }
    public double       getAngleDeg()     { return angleDeg; }
    public double       getStartX()       { return startX; }
    public double       getStartY()       { return startY; }
    public double       getEndX()         { return endX; }
    public double       getEndY()         { return endY; }
    public double       getWidth()        { return width; }
    public int          getLaneIndex()    { return laneIndex; }
    public TrafficLight getTrafficLight() { return trafficLight; }
    public void         setTrafficLight(TrafficLight tl) { this.trafficLight = tl; }
    public void         setStopLine(double x, double y)  { stopLineX=x; stopLineY=y; stopLineSet=true; }
    public double       getStopLineX()   { return stopLineX; }
    public double       getStopLineY()   { return stopLineY; }
    public boolean      isStopLineSet()  { return stopLineSet; }
}
