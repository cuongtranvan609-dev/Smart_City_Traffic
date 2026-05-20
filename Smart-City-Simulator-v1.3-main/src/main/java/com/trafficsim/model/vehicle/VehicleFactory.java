package com.trafficsim.model.vehicle;

import com.trafficsim.model.Direction;
import com.trafficsim.model.driver.*;
import java.util.Random;

public class VehicleFactory {
    public enum Type { CAR, MOTORBIKE, BICYCLE, BUS, AMBULANCE, FIRE_TRUCK }

    private static final Random RNG = new Random();

    public static Vehicle create(Type type, double x, double y, Direction dir) {
        return switch (type) {
            case CAR        -> new Car(x, y, dir, randomCivilBehavior());
            case MOTORBIKE  -> new Motorbike(x, y, dir, randomCivilBehavior());
            case BICYCLE    -> new Bicycle(x, y, dir, new CautiousDriver());
            case BUS        -> new Bus(x, y, dir, new CautiousDriver());
            case AMBULANCE  -> new Ambulance(x, y, dir);
            case FIRE_TRUCK -> new FireTruck(x, y, dir);
        };
    }

    public static Vehicle createRandom(double x, double y, Direction dir) {
        Type[] civil = {Type.CAR, Type.CAR, Type.CAR,
                        Type.MOTORBIKE, Type.MOTORBIKE,
                        Type.BICYCLE, Type.BUS};
        return create(civil[RNG.nextInt(civil.length)], x, y, dir);
    }

    private static DrivingBehavior randomCivilBehavior() {
        return switch (RNG.nextInt(3)) {
            case 0  -> new NormalDriver();
            case 1  -> new AggressiveDriver();
            default -> new CautiousDriver();
        };
    }
}
