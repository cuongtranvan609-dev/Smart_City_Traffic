package com.trafficsim.model.driver;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.road.Lane;
import com.trafficsim.model.vehicle.Vehicle;

public class CautiousDriver implements DrivingBehavior {
    @Override
    public double computeAcceleration(Vehicle self, Lane lane, Vehicle front, double dt) {
        double desired = self.getMaxSpeed() * 0.75;
        if (front != null) {
            double gap = self.longitudinalGapTo(front);
            if (gap < SimConfig.SAFE_FOLLOW_DISTANCE * 2)
                return -SimConfig.DEFAULT_DECELERATION * 0.8;
            if (gap < SimConfig.BRAKE_DISTANCE * 1.5)
                desired = front.getSpeed() * (gap / (SimConfig.BRAKE_DISTANCE * 1.5));
        }
        double delta = desired - self.getSpeed();
        if (delta > 0) return Math.min(delta/dt, SimConfig.DEFAULT_ACCELERATION * 0.6);
        else           return Math.max(delta/dt, -SimConfig.DEFAULT_DECELERATION * 0.7);
    }

    @Override
    public boolean shouldStopAtRedLight(Vehicle self, Lane lane) {
        var tl = lane.getTrafficLight();
        return tl != null && !tl.isGreen();
    }

    @Override public boolean shouldOvertake(Vehicle self, Vehicle front, Lane adj) { return false; }
    @Override public String getName() { return "CautiousDriver"; }
}
