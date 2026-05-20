package com.trafficsim.model.vehicle;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.Direction;
import com.trafficsim.model.TrafficLight;
import com.trafficsim.model.driver.DrivingBehavior;
import com.trafficsim.model.road.Lane;
import java.util.List;
import java.util.UUID;

/**
 * Base class for all vehicles. No drawing code here.
 *
 * Lane assignment rules:
 *   laneIndex 0 (inner): cars, buses, trucks, priority vehicles
 *   laneIndex 1 (outer): motorbikes, bicycles
 *
 * Stop-line logic:
 *   - RED   → brake and stop before stop line
 *   - YELLOW → start braking to stop before stop line (unless already past it)
 *   - If vehicle has already crossed stop line → continue through intersection
 *
 * Overtaking:
 *   - Normal vehicles: move to inner sibling lane if blocked
 *   - Priority vehicles: may use opposite lane temporarily if clear
 */
public abstract class Vehicle {
    private final String id;
    protected double x, y;
    protected double speed;
    private double maxSpeed;
    private double speedMultiplier = 1.0;
    protected double length, width;
    protected Direction direction;
    private Lane currentLane;
    private DrivingBehavior behavior;

    // Traffic state
    private boolean stoppedForRed       = false;
    private boolean passedStopLine      = false;
    private boolean yieldingForPriority = false;
    private boolean yieldingAtIntersection = false;
    private boolean onPriorityShoulder  = false;
    private boolean signalLeftOn        = false;
    private boolean signalRightOn       = false;
    private double  signalTimer         = 0;

    // Overtake state
    private boolean overtaking          = false;
    private Lane    overtakeTargetLane  = null;
    private double  overtakeTimer       = 0;
    private boolean wrongWayOvertaking  = false;
    private double  wrongWayDirX        = 0;
    private double  wrongWayDirY        = 0;
    private double  wrongWayAngleDeg    = 0;
    private double  laneChangeOffsetX   = 0;
    private double  laneChangeOffsetY   = 0;
    public double laneChangeSignalTimer = 0;
    private static final double OVERTAKE_DURATION = 4.0;

    // Priority yielding state
    private Lane priorityYieldOriginalLane = null;
    private double priorityYieldOffset = 0;
    private double priorityYieldTargetOffset = 0;

    // Pre-lane-change signaling state
    private Lane pendingLaneChangeTarget = null;
    private double laneChangePrepTimer = 0.0;
    private boolean isOvertakePrep = false;
    private boolean isEmergencyOvertakePrep = false;

    // Curved routing state used inside intersections and roundabouts.
    private boolean followingPath = false;
    private Lane pathExitLane = null;
    private double[] pathXs = new double[0];
    private double[] pathYs = new double[0];
    private double[] pathCumulative = new double[0];
    private double pathProgress = 0;
    private double pathLength = 0;
    private double pathDirX = 1;
    private double pathDirY = 0;
    private double pathHeadingAngleDeg = 0;
    private double pathTargetAngleDeg = 0;

    public enum TurnIntent { LEFT, STRAIGHT, RIGHT, UTURN }
    private TurnIntent globalTurnPreference = TurnIntent.STRAIGHT;
    private TurnIntent activeTurnIntent = TurnIntent.STRAIGHT;
    private Lane upcomingExitLane = null;
    private com.trafficsim.model.intersection.Intersection upcomingIntersection = null;
    private boolean honking = false;

    // Preferred lane index for this vehicle type (0=inner, 1=outer)
    protected int preferredLaneIndex = 0;

