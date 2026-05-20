package com.trafficsim.view.renderer;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.Direction;
import com.trafficsim.model.SimScene;
import com.trafficsim.model.TrafficLight;
import com.trafficsim.model.intersection.Intersection;
import com.trafficsim.model.road.Lane;
import com.trafficsim.model.road.Road;
import com.trafficsim.model.vehicle.Vehicle;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

public class BasicRenderer implements SceneRenderer {
    static final Color BG        = Color.rgb(30, 100, 30);
    static final Color ROAD_CLR  = Color.rgb(55, 55, 65);
    static final Color CURB_CLR  = Color.rgb(75, 75, 85);
    static final Color INTER_CLR = Color.rgb(45, 45, 55);
    static final Color CENTER_LINE = Color.rgb(240,200,30,0.7);  // yellow center divider
    static final Color LANE_DASH   = Color.rgb(220,220,200,0.6); // white dashed lane divider
    static final Color STOP_LINE   = Color.WHITE;
    static final Color TURN_GUIDE  = Color.rgb(245, 205, 40, 0.7);

    @Override
    public void render(GraphicsContext gc, SimScene scene, double zoom, double panX, double panY) {
        double W = gc.getCanvas().getWidth(), H = gc.getCanvas().getHeight();
        gc.clearRect(0,0,W,H);
        gc.setFill(BG); gc.fillRect(0,0,W,H);

        gc.save();
        gc.translate(panX, panY);
        gc.scale(zoom, zoom);

        scene.getRoads().forEach(r -> drawRoad(gc, r));
        scene.getIntersections().forEach(i -> drawIntersection(gc, i));
        drawTurnGuides(gc, scene);
        scene.getRoads().forEach(r -> drawStopLines(gc, r));
        scene.getIntersections().forEach(i -> i.getTrafficLights().forEach(tl -> drawLight(gc, tl)));
        scene.getVehicles().forEach(v -> drawVehicle(gc, v));

        gc.restore();
    }

    static void drawRoad(GraphicsContext gc, Road road) {
        double lw = SimConfig.LANE_WIDTH;
        double hw  = lw * 3; // half total width (3 lanes per direction)
        double fullW = hw * 2;

        double dx = road.getX2()-road.getX1(), dy = road.getY2()-road.getY1();
        double len = Math.sqrt(dx*dx+dy*dy); if (len<1) return;
        // Unit perpendicular to the drawn centerline.
        double px = -dy/len, py = dx/len;

        // Curb
        gc.setStroke(CURB_CLR); gc.setLineWidth(fullW+5);
        gc.strokeLine(road.getX1(), road.getY1(), road.getX2(), road.getY2());
        // Asphalt
        gc.setStroke(ROAD_CLR); gc.setLineWidth(fullW);
        gc.strokeLine(road.getX1(), road.getY1(), road.getX2(), road.getY2());

        // Center yellow double line
        gc.setStroke(CENTER_LINE); gc.setLineWidth(1.4); gc.setLineDashes(null);
        gc.strokeLine(road.getX1(), road.getY1(), road.getX2(), road.getY2());

        // White dashed dividers between lanes within each direction
        gc.setStroke(LANE_DASH); gc.setLineWidth(0.8); gc.setLineDashes(8,6);
        // Inner lane dividers (offset 1 * lw)
        gc.strokeLine(road.getX1() + px * lw, road.getY1() + py * lw,
                      road.getX2() + px * lw, road.getY2() + py * lw);
        gc.strokeLine(road.getX1() - px * lw, road.getY1() - py * lw,
                      road.getX2() - px * lw, road.getY2() - py * lw);
        // Outer lane dividers (offset 2 * lw)
        gc.strokeLine(road.getX1() + px * lw * 2, road.getY1() + py * lw * 2,
                      road.getX2() + px * lw * 2, road.getY2() + py * lw * 2);
        gc.strokeLine(road.getX1() - px * lw * 2, road.getY1() - py * lw * 2,
                      road.getX2() - px * lw * 2, road.getY2() - py * lw * 2);
        gc.setLineDashes(null);
    }

