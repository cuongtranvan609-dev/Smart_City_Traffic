package com.trafficsim.model;

/**
 * Hướng di chuyển của phương tiện và làn đường.
 */
public enum Direction {
    NORTH(0, -1, 270),
    EAST (1,  0, 0),
    SOUTH(0,  1, 90),
    WEST (-1, 0, 180);

    public final int dx;
    public final int dy;
    /** Góc quay hình ảnh xe (độ), 0 = mũi xe nhìn sang phải */
    public final double angleDeg;

    Direction(int dx, int dy, double angleDeg) {
        this.dx = dx;
        this.dy = dy;
        this.angleDeg = angleDeg;
    }

    public Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST  -> WEST;
            case WEST  -> EAST;
        };
    }

    public Direction turnLeft() {
        return switch (this) {
            case NORTH -> WEST;
            case WEST  -> SOUTH;
            case SOUTH -> EAST;
            case EAST  -> NORTH;
        };
    }

    public Direction turnRight() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST  -> SOUTH;
            case SOUTH -> WEST;
            case WEST  -> NORTH;
        };
    }

    public static Direction fromVector(double dx, double dy) {
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0 ? EAST : WEST;
        }
        return dy >= 0 ? SOUTH : NORTH;
    }
}
