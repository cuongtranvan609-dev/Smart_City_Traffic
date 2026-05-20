package com.trafficsim.config;

public final class SimConfig {
    private SimConfig() {}

    public static final double WINDOW_WIDTH  = 1340;
    public static final double WINDOW_HEIGHT = 820;
    public static final double CANVAS_WIDTH  = 980;
    public static final double CANVAS_HEIGHT = 760;

    // Road geometry - 4 lanes per direction
    public static final double ROAD_HALF_WIDTH = 64; // half total road width
    public static final double LANE_WIDTH      = 16; // each lane width

    // Vehicle defaults
    public static final double DEFAULT_MAX_SPEED    = 80;
    public static final double SAFE_FOLLOW_DISTANCE = 24;
    public static final double BRAKE_DISTANCE       = 85;
    public static final double PRIORITY_YIELD_RANGE = 160;
    public static final double INTERSECTION_YIELD_RANGE = 95;
    public static final double INTERSECTION_ENTRY_DECISION_RANGE = 22;
    public static final double YIELD_SPEED_FACTOR   = 0.25;
    public static final double DEFAULT_ACCELERATION = 35;
    public static final double DEFAULT_DECELERATION = 70;
    public static final double STOP_LINE_DISTANCE   = 12;
    public static final double STOP_LINE_BUFFER     = 3;
    public static final double MIN_VEHICLE_GAP      = 4;
    public static final double QUEUE_STOPPED_GAP    = 6;
    public static final double SHOULDER_YIELD_OFFSET = 14;
    public static final double PRIORITY_YIELD_LATERAL_SPEED = 22;
    public static final double LANE_CHANGE_LATERAL_SPEED = 20;
    public static final double TURN_SPEED_FACTOR = 0.58;
    public static final double TURN_ROTATION_SPEED_DEG = 170;

    // Traffic light timing
    public static final double GREEN_DURATION   = 18;
    public static final double YELLOW_DURATION  = 3;
    public static final double RED_DURATION     = 18;
    public static final int    COUNTDOWN_THRESHOLD = 10;

    // Spawn intervals (seconds between spawns)
    public static final double SPAWN_INTERVAL_LOW    = 5.0;
    public static final double SPAWN_INTERVAL_MEDIUM = 2.2;
    public static final double SPAWN_INTERVAL_HIGH   = 0.7;

    // Rendering
    public static final int    TARGET_FPS  = 60;
    public static final double MIN_ZOOM    = 0.3;
    public static final double MAX_ZOOM    = 3.0;
    public static final double DEFAULT_ZOOM = 1.0;
    public static final double MAP_MARGIN   = 800;

    // Network scene - 10 intersections, mixed types
    public static final double NETWORK_SPACING_X = 360;
    public static final double NETWORK_SPACING_Y = 320;

    // Sound
    public static final double MASTER_VOLUME = 0.7;
    public static final double SIREN_VOLUME  = 0.9;
    public static final double HORN_VOLUME   = 0.5;
    public static final double SIGNAL_VOLUME = 0.35;

    // Global speed multiplier (runtime-adjustable)
    public static volatile double globalSpeedMultiplier = 1.0;

    // Day / Night cycle
    public static enum TimeMode { DAY, NIGHT, CYCLE }
    public static TimeMode timeMode = TimeMode.DAY;
    public static double timeOfDay = 12.0; // 0.0 to 24.0 hours

    public static boolean isNightMode() {
        if (timeMode == TimeMode.NIGHT) return true;
        if (timeMode == TimeMode.DAY) return false;
        return timeOfDay < 6.0 || timeOfDay > 18.0;
    }

    public static double getAmbientLight() {
        if (timeMode == TimeMode.DAY) return 1.0;
        if (timeMode == TimeMode.NIGHT) return 0.15;
        
        // Cycle mode: mapping 0..24 to cosine wave. Peak day at 12, peak night at 0/24
        double rad = Math.toRadians((timeOfDay - 12.0) * 15.0); // 12 hours = 180 deg
        double cosVal = Math.cos(rad);
        return 0.15 + (1.0 - 0.15) * (cosVal + 1.0) / 2.0;
    }
}