    static void drawIntersection(GraphicsContext gc, Intersection inter) {
        double r = inter.getRadius();
        gc.setFill(INTER_CLR);
        gc.fillOval(inter.getCx()-r, inter.getCy()-r, r*2, r*2);

        if (inter.getType() == Intersection.Type.FIVE_WAY) {
            gc.setFill(BG);
            gc.fillOval(inter.getCx()-56, inter.getCy()-56, 56*2, 56*2);
            gc.setStroke(LANE_DASH);
            gc.setLineWidth(1.2);
            gc.setLineDashes(8, 6);
            gc.strokeOval(inter.getCx()-72, inter.getCy()-72, 72*2, 72*2);
            gc.strokeOval(inter.getCx()-88, inter.getCy()-88, 88*2, 88*2);
            gc.setLineDashes(null);
        } else {
            // Note: Pedestrian crosswalks have been intentionally removed by user request
        }

        // Label
        gc.setFill(Color.rgb(200,200,180,0.75));
        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 9));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(inter.getTypeName(), inter.getCx(), inter.getCy()+3);
    }

    private static void drawBasicCrosswalk(GraphicsContext gc, Intersection inter, Direction dir) {
        double r = inter.getRadius();
        double armX = inter.getCx() + dir.dx * (r - 4); 
        double armY = inter.getCy() + dir.dy * (r - 4);

        gc.save();
        gc.translate(armX, armY);
        gc.rotate(dir.angleDeg); 

        gc.setStroke(Color.rgb(180, 180, 180, 0.6));
        gc.setLineWidth(2.0);
        
        double roadWidth = SimConfig.LANE_WIDTH * 4; 
        double stripeLen = 8;
        double stripeSpace = 6.0;
        
        for (double y = -roadWidth/2 + 2; y <= roadWidth/2 - 2; y += stripeSpace) {
            gc.strokeLine(-stripeLen/2, y, stripeLen/2, y);
        }
        gc.restore();
    }

    static void drawTurnGuides(GraphicsContext gc, SimScene scene) {
        for (Intersection inter : scene.getIntersections()) {
            if (inter.getType() == Intersection.Type.FIVE_WAY) continue;

            for (Road inRoad : scene.getRoads()) {
                for (Lane inLane : inRoad.getLanes()) {
                    if (inLane.getLaneIndex() != 0 || !laneEndsAtIntersection(inLane, inter)) continue;

                    for (Road outRoad : scene.getRoads()) {
                        for (Lane outLane : outRoad.getLanes()) {
                            if (outLane.getLaneIndex() != 0 || !laneStartsAtIntersection(outLane, inter)) continue;
                            if (isUTurn(inLane, outLane)) continue;
                            double dot = inLane.getDirX() * outLane.getDirX()
                                    + inLane.getDirY() * outLane.getDirY();
                            if (dot > 0.92) continue;
                            double cross = inLane.getDirX() * outLane.getDirY()
                                    - inLane.getDirY() * outLane.getDirX();
                            if (cross >= -0.25) continue;
                            drawGuidePath(gc, inter, inLane, outLane);
                        }
                    }
                }
            }
        }
    }

    private static void drawGuidePath(GraphicsContext gc, Intersection inter, Lane inLane, Lane outLane) {
        java.util.List<double[]> pts = buildGuidePath(inter, inLane, outLane);
        if (pts.size() < 2) return;

        gc.setStroke(TURN_GUIDE);
        gc.setLineWidth(0.9);
        gc.setLineDashes(8, 6);
        gc.beginPath();
        gc.moveTo(pts.get(0)[0], pts.get(0)[1]);
        for (int i = 1; i < pts.size(); i++) {
            gc.lineTo(pts.get(i)[0], pts.get(i)[1]);
        }
        gc.stroke();
        gc.setLineDashes(null);
    }

    private static java.util.List<double[]> buildGuidePath(Intersection inter, Lane inLane, Lane outLane) {
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        double r = inter.getRadius();
        double sx = inter.getCx() - inLane.getDirX() * r;
        double sy = inter.getCy() - inLane.getDirY() * r;
        double ex = inter.getCx() + outLane.getDirX() * r;
        double ey = inter.getCy() + outLane.getDirY() * r;
        double[] end = {ex, ey};
        double distance = Math.hypot(end[0] - sx, end[1] - sy);
        double inDx = inLane.getDirX(), inDy = inLane.getDirY();
        double outDx = outLane.getDirX(), outDy = outLane.getDirY();

        pts.add(new double[]{sx, sy});
        double ctrl = Math.max(inter.getRadius() * 0.85, distance * 0.42);
        addCubicSamples(pts,
                sx, sy,
                sx + inDx * ctrl, sy + inDy * ctrl,
                end[0] - outDx * ctrl, end[1] - outDy * ctrl,
                end[0], end[1],
                34);
        return pts;
    }

    private static void addLineSamples(java.util.List<double[]> pts,
                                       double x1, double y1, double x2, double y2, int steps) {
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            pts.add(new double[]{x1 + (x2 - x1) * t, y1 + (y2 - y1) * t});
        }
    }

    private static void addCubicSamples(java.util.List<double[]> pts,
                                        double x0, double y0,
                                        double x1, double y1,
                                        double x2, double y2,
                                        double x3, double y3,
                                        int steps) {
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double u = 1 - t;
            double x = u*u*u*x0 + 3*u*u*t*x1 + 3*u*t*t*x2 + t*t*t*x3;
            double y = u*u*u*y0 + 3*u*u*t*y1 + 3*u*t*t*y2 + t*t*t*y3;
            pts.add(new double[]{x, y});
        }
    }

    private static boolean laneEndsAtIntersection(Lane lane, Intersection inter) {
        double d = Math.hypot(lane.getEndX() - inter.getCx(), lane.getEndY() - inter.getCy());
        return d < inter.getRadius() + SimConfig.LANE_WIDTH * 2.5;
    }

    private static boolean laneStartsAtIntersection(Lane lane, Intersection inter) {
        double d = Math.hypot(lane.getStartX() - inter.getCx(), lane.getStartY() - inter.getCy());
        return d < inter.getRadius() + SimConfig.LANE_WIDTH * 2.5;
    }

    private static boolean isUTurn(Lane entryLane, Lane exitLane) {
        double dot = entryLane.getDirX() * exitLane.getDirX() + entryLane.getDirY() * exitLane.getDirY();
        return dot < -0.92;
    }

    static void drawStopLines(GraphicsContext gc, Road road) {
        for (Lane lane : road.getLanes()) {
            if (!lane.isStopLineSet()) continue;
            // Perpendicular to lane direction, width = lane width
            double px = -lane.getDirY() * (SimConfig.LANE_WIDTH * 0.52);
            double py =  lane.getDirX() * (SimConfig.LANE_WIDTH * 0.52);
            double sx = lane.getStopLineX(), sy = lane.getStopLineY();
            gc.setStroke(STOP_LINE); gc.setLineWidth(4.0);
            gc.strokeLine(sx-px, sy-py, sx+px, sy+py);
        }
    }

    static void drawLight(GraphicsContext gc, TrafficLight tl) {
        double bx=tl.getX()-5, by=tl.getY()-13, bw=10, bh=26;
        gc.setFill(Color.rgb(20,20,20)); gc.fillRoundRect(bx,by,bw,bh,3,3);
        double r=3.8;
        gc.setFill(tl.isRed()    ? Color.rgb(255,40,40)  : Color.rgb(60,0,0));
        gc.fillOval(tl.getX()-r, by+2, r*2, r*2);
        gc.setFill(tl.isYellow() ? Color.rgb(255,220,0)  : Color.rgb(60,55,0));
        gc.fillOval(tl.getX()-r, by+bh/2-r, r*2, r*2);
        gc.setFill(tl.isGreen()  ? Color.rgb(40,220,40)  : Color.rgb(0,50,0));
        gc.fillOval(tl.getX()-r, by+bh-r*2-2, r*2, r*2);
        if (tl.shouldShowCountdown()) {
            gc.setFill(Color.WHITE); gc.setFont(Font.font("Arial Bold",8));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.valueOf((int)Math.ceil(tl.getRemainingSeconds())),
                        tl.getX(), tl.getY()+14);
        }
    }

    void drawVehicle(GraphicsContext gc, Vehicle v) {
        double vw=v.getWidth(), vh=v.getLength();
        gc.save();
        gc.translate(v.getRenderX(), v.getRenderY());
        gc.rotate(v.getHeadingAngleDeg());
        gc.setFill(Color.web(v.getColor()));
        gc.fillRoundRect(-vh/2,-vw/2,vh,vw,2.5,2.5);
        if (v.isYieldingForPriority()) {
            gc.setStroke(Color.ORANGE); gc.setLineWidth(1.5);
            gc.strokeRoundRect(-vh/2,-vw/2,vh,vw,2.5,2.5);
        }
        if (v.isStoppedForRed()) {
            gc.setFill(Color.rgb(255,80,80,0.5));
            gc.fillOval(-2,-2,4,4);
        }
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial Bold", Math.max(5.5, 6.5)));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(v.getShortName(), 0, 2);
        gc.restore();
    }

    @Override public String getModeName() { return "Basic"; }
}
