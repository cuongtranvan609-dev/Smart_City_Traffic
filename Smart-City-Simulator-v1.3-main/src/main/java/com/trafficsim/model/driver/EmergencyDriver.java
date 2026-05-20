package com.trafficsim.model.driver;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.road.Lane;
import com.trafficsim.model.vehicle.Vehicle;

public class EmergencyDriver implements DrivingBehavior {
    @Override
    public double computeAcceleration(Vehicle self, Lane lane, Vehicle front, double dt) {
        double desired = self.getMaxSpeed() * 1.5;
        if (front != null) {
            double gap = self.longitudinalGapTo(front);
            if (gap < SimConfig.SAFE_FOLLOW_DISTANCE * 0.6)
                return -SimConfig.DEFAULT_DECELERATION * 2.0;
        }
        double delta = desired - self.getSpeed();
        if (delta > 0) return Math.min(delta/dt, SimConfig.DEFAULT_ACCELERATION * 2.0);
        else           return Math.max(delta/dt, -SimConfig.DEFAULT_DECELERATION * 1.5);
    }

    @Override public boolean shouldStopAtRedLight(Vehicle self, Lane lane) { return false; }
    @Override public boolean shouldOvertake(Vehicle self, Vehicle front, Lane adj) { return true; }
    @Override public String getName() { return "EmergencyDriver"; }
}
