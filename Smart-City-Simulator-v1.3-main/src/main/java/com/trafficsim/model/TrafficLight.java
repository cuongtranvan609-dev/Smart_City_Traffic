package com.trafficsim.model;

import com.trafficsim.config.SimConfig;

/**
 * Đèn giao thông hỗ trợ 3 kiểu hiển thị:
 *  ALWAYS_COUNTDOWN  – luôn hiển thị giây đếm ngược
 *  NO_COUNTDOWN      – không hiển thị đếm ngược
 *  LATE_COUNTDOWN    – chỉ hiển thị đếm ngược khi còn <= 10 giây
 */
public class TrafficLight {

    public enum Phase { GREEN, YELLOW, RED }

    public enum DisplayType {
        ALWAYS_COUNTDOWN,
        NO_COUNTDOWN,
        LATE_COUNTDOWN
    }

    private Phase       phase;
    private DisplayType displayType;
    private double      timer;          // giây đã trôi trong pha hiện tại
    private double      greenDuration;
    private double      yellowDuration;
    private double      redDuration;
    private boolean     autoMode;       // true = tự động, false = thủ công

    // Vị trí để vẽ đèn
    private double x, y;
    /** Hướng của nhánh đường mà đèn này kiểm soát */
    private Direction direction;
    private Double customAngle = null;

    public double getAngleDeg() {
        return customAngle != null ? customAngle : direction.angleDeg;
    }

    public void setAngleDeg(double angle) {
        this.customAngle = angle;
    }

    public TrafficLight(double x, double y, Direction direction, Phase initialPhase) {
        this.x             = x;
        this.y             = y;
        this.direction     = direction;
        this.phase         = initialPhase;
        this.displayType   = DisplayType.LATE_COUNTDOWN;
        this.greenDuration  = SimConfig.GREEN_DURATION;
        this.yellowDuration = SimConfig.YELLOW_DURATION;
        this.redDuration    = SimConfig.RED_DURATION;
        this.autoMode       = true;
        this.timer          = 0;
    }

    /** Gọi mỗi frame với delta time (giây). */
    public void update(double dt) {
        if (!autoMode) return;
        timer += dt;
        double phaseDuration = getPhaseDuration();
        if (timer >= phaseDuration) {
            timer -= phaseDuration;
            advancePhase();
        }
    }

    private void advancePhase() {
        phase = switch (phase) {
            case GREEN  -> Phase.YELLOW;
            case YELLOW -> Phase.RED;
            case RED    -> Phase.GREEN;
        };
    }

    /** Chuyển pha thủ công (khi người dùng click). */
    public void manualAdvance() {
        timer = 0;
        advancePhase();
    }

    public double getRemainingSeconds() {
        return Math.max(0, getPhaseDuration() - timer);
    }

    private double getPhaseDuration() {
        return switch (phase) {
            case GREEN  -> greenDuration;
            case YELLOW -> yellowDuration;
            case RED    -> redDuration;
        };
    }

    /** Kiểm tra xem có hiển thị số giây không dựa vào DisplayType. */
    public boolean shouldShowCountdown() {
        return switch (displayType) {
            case ALWAYS_COUNTDOWN -> true;
            case NO_COUNTDOWN     -> false;
            case LATE_COUNTDOWN   -> getRemainingSeconds() <= SimConfig.COUNTDOWN_THRESHOLD;
        };
    }

    public boolean isGreen()  { return phase == Phase.GREEN; }
    public boolean isYellow() { return phase == Phase.YELLOW; }
    public boolean isRed()    { return phase == Phase.RED; }

    // -------- Getters / Setters --------
    public Phase       getPhase()       { return phase; }
    public DisplayType getDisplayType() { return displayType; }
    public boolean     isAutoMode()     { return autoMode; }
    public double      getX()           { return x; }
    public double      getY()           { return y; }
    public Direction   getDirection()   { return direction; }
    public double      getTimer()       { return timer; }

    public void setDisplayType(DisplayType dt) { this.displayType = dt; }
    public void setAutoMode(boolean auto)       { this.autoMode = auto; }
    public void setPhase(Phase p)               { this.phase = p; timer = 0; }
    public void setTimer(double t)              { this.timer = t; }
    public void setGreenDuration(double d)      { this.greenDuration = d; }
    public void setYellowDuration(double d)     { this.yellowDuration = d; }
    public void setRedDuration(double d)        { this.redDuration = d; }
}