    protected Vehicle(double x, double y, Direction direction,
                      double maxSpeed, double length, double width,
                      DrivingBehavior behavior) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.x = x; this.y = y; this.direction = direction;
        this.maxSpeed = maxSpeed; this.length = length; this.width = width;
        this.behavior = behavior; this.speed = 0;
    }

    public void update(double dt, Vehicle frontVehicle) {
        if (currentLane == null) return;

        if (laneChangePrepTimer > 0 && pendingLaneChangeTarget != null) {
            laneChangePrepTimer -= dt;
            if (laneChangePrepTimer <= 0) {
                Lane target = pendingLaneChangeTarget;
                pendingLaneChangeTarget = null;
                Lane original = currentLane;
                double reqGap = isOvertakePrep ? (length + 16) : (length + 20);
                if (original != null && changeToLane(target, reqGap)) {
                    if (isOvertakePrep) {
                        overtakeTargetLane = original;
                        overtaking = true;
                        overtakeTimer = isEmergencyOvertakePrep ? 3.0 : OVERTAKE_DURATION;
                        wrongWayOvertaking = isEmergencyOvertakePrep;
                        if (isEmergencyOvertakePrep) {
                            wrongWayDirX = getMoveX();
                            wrongWayDirY = getMoveY();
                            wrongWayAngleDeg = getHeadingAngleDeg();
                        }
                    } else {
                        laneChangeSignalTimer = 2.0;
                    }
                } else {
                    stopSignal();
                    if (isEmergencyOvertakePrep) stopHonk();
                }
                isOvertakePrep = false;
                isEmergencyOvertakePrep = false;
            }
        }

        updatePriorityYieldOffset(dt);
        updateLaneChangeOffset(dt);

        if (followingPath) {
            updatePath(dt, frontVehicle);
            onUpdate(dt);
            return;
        }

        double effectiveMax = maxSpeed * speedMultiplier * SimConfig.globalSpeedMultiplier;
        boolean stopAtLine = false;

        // ---- Stop-line / traffic light logic ----
        if (!passedStopLine) {
            double frontGapToLine = distanceToStopLine() - length * 0.5;
            TrafficLight tl = currentLane.getTrafficLight();
            boolean mustStopForLight = tl != null && behavior.shouldStopAtRedLight(this, currentLane);
            boolean mustYieldAtLine = yieldingAtIntersection && !isPriorityVehicle();

            if (frontGapToLine <= 0 && !mustStopForLight && !mustYieldAtLine) {
                passedStopLine = true;
                stoppedForRed = false;
            }

            if (!passedStopLine && (mustStopForLight || mustYieldAtLine)) {
                stopAtLine = true;
                stoppedForRed = mustStopForLight;
                double stopGap = Math.max(0, frontGapToLine - SimConfig.STOP_LINE_BUFFER);
                if (stopGap <= 0.25) {
                    speed = 0;
                    // Signal blinker
                    signalTimer += dt;
                    if ((signalLeftOn || signalRightOn) && signalTimer > 0.5) signalTimer = 0;
                    onUpdate(dt);
                    return;
                }
                double stopSpeedCap = Math.sqrt(2 * SimConfig.DEFAULT_DECELERATION * stopGap);
                effectiveMax = Math.min(effectiveMax, stopSpeedCap);
            } else if (!mustStopForLight) {
                stoppedForRed = false;
            }
        } else {
            stoppedForRed = false;
        }

        // ---- Collision avoidance: always respect vehicle ahead ----
        double acc = behavior.computeAcceleration(this, currentLane, frontVehicle, dt);
        if (frontVehicle != null) {
            double gap = longitudinalGapTo(frontVehicle);
            double queueGap = SimConfig.QUEUE_STOPPED_GAP;
            if (frontVehicle.getSpeed() < 1.0) {
                double stopRoom = gap - queueGap;
                if (stopRoom <= 0) {
                    effectiveMax = 0;
                    acc = Math.min(acc, -SimConfig.DEFAULT_DECELERATION * 1.5);
                } else {
                    double queueSpeedCap = Math.sqrt(2 * SimConfig.DEFAULT_DECELERATION * stopRoom);
                    effectiveMax = Math.min(effectiveMax, queueSpeedCap);
                    double targetSpeed = Math.min(effectiveMax, queueSpeedCap);
                    double queueAcc = (targetSpeed - speed) / Math.max(dt, 0.001);
                    acc = Math.max(-SimConfig.DEFAULT_DECELERATION * 1.5,
                            Math.min(SimConfig.DEFAULT_ACCELERATION, queueAcc));
                }
            }
        }

        if (yieldingForPriority) {
            effectiveMax = Math.min(effectiveMax,
                    maxSpeed * speedMultiplier * SimConfig.globalSpeedMultiplier * SimConfig.YIELD_SPEED_FACTOR);
            acc = Math.min(acc, (effectiveMax - speed) / Math.max(dt, 0.001));
        } else if (stopAtLine || (yieldingAtIntersection && !isPriorityVehicle())) {
            acc = Math.min(acc, (effectiveMax - speed) / Math.max(dt, 0.001));
        }

        speed = Math.max(0, Math.min(effectiveMax, speed + acc * dt));

        if (frontVehicle != null) {
            double gap = longitudinalGapTo(frontVehicle);
            double targetGap = frontVehicle.getSpeed() < 1.0
                    ? SimConfig.QUEUE_STOPPED_GAP
                    : SimConfig.MIN_VEHICLE_GAP;
            double maxMove = Math.max(0, gap - targetGap);
            if (speed * dt > maxMove) {
                speed = dt > 0 ? Math.max(0, maxMove / dt) : 0;
            }
        }

        double move = speed * dt;
        if (stopAtLine) {
            double frontGapToLine = distanceToStopLine() - length * 0.5;
            double maxMove = Math.max(0, frontGapToLine - SimConfig.STOP_LINE_BUFFER);
            if (move > maxMove) {
                move = maxMove;
                speed = dt > 0 ? move / dt : 0;
            }
        }

        x += getMoveX() * move;
        y += getMoveY() * move;

        if (frontVehicle != null) {
            double minAllowedDist = (length + frontVehicle.getLength()) * 0.5 + 4.0;
            double dx = frontVehicle.getX() - x;
            double dy = frontVehicle.getY() - y;
            double dist = Math.hypot(dx, dy);
            if (dist < minAllowedDist && dist > 0.001) {
                speed = 0; // Stop smoothly instead of jumping backward
            }
        }

        if (!stopAtLine && !passedStopLine && distanceToStopLine() - length * 0.5 <= 0) {
            passedStopLine = true;
            stoppedForRed = false;
        }

        // Signal blinker
        signalTimer += dt;
        if ((signalLeftOn || signalRightOn) && signalTimer > 0.5) signalTimer = 0;

        // Lane change signal timer
        if (laneChangeSignalTimer > 0) {
            laneChangeSignalTimer -= dt;
            if (laneChangeSignalTimer <= 0) {
                stopSignal();
            }
        }

        // Overtake timer
        if (overtaking) {
            overtakeTimer -= dt;
            if (overtakeTimer <= 0) finishOvertake();
        }

        onUpdate(dt);
    }

    /** Try to change to sibling lane for overtaking. Returns true if succeeded. */
    public boolean tryOvertake() {
        if (isPriorityVehicle()) return false; // Priority vehicles stay in the innermost lane (lane 0)
        if (overtaking || followingPath || currentLane == null || laneChangePrepTimer > 0 || pendingLaneChangeTarget != null) return false;
        Lane left = currentLane.getLeftSibling();
        Lane right = currentLane.getRightSibling();
        
        // Exclude lane 3 for civilian vehicles
        if (right != null && (!isPriorityVehicle() && right.getLaneIndex() == 3)) {
            right = null;
        }
        // Bicycles stay on lane 3 only
        if (this instanceof com.trafficsim.model.vehicle.Bicycle) {
            left = null;
            right = null;
        }

        if (left != null && left.hasSpaceNear(x, y, length + 16)) {
            pendingLaneChangeTarget = left;
            laneChangePrepTimer = 1.0;
            isOvertakePrep = true;
            isEmergencyOvertakePrep = false;
            setSignalLeft(true);
            return true;
        }
        
        if (right != null && right.hasSpaceNear(x, y, length + 16)) {
            pendingLaneChangeTarget = right;
            laneChangePrepTimer = 1.0;
            isOvertakePrep = true;
            isEmergencyOvertakePrep = false;
            setSignalRight(true);
            return true;
        }
        
        return false;
    }

    public boolean performSmoothLaneChange(Lane target) {
        if (target == null || currentLane == null || laneChangePrepTimer > 0 || pendingLaneChangeTarget != null || overtaking || followingPath) return false;
        
        // Bicycle lane safety constraint
        boolean isBicycle = this instanceof com.trafficsim.model.vehicle.Bicycle;
        boolean targetIsBicycleLane = target.getLaneIndex() == 3;
        if (isBicycle != targetIsBicycleLane) return false;

        // Priority vehicles stay in lane 0
        if (isPriorityVehicle() && target.getLaneIndex() != 0) return false;

        boolean movingLeft = target.getLaneIndex() < currentLane.getLaneIndex();
        if (target.hasSpaceNear(x, y, length + 20)) {
            pendingLaneChangeTarget = target;
            laneChangePrepTimer = 1.0;
            isOvertakePrep = false;
            isEmergencyOvertakePrep = false;
            if (movingLeft) setSignalLeft(true);
            else setSignalRight(true);
            return true;
        }
        return false;
    }

    /** Priority vehicles can use opposite lane to overtake. */
    public boolean tryEmergencyOvertake() {
        if (!isPriorityVehicle() || followingPath || currentLane == null || laneChangePrepTimer > 0 || pendingLaneChangeTarget != null || overtaking) return false;
        Lane opp = currentLane.getOppositeLane();
        if (opp != null && opp.hasSpaceNear(x, y, length + 20)) {
            pendingLaneChangeTarget = opp;
            laneChangePrepTimer = 1.0;
            isOvertakePrep = true;
            isEmergencyOvertakePrep = true;
            setSignalLeft(true); honk();
            return true;
        }
        return false;
    }

    private void finishOvertake() {
        if (overtakeTargetLane != null && currentLane != null) {
            if (!changeToLane(overtakeTargetLane, length + 12)) {
                overtakeTimer = 0.5;
                return;
            }
            overtakeTargetLane = null;
        }
        overtaking = false; overtakeTimer = 0;
        wrongWayOvertaking = false;
        stopSignal(); stopHonk();
    }

    protected void onUpdate(double dt) {}

    public boolean beginPath(Lane exitLane, List<double[]> points) {
        if (followingPath || currentLane == null || exitLane == null || points == null || points.size() < 2) {
            return false;
        }

        int n = points.size();
        pathXs = new double[n];
        pathYs = new double[n];
        pathCumulative = new double[n];
        pathLength = 0;
        double lastX = points.get(0)[0];
        double lastY = points.get(0)[1];
        pathXs[0] = lastX;
        pathYs[0] = lastY;
        pathCumulative[0] = 0;

        for (int i = 1; i < n; i++) {
            double[] p = points.get(i);
            pathXs[i] = p[0];
            pathYs[i] = p[1];
            pathLength += Math.hypot(p[0] - lastX, p[1] - lastY);
            pathCumulative[i] = pathLength;
            lastX = p[0];
            lastY = p[1];
        }

        if (pathLength < 1) return false;

        double initialHeading = getHeadingAngleDeg();
        followingPath = true;
        pathExitLane = exitLane;
        pathProgress = 0;
        passedStopLine = true;
        stoppedForRed = false;
        pathHeadingAngleDeg = initialHeading;
        pathTargetAngleDeg = initialHeading;
        setPathPosition(0);
        return true;
    }

    private void updatePath(double dt, Vehicle frontVehicle) {
        double desired = getMaxSpeed() * SimConfig.TURN_SPEED_FACTOR;
        if (yieldingForPriority) desired *= SimConfig.YIELD_SPEED_FACTOR;
        if (yieldingAtIntersection && !isPriorityVehicle()) desired = 0;

        double delta = desired - speed;
        double acc = delta > 0
                ? Math.min(delta / Math.max(dt, 0.001), SimConfig.DEFAULT_ACCELERATION)
                : Math.max(delta / Math.max(dt, 0.001), -SimConfig.DEFAULT_DECELERATION);

        if (frontVehicle != null) {
            double gap = distanceTo(frontVehicle) - (length + frontVehicle.getLength()) * 0.5;
            if (gap < SimConfig.SAFE_FOLLOW_DISTANCE * 1.5) {
                double otherSpeed = frontVehicle.getSpeed();
                double relSpeed = speed - otherSpeed;
                double safeGap = SimConfig.MIN_VEHICLE_GAP + speed * 0.4 + Math.max(0, relSpeed * relSpeed / (2 * SimConfig.DEFAULT_DECELERATION));
                if (gap < safeGap) {
                    acc = -SimConfig.DEFAULT_DECELERATION;
                    if (gap <= SimConfig.MIN_VEHICLE_GAP * 1.5) {
                        speed = Math.min(speed, otherSpeed);
                        acc = Math.min(acc, -SimConfig.DEFAULT_DECELERATION * 1.5);
                    }
                }
            }
        }

        speed = Math.max(0, speed + acc * dt);

        pathProgress = Math.min(pathLength, pathProgress + speed * dt);
        setPathPosition(pathProgress);

        if (frontVehicle != null) {
            double minAllowedDist = (length + frontVehicle.getLength()) * 0.5 + 4.0;
            double dx = frontVehicle.getX() - x;
            double dy = frontVehicle.getY() - y;
            double dist = Math.hypot(dx, dy);
            if (dist < minAllowedDist && dist > 0.001) {
                speed = 0; // Stop smoothly instead of jumping backward
            }
        }

        smoothPathHeading(dt);
        if (pathProgress >= pathLength - 0.001) finishPath();
    }

    private void setPathPosition(double progress) {
        if (pathCumulative.length < 2) return;
        double[] current = pointOnPath(progress);
        x = current[0];
        y = current[1];

        double lookAhead = Math.max(length * 0.8, 10);
        double lookBehind = Math.max(length * 0.3, 5);
        double[] ahead = pointOnPath(Math.min(pathLength, progress + lookAhead));
        double[] behind = pointOnPath(Math.max(0, progress - lookBehind));
        double dx = ahead[0] - behind[0];
        double dy = ahead[1] - behind[1];
        double len = Math.hypot(dx, dy);
        if (len > 0.001) {
            pathDirX = dx / len;
            pathDirY = dy / len;
            pathTargetAngleDeg = Math.toDegrees(Math.atan2(pathDirY, pathDirX));
        }
    }

    private double[] pointOnPath(double progress) {
        int idx = 1;
        while (idx < pathCumulative.length - 1 && pathCumulative[idx] < progress) idx++;

        double segStart = pathCumulative[idx - 1];
        double segLen = Math.max(0.001, pathCumulative[idx] - segStart);
        double t = Math.max(0, Math.min(1, (progress - segStart) / segLen));
        double x1 = pathXs[idx - 1], y1 = pathYs[idx - 1];
        double x2 = pathXs[idx],     y2 = pathYs[idx];
        return new double[]{x1 + (x2 - x1) * t, y1 + (y2 - y1) * t};
    }

    private void smoothPathHeading(double dt) {
        double delta = normalizeAngle(pathTargetAngleDeg - pathHeadingAngleDeg);
        double maxStep = SimConfig.TURN_ROTATION_SPEED_DEG * dt;
        if (Math.abs(delta) <= maxStep) {
            pathHeadingAngleDeg = pathTargetAngleDeg;
        } else {
            pathHeadingAngleDeg += Math.signum(delta) * maxStep;
        }
        pathHeadingAngleDeg = normalizeAngle(pathHeadingAngleDeg);
    }

    private double normalizeAngle(double angle) {
        while (angle <= -180) angle += 360;
        while (angle > 180) angle -= 360;
        return angle;
    }

    private void finishPath() {
        Lane oldLane = currentLane;
        Lane exitLane = pathExitLane;

        followingPath = false;
        pathExitLane = null;
        pathXs = new double[0];
        pathYs = new double[0];
        pathCumulative = new double[0];
        pathProgress = 0;
        pathLength = 0;

        if (oldLane != null) oldLane.removeVehicle(this);
        if (exitLane != null) {
            exitLane.addVehicle(this);
            direction = exitLane.getDirection();
            resetPassedStopLine();
        }
        if (!overtaking && !yieldingForPriority && priorityYieldTargetOffset <= 0.01) stopSignal();
    }

    public double distanceTo(Vehicle other) {
        double dx = other.x - x, dy = other.y - y;
        return Math.sqrt(dx*dx + dy*dy);
    }

    public double distanceToStopLine() {
        return currentLane == null ? Double.MAX_VALUE : currentLane.distanceToStopLine(this);
    }

    public double longitudinalGapTo(Vehicle frontVehicle) {
        if (frontVehicle == null || currentLane == null) return Double.MAX_VALUE;
        double dx = frontVehicle.x - x;
        double dy = frontVehicle.y - y;
        double along = dx * getMoveX() + dy * getMoveY();
        return along - (length + frontVehicle.length) * 0.5;
    }

    public boolean changeToLane(Lane targetLane, double minGap) {
        if (followingPath || currentLane == null || targetLane == null) return false;
        
        // Bicycle lane safety constraint
        boolean isBicycle = this instanceof com.trafficsim.model.vehicle.Bicycle;
        boolean targetIsBicycleLane = targetLane.getLaneIndex() == 3;
        if (isBicycle != targetIsBicycleLane) return false;

        double s = targetLane.projectDistance(x, y);
        double[] p = targetLane.pointAt(s);
        if (!targetLane.hasSpaceNear(p[0], p[1], minGap)) return false;
        currentLane.removeVehicle(this);
        double oldX = x;
        double oldY = y;
        x = p[0];
        y = p[1];
        laneChangeOffsetX += oldX - x;
        laneChangeOffsetY += oldY - y;
        targetLane.addVehicle(this);
        direction = targetLane.getDirection();
        return true;
    }

    public double getMoveX() {
        if (followingPath) return pathDirX;
        if (wrongWayOvertaking) return wrongWayDirX;
        return currentLane != null ? currentLane.getDirX() : direction.dx;
    }

    public double getMoveY() {
        if (followingPath) return pathDirY;
        if (wrongWayOvertaking) return wrongWayDirY;
        return currentLane != null ? currentLane.getDirY() : direction.dy;
    }

    public double getHeadingAngleDeg() {
        if (followingPath) return pathHeadingAngleDeg;
        if (wrongWayOvertaking) return wrongWayAngleDeg;
        return currentLane != null ? currentLane.getAngleDeg() : direction.angleDeg;
    }

    public double getRenderX() {
        double rx = x + laneChangeOffsetX;
        if (currentLane != null && Math.abs(priorityYieldOffset) > 0.01) {
            rx += currentLane.getRightNormalX() * priorityYieldOffset;
        }
        return rx;
    }

    public double getRenderY() {
        double ry = y + laneChangeOffsetY;
        if (currentLane != null && Math.abs(priorityYieldOffset) > 0.01) {
            ry += currentLane.getRightNormalY() * priorityYieldOffset;
        }
        return ry;
    }

    private void updatePriorityYieldOffset(double dt) {
        double delta = priorityYieldTargetOffset - priorityYieldOffset;
        double maxStep = SimConfig.PRIORITY_YIELD_LATERAL_SPEED * dt;
        if (Math.abs(delta) <= maxStep) {
            priorityYieldOffset = priorityYieldTargetOffset;
        } else {
            priorityYieldOffset += Math.signum(delta) * maxStep;
        }
        if (priorityYieldOffset <= 0.01 && !yieldingForPriority) {
            priorityYieldOffset = 0;
            onPriorityShoulder = false;
        }
    }

    private void updateLaneChangeOffset(double dt) {
        double dist = Math.hypot(laneChangeOffsetX, laneChangeOffsetY);
        if (dist <= 0.01) {
            laneChangeOffsetX = 0;
            laneChangeOffsetY = 0;
            return;
        }

        double step = SimConfig.LANE_CHANGE_LATERAL_SPEED * dt;
        if (step >= dist) {
            laneChangeOffsetX = 0;
            laneChangeOffsetY = 0;
        } else {
            double k = (dist - step) / dist;
            laneChangeOffsetX *= k;
            laneChangeOffsetY *= k;
        }
    }

    public boolean isPriorityVehicle() { return false; }

    /** True if this vehicle prefers the outer lane (motorbikes, bicycles). */
    public boolean prefersOuterLane() { return preferredLaneIndex == 1; }

    public abstract String getShortName();
    public abstract String getColor();
    public abstract String getSpritePath();

    public void drawShape(javafx.scene.canvas.GraphicsContext gc, double scale) {
        double vw = width*scale, vh = length*scale;
        gc.setFill(javafx.scene.paint.Color.web(getColor()));
        gc.fillRoundRect(-vh/2, -vw/2, vh, vw, 3*scale, 3*scale);
        gc.setFill(javafx.scene.paint.Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font("Arial", Math.max(6, 7*scale)));
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.fillText(getShortName(), 0, 2*scale);
    }

    public void setSignalLeft(boolean on) {
        if (on && !signalLeftOn) signalTimer = 0;
        signalLeftOn = on;
        if (on) signalRightOn = false;
    }
    public void setSignalRight(boolean on) {
        if (on && !signalRightOn) signalTimer = 0;
        signalRightOn = on;
        if (on) signalLeftOn = false;
    }
    public void stopSignal()  { signalLeftOn = false; signalRightOn = false; }
    public void honk()        { honking = true; }
    public void stopHonk()    { honking = false; }

    // ---- Getters / Setters ----
    public String    getId()                    { return id; }
    public double    getX()                     { return x; }
    public double    getY()                     { return y; }
    public void      setX(double x)             { this.x = x; }
    public void      setY(double y)             { this.y = y; }
    public double    getSpeed()                 { return speed; }
    public void      setSpeed(double s)         { this.speed = Math.max(0, s); }
    public double    getMaxSpeed()              { return maxSpeed * speedMultiplier * SimConfig.globalSpeedMultiplier; }
    public double    getBaseMaxSpeed()          { return maxSpeed; }
    public void      setBaseMaxSpeed(double s)  { this.maxSpeed = s; }
    public double    getSpeedMultiplier()       { return speedMultiplier; }
    public void      setSpeedMultiplier(double m){ this.speedMultiplier = Math.max(0.1, m); }
    public double    getLength()                { return length; }
    public double    getWidth()                 { return width; }
    public Direction getDirection()             { return direction; }
    public void      setDirection(Direction d)  { this.direction = d; }
    public Lane      getCurrentLane()           { return currentLane; }
    public void      setCurrentLane(Lane l)     { this.currentLane = l; }
    public DrivingBehavior getBehavior()                  { return behavior; }
    public void            setBehavior(DrivingBehavior b) { this.behavior = b; }
    public boolean   isStoppedForRed()          { return stoppedForRed; }
    public boolean   isYieldingForPriority()    { return yieldingForPriority; }
    public void      setYieldingForPriority(boolean y){
        if (isPriorityVehicle()) y = false;
        this.yieldingForPriority = y;
        if (y) setSignalRight(true);
        else if (!overtaking && !followingPath) stopSignal();
    }
    public boolean   isYieldingAtIntersection() { return yieldingAtIntersection; }
    public void      setYieldingAtIntersection(boolean y){ this.yieldingAtIntersection = y && !isPriorityVehicle(); }
    public boolean   isOnPriorityShoulder()     { return onPriorityShoulder; }
    public void      setOnPriorityShoulder(boolean s){ this.onPriorityShoulder = s; }
    public void      setPriorityYieldOffsetTarget(double offset) {
        this.priorityYieldTargetOffset = Math.max(0, offset);
        if (priorityYieldTargetOffset > 0.01) this.onPriorityShoulder = true;
    }
    public double    getPriorityYieldOffset()    { return priorityYieldOffset; }
    public Lane      getPriorityYieldOriginalLane() { return priorityYieldOriginalLane; }
    public void      rememberPriorityYieldLane() {
        if (priorityYieldOriginalLane == null) priorityYieldOriginalLane = currentLane;
    }
    public void      clearPriorityYieldLane()   { priorityYieldOriginalLane = null; }
    public boolean   isSignalOn()               { return signalLeftOn || signalRightOn; }
    public boolean   isSignalLeftOn()           { return signalLeftOn; }
    public boolean   isSignalRightOn()          { return signalRightOn; }
    public boolean   isHonking()                { return honking; }
    public boolean   isOvertaking()             { return overtaking; }
    public boolean   isFollowingPath()          { return followingPath; }
    public TurnIntent getGlobalTurnPreference() { return globalTurnPreference; }
    public void       setGlobalTurnPreference(TurnIntent t){ this.globalTurnPreference = t; }
    public TurnIntent getActiveTurnIntent()     { return activeTurnIntent; }
    public void       setActiveTurnIntent(TurnIntent t){ this.activeTurnIntent = t; }
    public Lane       getUpcomingExitLane()     { return upcomingExitLane; }
    public com.trafficsim.model.intersection.Intersection getUpcomingIntersection() { return upcomingIntersection; }
    public void       setUpcomingExit(com.trafficsim.model.intersection.Intersection inter, Lane exitLane, TurnIntent intent) {
        this.upcomingIntersection = inter;
        this.upcomingExitLane = exitLane;
        this.activeTurnIntent = intent;
    }
    public void       clearUpcomingExit()       { this.upcomingIntersection = null; this.upcomingExitLane = null; }
    public boolean   hasPassedStopLine()        { return passedStopLine; }
    public void      resetPassedStopLine()      { passedStopLine = false; }
    public int       getPreferredLaneIndex()    { return preferredLaneIndex; }
}
