package com.trafficsim.model.driver;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.road.Lane;
import com.trafficsim.model.vehicle.Vehicle;

public class AggressiveDriver implements DrivingBehavior {
    @Override
    public double computeAcceleration(Vehicle self, Lane lane, Vehicle front, double dt) {
        double desired = self.getMaxSpeed() * 1.25;
        if (front != null) {
            double gap = self.longitudinalGapTo(front);
            if (gap < SimConfig.SAFE_FOLLOW_DISTANCE * 0.7)
                return -SimConfig.DEFAULT_DECELERATION * 1.5;
            if (gap < SimConfig.BRAKE_DISTANCE * 0.8)
                desired = front.getSpeed() * (gap / (SimConfig.BRAKE_DISTANCE * 0.8));
        }
        double delta = desired - self.getSpeed();
        if (delta > 0) return Math.min(delta/dt, SimConfig.DEFAULT_ACCELERATION * 1.5);
        else           return Math.max(delta/dt, -SimConfig.DEFAULT_DECELERATION);
    }

    @Override
    public boolean shouldStopAtRedLight(Vehicle self, Lane lane) {
        var tl = lane.getTrafficLight();
        return tl != null && !tl.isGreen();
    }

    @Override
    public boolean shouldOvertake(Vehicle self, Vehicle front, Lane adj) {
        if (front == null || adj == null) return false;
        return front.getSpeed() < self.getMaxSpeed() * 0.7 && adj.hasSpaceFor(self);
    }

    @Override public String getName() { return "AggressiveDriver"; }
}
