package com.trafficsim.model.driver;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.road.Lane;
import com.trafficsim.model.vehicle.Vehicle;

/**
 * Normal driver: obeys lights (handled by Vehicle.update), keeps safe distance.
 * computeAcceleration is ONLY about following distance – not lights.
 * Light braking is done in Vehicle.update() before calling here.
 */
public class NormalDriver implements DrivingBehavior {
    @Override
    public double computeAcceleration(Vehicle self, Lane lane, Vehicle front, double dt) {
        double desired = self.getMaxSpeed();
        if (front != null) {
            double gap = self.longitudinalGapTo(front);
            if (gap < SimConfig.SAFE_FOLLOW_DISTANCE) {
                // Emergency stop – hard brake
                return -SimConfig.DEFAULT_DECELERATION * 1.5;
            }
            if (gap < SimConfig.BRAKE_DISTANCE) {
                // Gentle following
                desired = front.getSpeed() * (gap / SimConfig.BRAKE_DISTANCE);
            }
        }
        double delta = desired - self.getSpeed();
        if (delta > 0) return Math.min(delta / dt, SimConfig.DEFAULT_ACCELERATION);
        else           return Math.max(delta / dt, -SimConfig.DEFAULT_DECELERATION);
    }

    @Override
    public boolean shouldStopAtRedLight(Vehicle self, Lane lane) {
        // Handled centrally in Vehicle.update; this method kept for compatibility
        var tl = lane.getTrafficLight();
        return tl != null && (tl.isRed() || tl.isYellow());
    }

    @Override
    public boolean shouldOvertake(Vehicle self, Vehicle front, Lane adj) {
        if (front == null || adj == null) return false;
        return front.getSpeed() < self.getMaxSpeed() * 0.4 && adj.hasSpaceFor(self);
    }

    @Override public String getName() { return "NormalDriver"; }
}
