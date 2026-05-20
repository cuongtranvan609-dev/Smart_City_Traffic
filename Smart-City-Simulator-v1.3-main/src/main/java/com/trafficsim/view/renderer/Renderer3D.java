package com.trafficsim.view.renderer;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.Direction;
import com.trafficsim.model.SimScene;
import com.trafficsim.model.TrafficLight;
import com.trafficsim.model.intersection.Intersection;
import com.trafficsim.model.intersection.FiveWayIntersection;
import com.trafficsim.model.road.Road;
import com.trafficsim.model.vehicle.Vehicle;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.Glow;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Renderer3D implements SceneRenderer {
    private static final Color ASPHALT  = Color.rgb(50, 50, 55);
    private static final Color CURB     = Color.rgb(140, 140, 140);
    private static final Color SIDEWALK = Color.rgb(215, 215, 215);
    private boolean drawingVehicle = false;

    // 3D axonometric tilt projection offsets
    private double getProjX(double x, double z) {
        return x + 0.25 * z;
    }
    
    private double getProjY(double y, double z) {
        return y - 0.45 * z;
    }

    private double[] getBilinearPoint(double[] A, double[] B, double[] C, double[] D, double u, double v) {
        double x = (1-u)*(1-v)*A[0] + u*(1-v)*B[0] + u*v*C[0] + (1-u)*v*D[0];
        double y = (1-u)*(1-v)*A[1] + u*(1-v)*B[1] + u*v*C[1] + (1-u)*v*D[1];
        return new double[]{x, y};
    }

    private static class Renderable3D implements Comparable<Renderable3D> {
        double sortKey;
        Runnable renderAction;
        
        Renderable3D(double sortKey, Runnable renderAction) {
            this.sortKey = sortKey;
            this.renderAction = renderAction;
        }
        
        @Override
        public int compareTo(Renderable3D o) {
            return Double.compare(this.sortKey, o.sortKey);
        }
    }

    @Override
    public void render(GraphicsContext gc, SimScene scene, double zoom, double panX, double panY) {
        double W = gc.getCanvas().getWidth(), H = gc.getCanvas().getHeight();
        gc.clearRect(0, 0, W, H);
        
        gc.save();
        gc.translate(panX, panY);
        gc.scale(zoom, zoom);

        // Calculate dynamic bounds based on intersections
        double minX = -1000, maxX = 3000, minY = -1000, maxY = 3000;
        if (!scene.getIntersections().isEmpty()) {
            minX = scene.getIntersections().stream().mapToDouble(Intersection::getCx).min().orElse(0) - 800;
            maxX = scene.getIntersections().stream().mapToDouble(Intersection::getCx).max().orElse(0) + 800;
            minY = scene.getIntersections().stream().mapToDouble(Intersection::getCy).min().orElse(0) - 800;
            maxY = scene.getIntersections().stream().mapToDouble(Intersection::getCy).max().orElse(0) + 800;
        }

        // 1. Draw flat ground elements
        drawBackground(gc, scene, minX, maxX, minY, maxY);
        scene.getRoads().forEach(r -> drawRoadCurb(gc, r, scene));
        scene.getRoads().forEach(r -> drawRoadAsphalt(gc, r, scene));
        scene.getRoads().forEach(r -> drawRoadMarkings(gc, r, scene));
        scene.getIntersections().forEach(i -> drawIntersection(gc, i));
        BasicRenderer.drawTurnGuides(gc, scene);
        scene.getRoads().forEach(r -> BasicRenderer.drawStopLines(gc, r));
        
        // 2. Prepare 3D elements for sorting
        List<Renderable3D> renderables = new ArrayList<>();
        
        // A. City blocks (buildings and trees inside parks)
        double cellSize = 130;
        Random rng = new Random(42); // deterministic seed
        for (double x = minX; x < maxX; x += cellSize) {
            for (double y = minY; y < maxY; y += cellSize) {
                double cx = x + cellSize/2;
                double cy = y + cellSize/2;
                
                double safeDist = SimConfig.ROAD_HALF_WIDTH + 20; 
                if (isClearOfRoads(cx, cy, cellSize/2 * 1.4, safeDist, scene)) {
                    addCityBlockToRenderables(gc, renderables, x, y, cellSize, rng, scene);
                }
            }
        }
        
        // B. Traffic lights
        scene.getIntersections().forEach(i -> i.getTrafficLights().forEach(tl -> {
            renderables.add(new Renderable3D(tl.getY() - 0.5 * tl.getX(), () -> draw3DTrafficLight(gc, tl)));
        }));
        
        // C. Vehicles
        scene.getVehicles().forEach(v -> {
            renderables.add(new Renderable3D(v.getY() - 0.5 * v.getX(), () -> draw3DVehicle(gc, v)));
        });
        
        // 3. Sort and render 3D elements (Painter's Algorithm)
        Collections.sort(renderables);
        renderables.forEach(r -> r.renderAction.run());
 
        gc.restore();
    }
 
    private void drawBackground(GraphicsContext gc, SimScene scene, double minX, double maxX, double minY, double maxY) {
        // Base grass/parkland terrain (Green)
        gc.setFill(c(Color.rgb(125, 185, 105)));
        gc.fillRect(minX, minY, maxX - minX, maxY - minY);
        
        // Draw sidewalks along all roads
        gc.setStroke(c(SIDEWALK));
        double fullW = SimConfig.LANE_WIDTH * 8;
        gc.setLineWidth(fullW + 36);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.BUTT);
        for (Road r : scene.getRoads()) {
            double r1 = getRoadOffsetRadius(r, scene, true);
            double r2 = getRoadOffsetRadius(r, scene, false);
            
            double dx = r.getX2() - r.getX1();
            double dy = r.getY2() - r.getY1();
            double len = Math.hypot(dx, dy);
            if (len < 1) continue;
            double ux = dx / len;
            double uy = dy / len;
            
            double sx = r.getX1() + ux * r1;
            double sy = r.getY1() + uy * r1;
            double ex = r.getX2() - ux * r2;
            double ey = r.getY2() - uy * r2;
            
            gc.strokeLine(sx, sy, ex, ey);
        }
        
        // Draw sidewalks around all intersections (only roundabouts / five-way)
        gc.setFill(c(SIDEWALK));
        for (Intersection i : scene.getIntersections()) {
            if (i.getType() == Intersection.Type.FIVE_WAY) {
                double r = i.getRadius() + 18;
                gc.fillOval(i.getCx() - r, i.getCy() - r, r * 2, r * 2);
            }
        }
    }

    private double getRoadOffsetRadius(Road road, SimScene scene, boolean startNode) {
        double rx = startNode ? road.getX1() : road.getX2();
        double ry = startNode ? road.getY1() : road.getY2();
        for (Intersection i : scene.getIntersections()) {
            if (Math.hypot(i.getCx() - rx, i.getCy() - ry) < 1) {
                if (i.getType() == Intersection.Type.FIVE_WAY) {
                    return i.getRadius();
                } else {
                    return 0; // standard intersection, extend asphalt and curb to center
                }
            }
        }
        return 0;
    }

    private double getIntersectionRadius(Road road, SimScene scene, boolean startNode) {
        double rx = startNode ? road.getX1() : road.getX2();
        double ry = startNode ? road.getY1() : road.getY2();
        for (Intersection i : scene.getIntersections()) {
            if (Math.hypot(i.getCx() - rx, i.getCy() - ry) < 1) {
                return i.getRadius() + 30.0;
            }
        }
        return 0;
    }

    private void drawRoadCurb(GraphicsContext gc, Road road, SimScene scene) {
        double lw = SimConfig.LANE_WIDTH;
        double fullW = lw * 8; // 4 lanes per direction
        
        double dx = road.getX2() - road.getX1();
        double dy = road.getY2() - road.getY1();
        double len = Math.hypot(dx, dy);
        if (len < 1) return;

        double r1 = getRoadOffsetRadius(road, scene, true);
        double r2 = getRoadOffsetRadius(road, scene, false);

        double ux = dx / len;
        double uy = dy / len;
        double lx1 = road.getX1() + ux * r1;
        double ly1 = road.getY1() + uy * r1;
        double lx2 = road.getX2() - ux * r2;
        double ly2 = road.getY2() - uy * r2;

        if (r1 + r2 >= len) return;

        boolean isNight = SimConfig.isNightMode();

        gc.setStroke(c(CURB)); 
        gc.setLineWidth(fullW + 12);
        gc.strokeLine(lx1, ly1, lx2, ly2);

        // Neon Glow Curb Outline at night
        if (isNight) {
            double px = -dy / len;
            double py =  dx / len;
            gc.save();
            gc.setEffect(new Glow(0.35));
            gc.setStroke(Color.rgb(0, 180, 255, 0.45)); // Soft Neon Blue
            gc.setLineWidth(2.0);
            double offsetDist = (fullW + 12) / 2.0;
            gc.strokeLine(lx1 + px * offsetDist, ly1 + py * offsetDist,
                          lx2 + px * offsetDist, ly2 + py * offsetDist);
            gc.strokeLine(lx1 - px * offsetDist, ly1 - py * offsetDist,
                          lx2 - px * offsetDist, ly2 - py * offsetDist);
            gc.restore();
        }
    }

    private void drawRoadAsphalt(GraphicsContext gc, Road road, SimScene scene) {
        double lw = SimConfig.LANE_WIDTH;
        double fullW = lw * 8; // 4 lanes per direction
        
        double dx = road.getX2() - road.getX1();
        double dy = road.getY2() - road.getY1();
        double len = Math.hypot(dx, dy);
        if (len < 1) return;

        double r1 = getRoadOffsetRadius(road, scene, true);
        double r2 = getRoadOffsetRadius(road, scene, false);

        double ux = dx / len;
        double uy = dy / len;
        double lx1 = road.getX1() + ux * r1;
        double ly1 = road.getY1() + uy * r1;
        double lx2 = road.getX2() - ux * r2;
        double ly2 = road.getY2() - uy * r2;

        if (r1 + r2 >= len) return;

        // Asphalt fill
        gc.setStroke(c(ASPHALT)); 
        gc.setLineWidth(fullW);
        gc.strokeLine(lx1, ly1, lx2, ly2);
    }

    private void drawRoadMarkings(GraphicsContext gc, Road road, SimScene scene) {
        double lw = SimConfig.LANE_WIDTH;
        
        double dx = road.getX2() - road.getX1();
        double dy = road.getY2() - road.getY1();
        double len = Math.hypot(dx, dy);
        if (len < 1) return;
        double px = -dy / len;
        double py =  dx / len;

        double r1 = getIntersectionRadius(road, scene, true);
        double r2 = getIntersectionRadius(road, scene, false);

        double ux = dx / len;
        double uy = dy / len;
        double lx1 = road.getX1() + ux * r1;
        double ly1 = road.getY1() + uy * r1;
        double lx2 = road.getX2() - ux * r2;
        double ly2 = road.getY2() - uy * r2;

        if (r1 + r2 >= len) return;

        boolean isNight = SimConfig.isNightMode();

        // Brown-tinted bicycle lane background in 3D projection
        gc.setStroke(c(Color.rgb(139, 90, 43, 0.28)));
        gc.setLineWidth(10.0);
        gc.strokeLine(lx1 + px * 59.0, ly1 + py * 59.0, lx2 + px * 59.0, ly2 + py * 59.0);
        gc.strokeLine(lx1 - px * 59.0, ly1 - py * 59.0, lx2 - px * 59.0, ly2 - py * 59.0);

        // Yellow double center line
        if (isNight) {
            gc.save();
            gc.setEffect(new Glow(0.35));
            gc.setStroke(Color.rgb(255, 215, 0, 0.75)); // Soft Yellow Glow
        } else {
            gc.setStroke(c(Color.rgb(240, 200, 30, 0.9))); 
        }
        gc.setLineWidth(2.0); 
        gc.setLineDashes(null);
        gc.strokeLine(lx1, ly1, lx2, ly2);
        if (isNight) {
            gc.restore();
        }

        // Animated lane dividers - disabled movement, only static glow
        if (isNight) {
            gc.save();
            gc.setEffect(new Glow(0.35));
            gc.setStroke(Color.rgb(255, 255, 255, 0.70)); // Soft White Glow
        } else {
            gc.setStroke(c(Color.rgb(230, 230, 230, 0.7))); 
        }
        gc.setLineWidth(1.2); 
        
        // Divider 1
        gc.setLineDashes(10, 8);
        gc.strokeLine(lx1 + px * 18.0, ly1 + py * 18.0, lx2 + px * 18.0, ly2 + py * 18.0);
        gc.strokeLine(lx1 - px * 18.0, ly1 - py * 18.0, lx2 - px * 18.0, ly2 - py * 18.0);

        // Divider 2
        gc.strokeLine(lx1 + px * 36.0, ly1 + py * 36.0, lx2 + px * 36.0, ly2 + py * 36.0);
        gc.strokeLine(lx1 - px * 36.0, ly1 - py * 36.0, lx2 - px * 36.0, ly2 - py * 36.0);

        if (isNight) {
            gc.restore();
        }
        
        // Solid divider for bicycle lane (lane 2-3)
        if (isNight) {
            gc.save();
            gc.setEffect(new Glow(0.35));
            gc.setStroke(Color.rgb(255, 255, 255, 0.70)); // Soft White Glow
        } else {
            gc.setStroke(c(Color.rgb(255, 255, 255, 0.9)));
        }
        gc.setLineDashes(null);
        gc.strokeLine(lx1 + px * 54.0, ly1 + py * 54.0, lx2 + px * 54.0, ly2 + py * 54.0);
        gc.strokeLine(lx1 - px * 54.0, ly1 - py * 54.0, lx2 - px * 54.0, ly2 - py * 54.0);
        if (isNight) {
            gc.restore();
        }
    }

    private void drawIntersection(GraphicsContext gc, Intersection inter) {
        double r = inter.getRadius();
        
        if (inter.getType() == Intersection.Type.FIVE_WAY) {
            gc.setFill(c(CURB));
            gc.fillOval(inter.getCx() - r - 6, inter.getCy() - r - 6, (r + 6)*2, (r + 6)*2);
            
            gc.setFill(c(ASPHALT));
            gc.fillOval(inter.getCx() - r, inter.getCy() - r, r*2, r*2);
            
            gc.setFill(c(Color.rgb(180, 180, 180)));
            gc.fillOval(inter.getCx() - r*0.48, inter.getCy() - r*0.48, r*0.96, r*0.96);
            gc.setStroke(c(CURB));
            gc.setLineWidth(2);
            gc.strokeOval(inter.getCx() - r*0.48, inter.getCy() - r*0.48, r*0.96, r*0.96);
            
            gc.setFill(c(Color.rgb(90, 190, 80)));
            double islandR = 56;
            gc.fillOval(inter.getCx() - islandR, inter.getCy() - islandR, islandR * 2, islandR * 2);
            
            if (SimConfig.isNightMode()) {
                gc.save();
                gc.setEffect(new Glow(0.85));
            }
            gc.setFill(c(Color.rgb(100, 200, 255, 0.8)));
            gc.fillOval(inter.getCx() - r*0.25, inter.getCy() - r*0.25, r*0.5, r*0.5);
            gc.setStroke(c(Color.rgb(200, 200, 200)));
            gc.setLineWidth(3);
            gc.strokeOval(inter.getCx() - r*0.25, inter.getCy() - r*0.25, r*0.5, r*0.5);
            if (SimConfig.isNightMode()) {
                gc.restore();
            }
            
            gc.setStroke(c(Color.rgb(255, 255, 255, 0.6)));
            gc.setLineWidth(1.5);
            gc.setLineDashes(10, 8);
            // Inner divider (between lane 0 and 1)
            double rDiv1 = 74;
            gc.strokeOval(inter.getCx() - rDiv1, inter.getCy() - rDiv1, rDiv1 * 2, rDiv1 * 2);
            // Middle divider (between lane 1 and 2)
            double rDiv2 = 92;
            gc.strokeOval(inter.getCx() - rDiv2, inter.getCy() - rDiv2, rDiv2 * 2, rDiv2 * 2);
            gc.setLineDashes(null);
            
            gc.setFill(c(Color.rgb(240, 240, 240)));
            gc.fillOval(inter.getCx() - r*0.1, inter.getCy() - r*0.1, r*0.2, r*0.2);
        }

        // Draw pedestrian crosswalks on all road arms
        if (inter.getType() != Intersection.Type.FIVE_WAY) {
            for (Direction dir : inter.getArms()) {
                BasicRenderer.drawBeautifulCrosswalk(gc, inter.getCx(), inter.getCy(), inter.getRadius(), dir.angleDeg);
            }
        } else {
            // Roundabout arms in 3D
            com.trafficsim.model.intersection.FiveWayIntersection fwi = (com.trafficsim.model.intersection.FiveWayIntersection) inter;
            for (int i = 0; i < fwi.getNumArms(); i++) {
                BasicRenderer.drawBeautifulCrosswalk(gc, inter.getCx(), inter.getCy(), inter.getRadius(), 360.0 - fwi.getArmAngle(i));
            }
        }

        // Note: text labels and chevron overlays are removed from the intersection center

        // Radial streetlight at night
        double amb = SimConfig.getAmbientLight();
        if (amb < 0.95) {
            gc.save();
            gc.setEffect(new Glow(0.65));
            javafx.scene.paint.RadialGradient shadow = new javafx.scene.paint.RadialGradient(
                0, 0, inter.getCx(), inter.getCy(), inter.getRadius() * 1.5, false,
                javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0.0, Color.rgb(255, 240, 180, (1.0 - amb) * 0.35)),
                new javafx.scene.paint.Stop(1.0, Color.TRANSPARENT)
            );
            gc.setFill(shadow);
            gc.fillOval(inter.getCx() - inter.getRadius()*1.5, inter.getCy() - inter.getRadius()*1.5, inter.getRadius()*3, inter.getRadius()*3);
            gc.restore();
        }
    }

    private void addCityBlockToRenderables(GraphicsContext gc, List<Renderable3D> renderables, double x, double y, double size, Random rng, SimScene scene) {
        double pad = 8;
        gc.setFill(c(Color.rgb(115, 175, 95)));
        gc.fillRect(x + pad, y + pad, size - pad*2, size - pad*2);
        gc.setStroke(c(Color.rgb(90, 150, 70)));
        gc.setLineWidth(2);
        gc.strokeRect(x + pad, y + pad, size - pad*2, size - pad*2);

        int type = rng.nextInt(5);
        if (type == 0) {
            gc.setFill(c(Color.rgb(190, 190, 170)));
            gc.fillOval(x + size/2 - 25, y + size/2 - 25, 50, 50);
            gc.fillOval(x + size/2 - 35, y + size/2 - 6, 70, 12);
            gc.fillOval(x + size/2 - 6, y + size/2 - 35, 12, 70);
            
            gc.setFill(c(Color.rgb(100, 180, 220))); 
            gc.fillOval(x + size/2 - 14, y + size/2 - 14, 28, 28);
            gc.setStroke(c(Color.WHITE));
            gc.setLineWidth(2);
            gc.strokeOval(x + size/2 - 14, y + size/2 - 14, 28, 28);
            
            addTreeToRenderables(gc, renderables, x + 25, y + 25, rng);
            addTreeToRenderables(gc, renderables, x + size - 25, y + 25, rng);
            addTreeToRenderables(gc, renderables, x + 25, y + size - 25, rng);
            addTreeToRenderables(gc, renderables, x + size - 25, y + size - 25, rng);
        } else if (type == 1) {
            // 3D Solar Farm
            gc.setFill(c(Color.rgb(190, 195, 200))); // concrete base
            gc.fillRect(x + pad + 2, y + pad + 2, size - pad*2 - 4, size - pad*2 - 4);
            
            // Add a small 3D power grid building in the center
            double bSize = 25;
            double bx = x + size/2 - bSize/2;
            double by = y + size/2 - bSize/2;
            renderables.add(new Renderable3D(by + bSize/2 - 0.5 * (bx + bSize/2), () -> 
                draw3DBuilding(gc, bx, by, bSize, bSize, 22.0, Color.rgb(100, 110, 120), rng)
            ));
            
            // Draw flat solar panels around it
            for (double px = x + pad + 8; px < x + size - pad - 20; px += 28) {
                for (double py = y + pad + 8; py < y + size - pad - 20; py += 28) {
                    if (Math.abs(px - (x + size/2)) < 20 && Math.abs(py - (y + size/2)) < 20) continue;
                    double fpx = px;
                    double fpy = py;
                    renderables.add(new Renderable3D(fpy - 0.5 * fpx, () -> {
                        double[] p1 = getProjPt(fpx + 8, fpy + 4, 0, -8, -6, 0);
                        double[] p2 = getProjPt(fpx + 8, fpy + 4, 0, 8, -6, 0);
                        double[] p3 = getProjPt(fpx + 8, fpy + 4, 0, 8, 6, 2.0); // tilted!
                        double[] p4 = getProjPt(fpx + 8, fpy + 4, 0, -8, 6, 2.0); // tilted!
                        
                        gc.setFill(c(Color.rgb(20, 60, 120)));
                        gc.fillPolygon(new double[]{p1[0], p2[0], p3[0], p4[0]}, new double[]{p1[1], p2[1], p3[1], p4[1]}, 4);
                        gc.setStroke(c(Color.rgb(180, 180, 190)));
                        gc.setLineWidth(1.0);
                        gc.strokePolygon(new double[]{p1[0], p2[0], p3[0], p4[0]}, new double[]{p1[1], p2[1], p3[1], p4[1]}, 4);
                    }));
                }
            }
        } else if (type == 2) {
            // 3D Plaza Garden
            gc.setFill(c(Color.rgb(222, 210, 190))); // brick paths
            gc.fillRect(x + pad + 2, y + pad + 2, size - pad*2 - 4, size - pad*2 - 4);
            
            // Pond in the center
            gc.setFill(c(Color.rgb(80, 160, 200)));
            gc.fillOval(x + size/2 - 20, y + size/2 - 20, 40, 40);
            gc.setStroke(c(Color.rgb(140, 130, 120)));
            gc.setLineWidth(2.0);
            gc.strokeOval(x + size/2 - 20, y + size/2 - 20, 40, 40);
            
            addTreeToRenderables(gc, renderables, x + 25, y + 25, rng);
            addTreeToRenderables(gc, renderables, x + size - 25, y + size - 25, rng);
        } else {
            double bW = size * (0.55 + rng.nextDouble() * 0.25);
            double bD = size * (0.55 + rng.nextDouble() * 0.25);
            double bx = x + (size - bW) / 2;
            double by = y + (size - bD) / 2;
            double bHeight = 45 + rng.nextDouble() * 95;
            
            Color roofColor = switch(rng.nextInt(4)) {
                case 0 -> Color.rgb(190, 80, 70);
                case 1 -> Color.rgb(75, 120, 160);
                case 2 -> Color.rgb(110, 170, 130);
                default -> Color.rgb(190, 190, 200);
            };
            
            gc.setFill(c(Color.rgb(0, 0, 0, 0.22)));
            gc.fillRect(bx + 5, by + 5, bW, bD);

            double centerX = bx + bW/2;
            double centerY = by + bD/2;
            renderables.add(new Renderable3D(centerY - 0.5 * centerX, () -> draw3DBuilding(gc, bx, by, bW, bD, bHeight, roofColor, rng)));
            
            if (rng.nextBoolean()) addTreeToRenderables(gc, renderables, bx - 6, by + bD/2, rng);
            if (rng.nextBoolean()) addTreeToRenderables(gc, renderables, bx + bW + 6, by + bD/2, rng);
            if (rng.nextBoolean()) addTreeToRenderables(gc, renderables, bx + bW/2, by - 6, rng);
        }
    }

    private void addTreeToRenderables(GraphicsContext gc, List<Renderable3D> renderables, double tx, double ty, Random rng) {
        double r = 9 + rng.nextDouble() * 6;
        Color leafColor = Color.rgb(35 + rng.nextInt(25), 115 + rng.nextInt(40), 35 + rng.nextInt(25));
        renderables.add(new Renderable3D(ty - 0.5 * tx, () -> draw3DTree(gc, tx, ty, r, leafColor)));
    }

    private void draw3DTree(GraphicsContext gc, double tx, double ty, double r, Color leafColor) {
        double trunkHeight = 11;
        double canopyHeight = trunkHeight + r;
        
        gc.setStroke(c(Color.rgb(101, 67, 33)));
        gc.setLineWidth(3.0);
        gc.strokeLine(tx, ty, getProjX(tx, trunkHeight), getProjY(ty, trunkHeight));
        
        gc.setFill(c(Color.rgb(0, 0, 0, 0.2)));
        gc.fillOval(tx - 3, ty - 2, 6, 4);
        
        gc.setFill(c(Color.rgb(0, 0, 0, 0.22)));
        gc.fillOval(tx - r*0.7, ty - r*0.45, r*1.4, r*0.9);
        
        double cpx = getProjX(tx, canopyHeight);
        double cpy = getProjY(ty, canopyHeight);
        
        gc.setFill(c(leafColor));
        gc.fillOval(cpx - r, cpy - r, r*2, r*2);
        
        gc.setFill(c(leafColor.brighter().brighter()));
        gc.fillOval(cpx - r*0.5, cpy - r*0.6, r, r);
    }

    private void draw3DBuilding(GraphicsContext gc, double bx, double by, double bW, double bD, double bHeight, Color roofColor, Random rng) {
        double x1 = bx, y1 = by;
        double x2 = bx + bW, y2 = by;
        double x3 = bx + bW, y3 = by + bD;
        double x4 = bx, y4 = by + bD;
        
        double rx1 = getProjX(x1, bHeight), ry1 = getProjY(y1, bHeight);
        double rx2 = getProjX(x2, bHeight), ry2 = getProjY(y2, bHeight);
        double rx3 = getProjX(x3, bHeight), ry3 = getProjY(y3, bHeight);
        double rx4 = getProjX(x4, bHeight), ry4 = getProjY(y4, bHeight);
        
        Color westColor = c(roofColor.darker());
        Color southColor = c(roofColor.darker().darker());
        Color northColor = c(roofColor.darker().darker().darker());
        Color eastColor = c(northColor.darker());
        
        gc.setFill(northColor);
        gc.fillPolygon(new double[]{x1, x2, rx2, rx1}, new double[]{y1, y2, ry2, ry1}, 4);
        
        gc.setFill(eastColor);
        gc.fillPolygon(new double[]{x2, x3, rx3, rx2}, new double[]{y2, y3, ry3, ry2}, 4);
        
        gc.setFill(westColor);
        gc.fillPolygon(new double[]{x4, x1, rx1, rx4}, new double[]{y4, y1, ry1, ry4}, 4);
        drawWindowsOnWall(gc, new double[]{x4, y4}, new double[]{x1, y1}, new double[]{rx1, ry1}, new double[]{rx4, ry4}, bHeight);
        
        gc.setFill(southColor);
        gc.fillPolygon(new double[]{x3, x4, rx4, rx3}, new double[]{y3, y4, ry4, ry3}, 4);
        drawWindowsOnWall(gc, new double[]{x3, y3}, new double[]{x4, y4}, new double[]{rx4, ry4}, new double[]{rx3, ry3}, bHeight);
        
        gc.setFill(c(roofColor));
        gc.fillPolygon(new double[]{rx1, rx2, rx3, rx4}, new double[]{ry1, ry2, ry3, ry4}, 4);
        
        gc.setStroke(c(roofColor.brighter()));
        gc.setLineWidth(1.5);
        gc.strokePolygon(new double[]{rx1, rx2, rx3, rx4}, new double[]{ry1, ry2, ry3, ry4}, 4);
        
        if (bW > 50 && bD > 50 && rng.nextBoolean()) {
            // Helipad on 3D roof
            double rcx = (rx1 + rx3) / 2.0;
            double rcy = (ry1 + ry3) / 2.0;
            
            gc.setStroke(c(Color.WHITE));
            gc.setLineWidth(1.5);
            double hRad = Math.min(bW, bD) * 0.25;
            gc.strokeOval(rcx - hRad, rcy - hRad * 0.7, hRad * 2, hRad * 1.4);
            
            gc.setFill(c(Color.WHITE));
            gc.setFont(Font.font("Arial Bold", Math.max(8, hRad * 0.8)));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("H", rcx, rcy + hRad * 0.3);
        } else {
            // AC Units
            gc.setFill(c(Color.rgb(130, 130, 130)));
            double acSize = 10;
            double acx = rx1 + (rx3 - rx1)*0.3;
            double acy = ry1 + (ry3 - ry1)*0.3;
            gc.fillRect(acx, acy, acSize, acSize);
            gc.setFill(c(Color.rgb(70, 70, 70)));
            gc.fillOval(acx + 1.5, acy + 1.5, acSize - 3, acSize - 3);
        }
    }

    private void drawWindowsOnWall(GraphicsContext gc, double[] A, double[] B, double[] C, double[] D, double bHeight) {
        int floors = (int)(bHeight / 14);
        int cols = 4;
        if (floors < 2) return;
        
        double amb = SimConfig.getAmbientLight();
        boolean isNight = SimConfig.isNightMode();
        
        for (int f = 0; f < floors; f++) {
            double v1 = 0.12 + ((double)f / floors) * 0.75;
            double v2 = v1 + (0.5 / floors);
            
            for (int c = 0; c < cols; c++) {
                double u1 = 0.15 + ((double)c / cols) * 0.7;
                double u2 = u1 + (0.5 / cols);
                
                double[] w1 = getBilinearPoint(A, B, C, D, u1, v1);
                double[] w2 = getBilinearPoint(A, B, C, D, u2, v1);
                double[] w3 = getBilinearPoint(A, B, C, D, u2, v2);
                double[] w4 = getBilinearPoint(A, B, C, D, u1, v2);
                
                if (isNight) {
                    int coordHash = (int)(A[0] * 17 + A[1] * 31 + f * 7 + c * 13);
                    if (coordHash % 4 == 0) {
                        gc.save();
                        gc.setEffect(new Glow(0.85));
                        gc.setFill(Color.rgb(255, 235, 120, (1.0 - amb) * 0.95));
                        gc.fillPolygon(new double[]{w1[0], w2[0], w3[0], w4[0]}, new double[]{w1[1], w2[1], w3[1], w4[1]}, 4);
                        gc.restore();
                        continue;
                    }
                }
                
                gc.setFill(Color.color(0.7 * amb, 0.85 * amb, 1.0 * amb, 0.65));
                gc.fillPolygon(new double[]{w1[0], w2[0], w3[0], w4[0]}, new double[]{w1[1], w2[1], w3[1], w4[1]}, 4);
            }
        }
    }

    private void draw3DTrafficLight(GraphicsContext gc, TrafficLight tl) {
        double x = tl.getX();
        double y = tl.getY();
        double poleHeight = 28;
        
        gc.setStroke(c(Color.rgb(0, 0, 0, 0.25)));
        gc.setLineWidth(2.5);
        gc.strokeLine(x, y, x + 6, y + 4);
        
        double px0 = x;
        double py0 = y;
        double px1 = getProjX(x, poleHeight);
        double py1 = getProjY(y, poleHeight);
        gc.setStroke(c(Color.rgb(80, 80, 80)));
        gc.setLineWidth(2.5);
        gc.strokeLine(px0, py0, px1, py1);
        
        double armLength = 20; // Extended length to hang over the lanes
        double armAngleRad = Math.toRadians(tl.getAngleDeg() - 90); // Perpendicular to the road arm, pointing over the road
        double ax0 = px1;
        double ay0 = py1;
        double ax1 = px1 + armLength * Math.cos(armAngleRad);
        double ay1 = py1 + armLength * Math.sin(armAngleRad);
        
        gc.setStroke(c(Color.rgb(90, 90, 90)));
        gc.setLineWidth(1.8);
        gc.strokeLine(ax0, ay0, ax1, ay1);
        
        double hx = ax1;
        double hy = ay1;
        
        gc.setFill(c(Color.rgb(20, 20, 20)));
        gc.fillRoundRect(hx - 3.5, hy - 7, 7, 14, 2, 2);
        
        boolean rOn = tl.isRed(), yOn = tl.isYellow(), gOn = tl.isGreen();
        double r = 2.0;
        
        if (rOn) gc.setEffect(new Glow(0.85));
        gc.setFill(rOn ? Color.rgb(255, 40, 40) : Color.rgb(55, 0, 0));
        gc.fillOval(hx - r, hy - 5, r*2, r*2);
        gc.setEffect(null);
        
        if (yOn) gc.setEffect(new Glow(0.85));
        gc.setFill(yOn ? Color.rgb(255, 215, 0) : Color.rgb(55, 52, 0));
        gc.fillOval(hx - r, hy - r, r*2, r*2);
        gc.setEffect(null);
        
        if (gOn) gc.setEffect(new Glow(0.85));
        gc.setFill(gOn ? Color.rgb(40, 255, 40) : Color.rgb(0, 50, 0));
        gc.fillOval(hx - r, hy + 5 - r*2, r*2, r*2);
        gc.setEffect(null);
        
        if (tl.shouldShowCountdown()) {
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 8));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.valueOf((int)Math.ceil(tl.getRemainingSeconds())), hx, hy - 9);
        }
    }

    private double[] getProjPt(double cx, double cy, double phi, double dx, double dy, double z) {
        double cos = Math.cos(phi);
        double sin = Math.sin(phi);
        double wx = cx + dx * cos - dy * sin;
        double wy = cy + dx * sin + dy * cos;
        return new double[]{ getProjX(wx, z), getProjY(wy, z) };
    }

    private void draw3DBoxPart(GraphicsContext gc, double cx, double cy, double phi,
                               double xMin, double xMax, double yMin, double yMax,
                               double zMin, double zMax, Color color) {
        double[][] base = new double[4][2];
        double[][] roof = new double[4][2];
        
        base[0] = getProjPt(cx, cy, phi, xMin, yMin, zMin);
        base[1] = getProjPt(cx, cy, phi, xMax, yMin, zMin);
        base[2] = getProjPt(cx, cy, phi, xMax, yMax, zMin);
        base[3] = getProjPt(cx, cy, phi, xMin, yMax, zMin);
        
        roof[0] = getProjPt(cx, cy, phi, xMin, yMin, zMax);
        roof[1] = getProjPt(cx, cy, phi, xMax, yMin, zMax);
        roof[2] = getProjPt(cx, cy, phi, xMax, yMax, zMax);
        roof[3] = getProjPt(cx, cy, phi, xMin, yMax, zMax);
        
        Color roofColor = color;
        Color wallColor1 = c(color.darker());
        Color wallColor2 = c(color.darker().darker());
        Color wallColor1Brighter = c(color.darker().brighter());
        
        // North face (0 -> 1)
        gc.setFill(wallColor1);
        gc.fillPolygon(new double[]{base[0][0], base[1][0], roof[1][0], roof[0][0]},
                       new double[]{base[0][1], base[1][1], roof[1][1], roof[0][1]}, 4);
                       
        // West face (0 -> 3)
        gc.setFill(wallColor2);
        gc.fillPolygon(new double[]{base[0][0], base[3][0], roof[3][0], roof[0][0]},
                       new double[]{base[0][1], base[3][1], roof[3][1], roof[0][1]}, 4);
                       
        // East face (1 -> 2)
        gc.setFill(wallColor1Brighter);
        gc.fillPolygon(new double[]{base[1][0], base[2][0], roof[2][0], roof[1][0]},
                       new double[]{base[1][1], base[2][1], roof[2][1], roof[1][1]}, 4);
                       
        // South face (2 -> 3)
        gc.setFill(wallColor2);
        gc.fillPolygon(new double[]{base[2][0], base[3][0], roof[3][0], roof[2][0]},
                       new double[]{base[2][1], base[3][1], roof[3][1], roof[2][1]}, 4);
                       
        // Roof
        gc.setFill(c(roofColor));
        gc.fillPolygon(new double[]{roof[0][0], roof[1][0], roof[2][0], roof[3][0]},
                       new double[]{roof[0][1], roof[1][1], roof[2][1], roof[3][1]}, 4);
                       
        // Roof border
        gc.setStroke(c(roofColor.brighter()));
        gc.setLineWidth(0.8);
        gc.strokePolygon(new double[]{roof[0][0], roof[1][0], roof[2][0], roof[3][0]},
                         new double[]{roof[0][1], roof[1][1], roof[2][1], roof[3][1]}, 4);
    }

    private void draw3DSlopedFace(GraphicsContext gc, double cx, double cy, double phi,
                                  double xBottom, double xTop, double yMin, double yMax,
                                  double zBottom, double zTop, Color color) {
        double[] bL = getProjPt(cx, cy, phi, xBottom, yMin, zBottom);
        double[] bR = getProjPt(cx, cy, phi, xBottom, yMax, zBottom);
        double[] tR = getProjPt(cx, cy, phi, xTop, yMax, zTop);
        double[] tL = getProjPt(cx, cy, phi, xTop, yMin, zTop);
        
        gc.setFill(c(color));
        gc.fillPolygon(new double[]{bL[0], bR[0], tR[0], tL[0]},
                       new double[]{bL[1], bR[1], tR[1], tL[1]}, 4);
        gc.setStroke(c(color.brighter()));
        gc.setLineWidth(0.5);
        gc.strokePolygon(new double[]{bL[0], bR[0], tR[0], tL[0]},
                         new double[]{bL[1], bR[1], tR[1], tL[1]}, 4);
    }

    private void draw3DVehicle(GraphicsContext gc, Vehicle v) {
        double x = v.getX();
        double y = v.getY();
        double phi = Math.toRadians(v.getHeadingAngleDeg());
        double length = v.getLength();
        double width = v.getWidth();
        
        double Hz = 11;
        if (v instanceof com.trafficsim.model.vehicle.Bus) Hz = 20;
        else if (v instanceof com.trafficsim.model.vehicle.FireTruck) Hz = 22;
        else if (v instanceof com.trafficsim.model.vehicle.Ambulance) Hz = 17;
        else if (v instanceof com.trafficsim.model.vehicle.PoliceCar) Hz = 12;
        else if (v instanceof com.trafficsim.model.vehicle.Motorbike) Hz = 8.5;
        else if (v instanceof com.trafficsim.model.vehicle.Bicycle) Hz = 7.5;
        
        this.drawingVehicle = true;
        try {
            if (v instanceof com.trafficsim.model.vehicle.Motorbike || v instanceof com.trafficsim.model.vehicle.Bicycle) {
                draw3DTwoWheeler(gc, v, length, width, Hz);
                return;
            }
            
            // Ground shadow
            gc.setFill(Color.rgb(0, 0, 0, 0.35));
            gc.save();
            gc.translate(x, y);
            gc.rotate(v.getHeadingAngleDeg());
            gc.fillRoundRect(-length/2 - 1, -width/2 - 1, length + 2, width + 2, 4, 4);
            gc.restore();
            
            draw3DHeadlightBeams(gc, x, y, phi, length, width);
            
            Color baseColor = Color.web(v.getColor());
            if (v instanceof com.trafficsim.model.vehicle.PoliceCar) {
                baseColor = Color.rgb(20, 20, 25);
            } else if (v instanceof com.trafficsim.model.vehicle.Ambulance) {
                baseColor = Color.WHITE;
            } else if (v instanceof com.trafficsim.model.vehicle.FireTruck) {
                baseColor = Color.rgb(200, 30, 30);
            }
            
            double L2 = length / 2;
            double W2 = width / 2;
            Color glassColor = Color.rgb(25, 30, 45, 0.85);
    
            if (v instanceof com.trafficsim.model.vehicle.Bus) {
                // BUS MODEL
                draw3DBoxPart(gc, x, y, phi, -L2, L2, -W2, W2, 0, Hz, baseColor);
                draw3DSlopedFace(gc, x, y, phi, L2 - 0.5, L2 - 0.5, -W2 * 0.9, W2 * 0.9, Hz * 0.3, Hz * 0.85, glassColor);
                
                // Side window bands
                double[] wL1 = getProjPt(x, y, phi, -L2 * 0.9, -W2 - 0.1, Hz * 0.4);
                double[] wL2 = getProjPt(x, y, phi, L2 * 0.8, -W2 - 0.1, Hz * 0.4);
                double[] wL3 = getProjPt(x, y, phi, L2 * 0.8, -W2 - 0.1, Hz * 0.8);
                double[] wL4 = getProjPt(x, y, phi, -L2 * 0.9, -W2 - 0.1, Hz * 0.8);
                gc.setFill(glassColor);
                gc.fillPolygon(new double[]{wL1[0], wL2[0], wL3[0], wL4[0]}, new double[]{wL1[1], wL2[1], wL3[1], wL4[1]}, 4);
                
                double[] wR1 = getProjPt(x, y, phi, -L2 * 0.9, W2 + 0.1, Hz * 0.4);
                double[] wR2 = getProjPt(x, y, phi, L2 * 0.8, W2 + 0.1, Hz * 0.4);
                double[] wR3 = getProjPt(x, y, phi, L2 * 0.8, W2 + 0.1, Hz * 0.8);
                double[] wR4 = getProjPt(x, y, phi, -L2 * 0.9, W2 + 0.1, Hz * 0.8);
                gc.setFill(glassColor);
                gc.fillPolygon(new double[]{wR1[0], wR2[0], wR3[0], wR4[0]}, new double[]{wR1[1], wR2[1], wR3[1], wR4[1]}, 4);
                
            } else if (v instanceof com.trafficsim.model.vehicle.Ambulance) {
                // AMBULANCE MODEL
                draw3DBoxPart(gc, x, y, phi, L2 * 0.2, L2, -W2, W2, 0, Hz * 0.6, baseColor);
                draw3DBoxPart(gc, x, y, phi, -L2, L2 * 0.2, -W2, W2, 0, Hz, baseColor);
                draw3DSlopedFace(gc, x, y, phi, L2 * 0.2, L2 * 0.15, -W2 * 0.9, W2 * 0.9, Hz * 0.6, Hz, glassColor);
                
                // Red stripes
                double[] sL1 = getProjPt(x, y, phi, -L2 * 0.95, -W2 - 0.1, Hz * 0.35);
                double[] sL2 = getProjPt(x, y, phi, L2 * 0.15, -W2 - 0.1, Hz * 0.35);
                double[] sL3 = getProjPt(x, y, phi, L2 * 0.15, -W2 - 0.1, Hz * 0.5);
                double[] sL4 = getProjPt(x, y, phi, -L2 * 0.95, -W2 - 0.1, Hz * 0.5);
                gc.setFill(Color.rgb(220, 30, 30));
                gc.fillPolygon(new double[]{sL1[0], sL2[0], sL3[0], sL4[0]}, new double[]{sL1[1], sL2[1], sL3[1], sL4[1]}, 4);
                
                // Red cross on side
                double[] cL1_h = getProjPt(x, y, phi, -L2 * 0.45, -W2 - 0.2, Hz * 0.55);
                double[] cL2_h = getProjPt(x, y, phi, -L2 * 0.25, -W2 - 0.2, Hz * 0.55);
                double[] cL3_h = getProjPt(x, y, phi, -L2 * 0.25, -W2 - 0.2, Hz * 0.65);
                double[] cL4_h = getProjPt(x, y, phi, -L2 * 0.45, -W2 - 0.2, Hz * 0.65);
                gc.setFill(Color.rgb(220, 30, 30));
                gc.fillPolygon(new double[]{cL1_h[0], cL2_h[0], cL3_h[0], cL4_h[0]}, new double[]{cL1_h[1], cL2_h[1], cL3_h[1], cL4_h[1]}, 4);
                
                double[] cL1_v = getProjPt(x, y, phi, -L2 * 0.38, -W2 - 0.2, Hz * 0.47);
                double[] cL2_v = getProjPt(x, y, phi, -L2 * 0.32, -W2 - 0.2, Hz * 0.47);
                double[] cL3_v = getProjPt(x, y, phi, -L2 * 0.32, -W2 - 0.2, Hz * 0.73);
                double[] cL4_v = getProjPt(x, y, phi, -L2 * 0.38, -W2 - 0.2, Hz * 0.73);
                gc.fillPolygon(new double[]{cL1_v[0], cL2_v[0], cL3_v[0], cL4_v[0]}, new double[]{cL1_v[1], cL2_v[1], cL3_v[1], cL4_v[1]}, 4);
                
            } else if (v instanceof com.trafficsim.model.vehicle.FireTruck) {
                // FIRE TRUCK MODEL
                draw3DBoxPart(gc, x, y, phi, L2 * 0.2, L2, -W2, W2, 0, Hz, baseColor);
                draw3DBoxPart(gc, x, y, phi, -L2, L2 * 0.2, -W2 * 0.95, W2 * 0.95, 0, Hz * 0.75, Color.rgb(180, 25, 25));
                draw3DSlopedFace(gc, x, y, phi, L2 - 0.5, L2 - 0.5, -W2 * 0.9, W2 * 0.9, Hz * 0.4, Hz * 0.85, glassColor);
                
                // Ladder on top
                draw3DBoxPart(gc, x, y, phi, -L2 * 0.8, L2 * 0.1, -W2 * 0.25, W2 * 0.25, Hz * 0.75, Hz * 0.75 + 3.0, Color.rgb(200, 200, 200));
                gc.setStroke(Color.rgb(130, 130, 130));
                gc.setLineWidth(1.0);
                for (double rxVal = -L2 * 0.7; rxVal <= L2 * 0.0; rxVal += 3.5) {
                    double[] lp1 = getProjPt(x, y, phi, rxVal, -W2 * 0.25, Hz * 0.75 + 3.0);
                    double[] lp2 = getProjPt(x, y, phi, rxVal, W2 * 0.25, Hz * 0.75 + 3.0);
                    gc.strokeLine(lp1[0], lp1[1], lp2[0], lp2[1]);
                }
                
            } else {
                // SEDAN / CAR MODEL
                boolean isPolice = (v instanceof com.trafficsim.model.vehicle.PoliceCar);
                Color cabColor = isPolice ? Color.WHITE : baseColor;
                
                draw3DBoxPart(gc, x, y, phi, L2 * 0.2, L2, -W2, W2, 0, Hz * 0.55, baseColor);
                draw3DBoxPart(gc, x, y, phi, -L2 * 0.3, L2 * 0.2, -W2 * 0.9, W2 * 0.9, 0, Hz, cabColor);
                draw3DBoxPart(gc, x, y, phi, -L2, -L2 * 0.3, -W2, W2, 0, Hz * 0.55, baseColor);
                
                draw3DSlopedFace(gc, x, y, phi, L2 * 0.2, L2 * 0.12, -W2 * 0.8, W2 * 0.8, Hz * 0.55, Hz, glassColor);
                draw3DSlopedFace(gc, x, y, phi, -L2 * 0.3, -L2 * 0.38, -W2 * 0.8, W2 * 0.8, Hz, Hz * 0.55, glassColor);
                
                if (isPolice) {
                    double[] textP = getProjPt(x, y, phi, -L2 * 0.05, 0, Hz + 0.1);
                    gc.save();
                    gc.setFill(Color.rgb(20, 20, 30));
                    gc.setFont(Font.font("Arial Bold", 6));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText("POLICE", textP[0], textP[1] + 1);
                    gc.restore();
                }
            }
            
            // Thin white highlight outline at night for 3D boxes
            double amb = SimConfig.getAmbientLight();
            if (amb < 0.9) {
                double[] p1 = getProjPt(x, y, phi, -L2, -W2, 0);
                double[] p2 = getProjPt(x, y, phi, L2, -W2, 0);
                double[] p3 = getProjPt(x, y, phi, L2, W2, 0);
                double[] p4 = getProjPt(x, y, phi, -L2, W2, 0);
                gc.setStroke(Color.rgb(255, 255, 255, 0.40));
                gc.setLineWidth(0.8);
                gc.strokePolygon(new double[]{p1[0], p2[0], p3[0], p4[0]}, new double[]{p1[1], p2[1], p3[1], p4[1]}, 4);
            }
            
            // Headlights / Taillights
            double headlightZ = Hz * 0.25;
            if (v instanceof com.trafficsim.model.vehicle.Bus) headlightZ = Hz * 0.2;
            
            double[] hlLeft = getProjPt(x, y, phi, L2, -W2 * 0.6, headlightZ);
            double[] hlRight = getProjPt(x, y, phi, L2, W2 * 0.6, headlightZ);
            gc.setFill(Color.rgb(255, 255, 200, 0.95));
            gc.fillOval(hlLeft[0] - 1.5, hlLeft[1] - 1.5, 3, 3);
            gc.fillOval(hlRight[0] - 1.5, hlRight[1] - 1.5, 3, 3);
            
            double[] tlLeft = getProjPt(x, y, phi, -L2, -W2 * 0.6, headlightZ);
            double[] tlRight = getProjPt(x, y, phi, -L2, W2 * 0.6, headlightZ);
            gc.setFill(Color.rgb(255, 30, 30, 0.95));
            gc.fillRect(tlLeft[0] - 1.5, tlLeft[1] - 1.0, 3, 2);
            gc.fillRect(tlRight[0] - 1.5, tlRight[1] - 1.0, 3, 2);
    
            // Flashing Light Bars for Emergency Vehicles
            if (v instanceof com.trafficsim.model.vehicle.PoliceCar || 
                v instanceof com.trafficsim.model.vehicle.Ambulance || 
                v instanceof com.trafficsim.model.vehicle.FireTruck) {
                
                double zBar = Hz;
                double xBar = 0;
                if (v instanceof com.trafficsim.model.vehicle.PoliceCar) {
                    xBar = -L2 * 0.05;
                } else if (v instanceof com.trafficsim.model.vehicle.Ambulance) {
                    xBar = L2 * 0.05;
                } else if (v instanceof com.trafficsim.model.vehicle.FireTruck) {
                    xBar = L2 * 0.6;
                }
                
                long time = System.currentTimeMillis();
                boolean flash = (time % 200) < 100;
                
                gc.save();
                gc.setEffect(new Glow(0.9));
                double[] leftL = getProjPt(x, y, phi, xBar, -W2 * 0.4, zBar + 1.0);
                double[] rightL = getProjPt(x, y, phi, xBar, W2 * 0.4, zBar + 1.0);
                
                gc.setFill(flash ? Color.rgb(255, 30, 30) : Color.rgb(30, 80, 255));
                gc.fillOval(leftL[0] - 2, leftL[1] - 2, 4, 4);
                gc.setFill(!flash ? Color.rgb(255, 30, 30) : Color.rgb(30, 80, 255));
                gc.fillOval(rightL[0] - 2, rightL[1] - 2, 4, 4);
                gc.restore();
            }
        } finally {
            this.drawingVehicle = false;
        }
    }

    private void drawVehicleWindows3D(GraphicsContext gc, double[][] base, double[][] roof) {
        // Obsolete
    }

    private void draw3DHeadlightBeams(GraphicsContext gc, double x, double y, double phi, double length, double width) {
        double amb = SimConfig.getAmbientLight();
        if (amb >= 0.95) return;
        
        double beamLen = 65;
        double beamWidth = 25;
        
        double cos = Math.cos(phi);
        double sin = Math.sin(phi);
        
        double fx = x + (length / 2) * cos;
        double fy = y + (length / 2) * sin;
        
        double lx = fx - (width / 2.5) * sin;
        double ly = fy + (width / 2.5) * cos;
        
        double rx = fx + (width / 2.5) * sin;
        double ry = fy - (width / 2.5) * cos;
        
        double blx = lx + beamLen * cos - (beamWidth / 2) * sin;
        double bly = ly + beamLen * sin + (beamWidth / 2) * cos;
        
        double brx = rx + beamLen * cos + (beamWidth / 2) * sin;
        double bry = ry + beamLen * sin - (beamWidth / 2) * cos;
        
        double plx = getProjX(lx, 0); double ply = getProjY(ly, 0);
        double prx = getProjX(rx, 0); double pry = getProjY(ry, 0);
        double pblx = getProjX(blx, 0); double pbly = getProjY(bly, 0);
        double pbrx = getProjX(brx, 0); double pbry = getProjY(bry, 0);
        
        double alpha = (1.0 - amb) * 0.28;
        gc.setFill(Color.rgb(255, 255, 180, alpha));
        gc.fillPolygon(new double[]{plx, pblx, pbrx, prx}, new double[]{ply, pbly, pbry, pry}, 4);
    }

    private void draw3DTwoWheeler(GraphicsContext gc, Vehicle v, double length, double width, double Hz) {
        double x = v.getX();
        double y = v.getY();
        double phi = Math.toRadians(v.getHeadingAngleDeg());
        double cos = Math.cos(phi);
        double sin = Math.sin(phi);
        
        gc.setFill(Color.rgb(0, 0, 0, 0.3));
        gc.save();
        gc.translate(x, y);
        gc.rotate(v.getHeadingAngleDeg());
        gc.fillOval(-length/2, -1.2, length, 2.4);
        gc.restore();
        
        double L2 = length / 2;
        
        // 1. Draw Front Wheel
        double[] fWheelCenter = getProjPt(x, y, phi, L2 * 0.6, 0, 2.0);
        gc.setStroke(Color.rgb(30, 30, 30));
        gc.setLineWidth(1.6);
        gc.strokeLine(fWheelCenter[0], fWheelCenter[1] + 2.0, fWheelCenter[0], fWheelCenter[1] - 2.0);
        
        // 2. Draw Rear Wheel
        double[] rWheelCenter = getProjPt(x, y, phi, -L2 * 0.6, 0, 2.0);
        gc.strokeLine(rWheelCenter[0], rWheelCenter[1] + 2.0, rWheelCenter[0], rWheelCenter[1] - 2.0);
        
        // 3. Draw Frame / Body
        Color bikeColor = Color.web(v.getColor());
        double[] frameB = getProjPt(x, y, phi, -L2 * 0.4, 0, 2.5);
        double[] frameF = getProjPt(x, y, phi, L2 * 0.4, 0, 4.0);
        gc.setStroke(bikeColor);
        gc.setLineWidth(2.5);
        gc.strokeLine(frameB[0], frameB[1], frameF[0], frameF[1]);
        
        // 4. Draw Rider
        draw3DBoxPart(gc, x, y, phi, -L2 * 0.3, L2 * 0.1, -width * 0.35, width * 0.35, 3.5, Hz, Color.rgb(50, 50, 55));
        double[] helmPos = getProjPt(x, y, phi, -L2 * 0.1, 0, Hz + 2.0);
        gc.setFill(Color.rgb(30, 35, 45));
        gc.fillOval(helmPos[0] - 2.2, helmPos[1] - 2.2, 4.4, 4.4);
        
        // Handlebars
        double[] handleL = getProjPt(x, y, phi, L2 * 0.35, -width * 0.45, 5.5);
        double[] handleR = getProjPt(x, y, phi, L2 * 0.35, width * 0.45, 5.5);
        gc.setStroke(Color.rgb(10, 10, 10));
        gc.setLineWidth(1.2);
        gc.strokeLine(handleL[0], handleL[1], handleR[0], handleR[1]);
    }

    private boolean isClearOfRoads(double cx, double cy, double blockRadius, double safeDist, SimScene scene) {
        for (Intersection i : scene.getIntersections()) {
            if (Math.hypot(i.getCx() - cx, i.getCy() - cy) < i.getRadius() + blockRadius + 15) return false;
        }
        for (Road r : scene.getRoads()) {
            double l2 = Math.pow(r.getX2() - r.getX1(), 2) + Math.pow(r.getY2() - r.getY1(), 2);
            if (l2 == 0) continue;
            double t = Math.max(0, Math.min(1, ((cx - r.getX1()) * (r.getX2() - r.getX1()) + (cy - r.getY1()) * (r.getY2() - r.getY1())) / l2));
            double projX = r.getX1() + t * (r.getX2() - r.getX1());
            double projY = r.getY1() + t * (r.getY2() - r.getY1());
            if (Math.hypot(cx - projX, cy - projY) < safeDist + blockRadius) return false;
        }
        return true;
    }

    private Color c(Color base) {
        double amb = SimConfig.getAmbientLight();
        double factor = drawingVehicle ? Math.max(0.60, amb) : amb;
        return Color.color(base.getRed() * factor, base.getGreen() * factor, base.getBlue() * factor, base.getOpacity());
    }

    @Override
    public String getModeName() {
        return "3D";
    }
}
