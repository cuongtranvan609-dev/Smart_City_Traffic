package com.trafficsim.view.renderer;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.Direction;
import com.trafficsim.model.SimScene;
import com.trafficsim.model.TrafficLight;
import com.trafficsim.model.intersection.Intersection;
import com.trafficsim.model.road.Road;
import com.trafficsim.model.vehicle.Vehicle;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.Glow;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.Random;

public class GraphicRenderer implements SceneRenderer {
    private static final Color ASPHALT  = Color.rgb(50, 50, 55);
    private static final Color CURB     = Color.rgb(140, 140, 140);
    private static final Color SIDEWALK = Color.rgb(215, 215, 215);
    private boolean drawingVehicle = false;

    @Override
    public void render(GraphicsContext gc, SimScene scene, double zoom, double panX, double panY) {
        double W = gc.getCanvas().getWidth(), H = gc.getCanvas().getHeight();
        gc.clearRect(0,0,W,H);
        
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

        // 1. Draw Sidewalk Background Grid
        drawBackground(gc, scene, minX, maxX, minY, maxY);
        
        // 2. Draw Procedural City Blocks (Buildings, Parks, Trees)
        drawCityBlocks(gc, scene, minX, maxX, minY, maxY);

        // 3. Draw Roads (Asphalt & Curb)
        scene.getRoads().forEach(r -> drawRoad(gc, r, scene));
        
        // 4. Draw Intersections (Asphalt, Crosswalks, Markings)
        scene.getIntersections().forEach(i -> drawIntersection(gc, i));
        
        // 5. Draw Turn Guides and Stop Lines
        BasicRenderer.drawTurnGuides(gc, scene);
        scene.getRoads().forEach(r -> BasicRenderer.drawStopLines(gc, r));
        
        // 6. Draw Traffic Lights
        scene.getIntersections().forEach(i -> i.getTrafficLights().forEach(tl -> drawLight(gc, tl)));
        
        // 7. Draw Vehicles
        scene.getVehicles().forEach(v -> drawVehicle(gc, v));

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
            gc.strokeLine(r.getX1(), r.getY1(), r.getX2(), r.getY2());
        }
        
        // Draw sidewalks around all intersections
        gc.setFill(c(SIDEWALK));
        for (Intersection i : scene.getIntersections()) {
            double r = i.getRadius() + 18;
            gc.fillOval(i.getCx() - r, i.getCy() - r, r * 2, r * 2);
        }
    }

    private void drawCityBlocks(GraphicsContext gc, SimScene scene, double minX, double maxX, double minY, double maxY) {
        double cellSize = 130;
        Random rng = new Random(42); // deterministic seed for consistent city layout
        
        for (double x = minX; x < maxX; x += cellSize) {
            for (double y = minY; y < maxY; y += cellSize) {
                double cx = x + cellSize/2;
                double cy = y + cellSize/2;
                
                // Safe distance from road center is RoadHalfWidth + margin
                double safeDist = SimConfig.ROAD_HALF_WIDTH + 20; 
                if (isClearOfRoads(cx, cy, cellSize/2 * 1.4, safeDist, scene)) {
                    drawCityBlock(gc, x, y, cellSize, rng);
                }
            }
        }
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

    private void drawCityBlock(GraphicsContext gc, double x, double y, double size, Random rng) {
        double pad = 8;
        // Block base (grass)
        gc.setFill(c(Color.rgb(115, 175, 95)));
        gc.fillRect(x + pad, y + pad, size - pad*2, size - pad*2);
        // Grass border
        gc.setStroke(c(Color.rgb(90, 150, 70)));
        gc.setLineWidth(2);
        gc.strokeRect(x + pad, y + pad, size - pad*2, size - pad*2);

        int type = rng.nextInt(5);
        if (type == 0) {
            // Park with fountain
            gc.setFill(c(Color.rgb(190, 190, 170))); // Paths
            gc.fillOval(x + size/2 - 25, y + size/2 - 25, 50, 50);
            gc.fillOval(x + size/2 - 35, y + size/2 - 6, 70, 12);
            gc.fillOval(x + size/2 - 6, y + size/2 - 35, 12, 70);
            
            // Fountain
            if (SimConfig.isNightMode()) {
                gc.setEffect(new Glow(0.8));
            }
            gc.setFill(Color.rgb(100, 180, 220)); 
            gc.fillOval(x + size/2 - 14, y + size/2 - 14, 28, 28);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2);
            gc.strokeOval(x + size/2 - 14, y + size/2 - 14, 28, 28);
            gc.setEffect(null);
            
            // Trees around fountain
            drawTree(gc, x + 25, y + 25, rng);
            drawTree(gc, x + size - 25, y + 25, rng);
            drawTree(gc, x + 25, y + size - 25, rng);
            drawTree(gc, x + size - 25, y + size - 25, rng);
        } else if (type == 1) {
            // Solar Farm (Smart City Clean Energy)
            gc.setFill(c(Color.rgb(190, 195, 200))); // concrete base
            gc.fillRect(x + pad + 2, y + pad + 2, size - pad*2 - 4, size - pad*2 - 4);
            
            // Draw solar panel grids
            gc.setFill(c(Color.rgb(20, 60, 120))); // Dark Blue Silicon
            gc.setStroke(c(Color.rgb(180, 180, 190))); // Aluminum Frame
            gc.setLineWidth(1.2);
            for (double px = x + pad + 8; px < x + size - pad - 20; px += 24) {
                for (double py = y + pad + 8; py < y + size - pad - 20; py += 24) {
                    gc.fillRect(px, py, 18, 14);
                    gc.strokeRect(px, py, 18, 14);
                    // Draw grid reflection lines on silicon
                    gc.setStroke(Color.rgb(255, 255, 255, 0.2));
                    gc.strokeLine(px + 4, py + 2, px + 14, py + 12);
                    gc.setStroke(c(Color.rgb(180, 180, 190)));
                }
            }
        } else if (type == 2) {
            // Plaza Garden with Pond and Flowers
            gc.setFill(c(Color.rgb(222, 210, 190))); // Brick/sand paths
            gc.fillRect(x + pad + 2, y + pad + 2, size - pad*2 - 4, size - pad*2 - 4);
            
            // Pond in the center
            gc.setFill(c(Color.rgb(80, 160, 200)));
            gc.fillOval(x + size/2 - 20, y + size/2 - 20, 40, 40);
            gc.setStroke(c(Color.rgb(140, 130, 120)));
            gc.setLineWidth(2.0);
            gc.strokeOval(x + size/2 - 20, y + size/2 - 20, 40, 40);
            
            // Bushes and trees
            drawTree(gc, x + 25, y + 25, rng);
            drawTree(gc, x + size - 25, y + size - 25, rng);
            
            // Flower beds (red/yellow spots)
            gc.setFill(c(Color.rgb(230, 80, 120))); // Red/Pink
            gc.fillOval(x + size/2 - 28, y + 20, 5, 5);
            gc.fillOval(x + size/2 - 20, y + 18, 5, 5);
            gc.setFill(c(Color.rgb(250, 200, 50))); // Yellow
            gc.fillOval(x + 20, y + size/2 + 10, 5, 5);
            gc.fillOval(x + 24, y + size/2 + 18, 5, 5);
        } else {
            // Building
            double bW = size * (0.55 + rng.nextDouble() * 0.25);
            double bH = size * (0.55 + rng.nextDouble() * 0.25);
            double bx = x + (size - bW) / 2;
            double by = y + (size - bH) / 2;
            
            // Shadow
            gc.setFill(Color.rgb(0, 0, 0, 0.35));
            gc.fillRect(bx + 6, by + 8, bW, bH);
            
            // Roof Colors
            Color roof = rng.nextBoolean() ? Color.rgb(190, 90, 80) : Color.rgb(210, 210, 220);
            if (rng.nextInt(4) == 0) roof = Color.rgb(80, 120, 150);
            
            gc.setFill(c(roof));
            gc.fillRect(bx, by, bW, bH);
            gc.setStroke(c(roof.darker()));
            gc.setLineWidth(3);
            gc.strokeRect(bx, by, bW, bH);
            
            // AC Units / Helipad
            if (bW > 60 && bH > 60 && rng.nextBoolean()) {
                // Draw Helipad
                double cx = bx + bW/2;
                double cy = by + bH/2;
                gc.setStroke(c(Color.WHITE));
                gc.setLineWidth(2.0);
                gc.strokeOval(cx - 15, cy - 15, 30, 30);
                gc.setFill(c(Color.WHITE));
                gc.setFont(Font.font("Arial Bold", 14));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText("H", cx, cy + 5);
            } else {
                gc.setFill(c(Color.rgb(170, 170, 170)));
                gc.fillRect(bx + 12, by + 12, 14, 14);
                gc.setFill(c(Color.rgb(90, 90, 90)));
                gc.fillOval(bx + 14, by + 14, 10, 10);
            }
            
            if (bW > 50 && bH > 50 && rng.nextBoolean() && !(bW > 60 && bH > 60)) {
                gc.setFill(c(Color.rgb(170, 170, 170)));
                gc.fillRect(bx + bW - 26, by + bH - 26, 14, 14);
                gc.setFill(c(Color.rgb(90, 90, 90)));
                gc.fillOval(bx + bW - 24, by + bH - 24, 10, 10);
            }
            
            // Rooftop skylights at night
            double amb = SimConfig.getAmbientLight();
            if (amb < 0.7 && (int)(bx + by) % 2 == 0) {
                gc.save();
                gc.setEffect(new Glow(0.8));
                gc.setFill(Color.rgb(255, 235, 120, (1.0 - amb) * 0.95));
                gc.fillRect(bx + bW * 0.3, by + bH * 0.4, 5, 4);
                gc.fillRect(bx + bW * 0.6, by + bH * 0.4, 5, 4);
                gc.restore();
            }
            
            // Trees around building
            if (rng.nextBoolean()) drawTree(gc, bx - 5, by + bH/2, rng);
            if (rng.nextBoolean()) drawTree(gc, bx + bW + 5, by + bH/2, rng);
            if (rng.nextBoolean()) drawTree(gc, bx + bW/2, by - 5, rng);
        }
    }

    private void drawTree(GraphicsContext gc, double tx, double ty, Random rng) {
        double r = 10 + rng.nextDouble() * 7;
        gc.setFill(Color.rgb(0, 0, 0, 0.4)); // shadow
        gc.fillOval(tx - r + 3, ty - r + 4, r*2, r*2);
        
        Color base = Color.rgb(30 + rng.nextInt(30), 110 + rng.nextInt(40), 30 + rng.nextInt(30));
        gc.setFill(base);
        gc.fillOval(tx - r, ty - r, r*2, r*2);
        
        // Highlight
        gc.setFill(base.brighter().brighter());
        gc.fillOval(tx - r*0.5, ty - r*0.5, r, r);
    }

    private void drawRoad(GraphicsContext gc, Road road, SimScene scene) {
        double lw = SimConfig.LANE_WIDTH;
        double fullW = lw * 8; // 4 lanes per direction
        
        double dx = road.getX2() - road.getX1();
        double dy = road.getY2() - road.getY1();
        double len = Math.hypot(dx, dy);
        if (len < 1) return;
        double px = -dy / len;
        double py =  dx / len;

        double r1 = 0, r2 = 0;
        for (Intersection i : scene.getIntersections()) {
            if (Math.hypot(i.getCx() - road.getX1(), i.getCy() - road.getY1()) < 1) r1 = i.getRadius();
            if (Math.hypot(i.getCx() - road.getX2(), i.getCy() - road.getY2()) < 1) r2 = i.getRadius();
        }

        double ux = dx / len;
        double uy = dy / len;
        // Extend slightly into the intersection to avoid gaps, but not all the way to center
        double lx1 = road.getX1() + ux * (r1 * 0.9);
        double ly1 = road.getY1() + uy * (r1 * 0.9);
        double lx2 = road.getX2() - ux * (r2 * 0.9);
        double ly2 = road.getY2() - uy * (r2 * 0.9);

        if (r1 + r2 >= len) return;

        boolean isNight = SimConfig.isNightMode();

        // Curb border
        gc.setStroke(c(CURB)); 
        gc.setLineWidth(fullW + 12);
        gc.strokeLine(lx1, ly1, lx2, ly2);
        
        // Asphalt fill
        gc.setStroke(c(ASPHALT)); 
        gc.setLineWidth(fullW);
        gc.strokeLine(lx1, ly1, lx2, ly2);

        // Neon Glow Curb Outline at night
        if (isNight) {
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

        // Brown-tinted bicycle lane background
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
        
        // Divider 1 (offset 18.0)
        gc.setLineDashes(10, 8);
        gc.strokeLine(lx1 + px * 18.0, ly1 + py * 18.0, lx2 + px * 18.0, ly2 + py * 18.0);
        gc.strokeLine(lx1 - px * 18.0, ly1 - py * 18.0, lx2 - px * 18.0, ly2 - py * 18.0);

        // Divider 2 (offset 36.0)
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
        
        // Curb outline
        gc.setFill(c(CURB));
        gc.fillOval(inter.getCx() - r - 6, inter.getCy() - r - 6, (r + 6)*2, (r + 6)*2);
        
        // Asphalt fill
        gc.setFill(c(ASPHALT));
        gc.fillOval(inter.getCx() - r, inter.getCy() - r, r*2, r*2);
 
        if (inter.getType() == Intersection.Type.FIVE_WAY) {
            // Beautiful Roundabout Center Island (Fountain / Monument)
            // Outer decorative ring
            gc.setFill(c(Color.rgb(180, 180, 180)));
            gc.fillOval(inter.getCx() - r*0.48, inter.getCy() - r*0.48, r*0.96, r*0.96);
            gc.setStroke(c(CURB));
            gc.setLineWidth(2);
            gc.strokeOval(inter.getCx() - r*0.48, inter.getCy() - r*0.48, r*0.96, r*0.96);
            
            // Grass island
            gc.setFill(c(Color.rgb(90, 190, 80))); // Vibrant grass
            double islandR = 56;
            gc.fillOval(inter.getCx() - islandR, inter.getCy() - islandR, islandR * 2, islandR * 2);
            
            // Center fountain water pool
            if (SimConfig.isNightMode()) {
                gc.save();
                gc.setEffect(new Glow(0.85));
            }
            gc.setFill(c(Color.rgb(100, 200, 255, 0.8))); // Water blue
            gc.fillOval(inter.getCx() - r*0.25, inter.getCy() - r*0.25, r*0.5, r*0.5);
            gc.setStroke(c(Color.rgb(200, 200, 200)));
            gc.setLineWidth(3);
            gc.strokeOval(inter.getCx() - r*0.25, inter.getCy() - r*0.25, r*0.5, r*0.5);
            if (SimConfig.isNightMode()) {
                gc.restore();
            }
            
            // Roundabout lane dividers (dashed/solid circles)
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
            
            // Monument / Fountain core
            gc.setFill(c(Color.rgb(240, 240, 240)));
            gc.fillOval(inter.getCx() - r*0.1, inter.getCy() - r*0.1, r*0.2, r*0.2);
            
            // Note: Dashed guide lines and extra markings are intentionally removed 
        } else {
            // Center directional chevrons
            gc.setStroke(c(Color.rgb(255, 215, 0, 0.85)));
            gc.setLineWidth(2);
            double dist = r * 0.45;
            for (Direction d : Direction.values()) {
                gc.save();
                gc.translate(inter.getCx() + d.dx * dist, inter.getCy() + d.dy * dist);
                gc.rotate(d.angleDeg); 
                gc.strokeLine(0, 0, -6, -5);
                gc.strokeLine(0, 0, -6, 5);
                gc.restore();
            }
        }

        // Draw pedestrian crosswalks on all road arms
        if (inter.getType() != Intersection.Type.FIVE_WAY) {
            for (Direction dir : inter.getArms()) {
                BasicRenderer.drawBeautifulCrosswalk(gc, inter.getCx(), inter.getCy(), inter.getRadius(), dir.angleDeg);
            }
        } else {
            // Roundabout intersection arms
            com.trafficsim.model.intersection.FiveWayIntersection fwi = (com.trafficsim.model.intersection.FiveWayIntersection) inter;
            for (int i = 0; i < fwi.getNumArms(); i++) {
                BasicRenderer.drawBeautifulCrosswalk(gc, inter.getCx(), inter.getCy(), inter.getRadius(), 360.0 - fwi.getArmAngle(i));
            }
        }

        // Intersection text
        gc.setFill(c(Color.rgb(255, 255, 255, 0.6)));
        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(inter.getTypeName(), inter.getCx(), inter.getCy() + 4);

        // Draw streetlights at intersections at night
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


    private void drawTwoWheeler(GraphicsContext gc, Vehicle v, double vh, double vw) {
        gc.setFill(c(Color.rgb(30, 30, 30)));
        gc.fillOval(-vh/2, -1.5, 5, 3); // Rear wheel
        gc.fillOval(vh/2 - 5, -1.5, 5, 3); // Front wheel
        
        gc.setFill(c(Color.web(v.getColor())));
        gc.fillRoundRect(-vh/2 + 2, -vw/2 + 1, vh - 6, vw - 2, 3, 3);
        
        gc.setStroke(c(Color.DARKGRAY));
        gc.setLineWidth(1.5);
        gc.strokeLine(vh/2 - 7, -vw/2 - 0.5, vh/2 - 7, vw/2 + 0.5);
        
        gc.setFill(c(v instanceof com.trafficsim.model.vehicle.Motorbike ? Color.rgb(200, 50, 50) : Color.rgb(50, 150, 200))); 
        gc.fillOval(-3, -vw/2 + 1, 7, vw - 2); 
    }
 
    private void drawPoliceCar(GraphicsContext gc, Vehicle v, double vh, double vw) {
        // Police car body: Black front and rear, White middle (doors)
        gc.setFill(c(Color.rgb(20, 20, 25))); // Dark black
        gc.fillRoundRect(-vh/2, -vw/2, vh, vw, 4, 4);
        
        gc.setFill(c(Color.WHITE)); // White center
        gc.fillRect(-vh*0.18, -vw/2, vh*0.36, vw);
        
        // Windshield and windows
        gc.setFill(c(Color.rgb(30, 30, 35)));
        gc.fillRect(-vh/2 + vh*0.65, -vw/2 + 2, vh*0.18, vw - 4); 
        gc.fillRect(-vh/2 + vh*0.15, -vw/2 + 2, vh*0.12, vw - 4); 
        
        // Side mirrors / details
        gc.setFill(c(Color.web(v.getColor()).brighter()));
        gc.fillRect(-vh*0.1, -vw/2 + 1, vh*0.2, 1);
        gc.fillRect(-vh*0.1,  vw/2 - 2, vh*0.2, 1);
        
        // Headlights and tail lights
        gc.setFill(Color.LIGHTYELLOW);
        gc.fillOval(vh/2 - 2, -vw/2 + 1, 3, 3);
        gc.fillOval(vh/2 - 2, vw/2 - 4, 3, 3);
        gc.setFill(Color.RED);
        gc.fillOval(-vh/2, -vw/2 + 1, 2, 3);
        gc.fillOval(-vh/2, vw/2 - 4, 2, 3);
        
        // Flashing Light Bar (Red and Blue)
        long time = System.currentTimeMillis();
        boolean flash = (time % 200) < 100;
        if (SimConfig.isNightMode()) gc.setEffect(new Glow(0.85));
        gc.setFill(flash ? Color.rgb(255, 30, 30) : Color.rgb(30, 80, 255));
        gc.fillRoundRect(-2, -vw/2 + 2, 4, vw - 4, 1, 1);
        gc.setEffect(null);
    }
 
    private void drawCar(GraphicsContext gc, Vehicle v, double vh, double vw) {
        gc.setFill(c(Color.web(v.getColor())));
        gc.fillRoundRect(-vh/2, -vw/2, vh, vw, 4, 4);
        
        gc.setFill(c(Color.rgb(30, 30, 35)));
        gc.fillRect(-vh/2 + vh*0.65, -vw/2 + 2, vh*0.18, vw - 4); 
        gc.fillRect(-vh/2 + vh*0.15, -vw/2 + 2, vh*0.12, vw - 4); 
        
        gc.setFill(c(Color.web(v.getColor()).brighter()));
        gc.fillRect(-vh/2 + vh*0.3, -vw/2 + 2.5, vh*0.3, vw - 5);
        
        gc.setFill(Color.LIGHTYELLOW);
        gc.fillOval(vh/2 - 2, -vw/2 + 1, 3, 3);
        gc.fillOval(vh/2 - 2, vw/2 - 4, 3, 3);
        
        gc.setFill(Color.RED);
        gc.fillOval(-vh/2, -vw/2 + 1, 2, 3);
        gc.fillOval(-vh/2, vw/2 - 4, 2, 3);
    }
 
    private void drawAmbulance(GraphicsContext gc, Vehicle v, double vh, double vw) {
        gc.setFill(c(Color.WHITE));
        gc.fillRoundRect(-vh/2, -vw/2, vh, vw, 3, 3);
        
        gc.setFill(c(Color.RED));
        double cw = 6, ch = 2;
        gc.fillRect(-cw/2, -ch/2, cw, ch);
        gc.fillRect(-ch/2, -cw/2, ch, cw);
        
        gc.setFill(c(Color.rgb(30, 30, 35)));
        gc.fillRect(vh/2 - 6, -vw/2 + 1.5, 4, vw - 3);
        
        long time = System.currentTimeMillis();
        boolean flash = (time % 400) < 200;
        if (SimConfig.isNightMode()) gc.setEffect(new Glow(0.85));
        gc.setFill(flash ? Color.RED : Color.rgb(100,0,0));
        gc.fillOval(vh/2 - 4, -vw/2 + 1, 4, 3);
        gc.setFill(!flash ? Color.BLUE : Color.rgb(0,0,100));
        gc.fillOval(vh/2 - 4, vw/2 - 4, 4, 3);
        gc.setEffect(null);
    }
 
    private void drawFireTruck(GraphicsContext gc, Vehicle v, double vh, double vw) {
        gc.setFill(c(Color.rgb(200, 30, 30)));
        gc.fillRoundRect(-vh/2, -vw/2, vh, vw, 2, 2);
        
        gc.setFill(c(Color.LIGHTGRAY));
        gc.fillRect(-vh/2 + 4, -vw/2 + 3, vh - 12, vw - 6);
        gc.setStroke(c(Color.DARKGRAY));
        gc.setLineWidth(1);
        for(double i = -vh/2 + 6; i < vh/2 - 10; i+= 4) {
            gc.strokeLine(i, -vw/2 + 3, i, vw/2 - 3);
        }
        
        gc.setFill(c(Color.rgb(30, 30, 35)));
        gc.fillRect(vh/2 - 7, -vw/2 + 1.5, 5, vw - 3);
        
        long time = System.currentTimeMillis();
        boolean flash = (time % 300) < 150;
        if (SimConfig.isNightMode()) gc.setEffect(new Glow(0.85));
        gc.setFill(flash ? Color.RED : Color.rgb(100,0,0));
        gc.fillRect(vh/2 - 5, -vw/2 + 1, 3, 3);
        gc.setFill(!flash ? Color.RED : Color.rgb(100,0,0));
        gc.fillRect(vh/2 - 5, vw/2 - 4, 3, 3);
        gc.setEffect(null);
    }
 
    private void drawBus(GraphicsContext gc, Vehicle v, double vh, double vw) {
        gc.setFill(c(Color.web(v.getColor())));
        gc.fillRoundRect(-vh/2, -vw/2, vh, vw, 2, 2);
        
        gc.setFill(c(Color.LIGHTGRAY));
        gc.fillRect(-vh/2 + 6, -vw/2 + 4, 8, vw - 8);
        gc.fillRect(-vh/2 + 18, -vw/2 + 4, 8, vw - 8);
        
        gc.setFill(c(Color.rgb(30, 30, 35)));
        gc.fillRect(vh/2 - 5, -vw/2 + 1.5, 4, vw - 3);
        
        gc.setFill(c(Color.rgb(40, 40, 50)));
        gc.fillRect(-vh/2 + 3, -vw/2 + 1.5, vh - 10, 2);
        gc.fillRect(-vh/2 + 3, vw/2 - 3.5, vh - 10, 2);
    }

    private void drawLight(GraphicsContext gc, TrafficLight tl) {
        double bx = tl.getX() - 5, by = tl.getY() - 14;
        double bw = 10, bh = 28;
        // Post
        gc.setFill(Color.rgb(60, 60, 60)); 
        gc.fillRect(tl.getX() - 1.5, by + bh, 3, 12);
        // Housing
        gc.setFill(Color.rgb(15, 15, 15)); 
        gc.fillRoundRect(bx, by, bw, bh, 4, 4);
        
        double r = 3.8;
        boolean rOn = tl.isRed(), yOn = tl.isYellow(), gOn = tl.isGreen();
        
        if (rOn) gc.setEffect(new Glow(0.8));
        gc.setFill(rOn ? Color.rgb(255, 40, 40) : Color.rgb(55, 0, 0));
        gc.fillOval(tl.getX() - r, by + 2, r*2, r*2);
        gc.setEffect(null);
        
        if (yOn) gc.setEffect(new Glow(0.8));
        gc.setFill(yOn ? Color.rgb(255, 215, 0) : Color.rgb(55, 52, 0));
        gc.fillOval(tl.getX() - r, by + bh/2 - r, r*2, r*2);
        gc.setEffect(null);
        
        if (gOn) gc.setEffect(new Glow(0.8));
        gc.setFill(gOn ? Color.rgb(40, 255, 40) : Color.rgb(0, 50, 0));
        gc.fillOval(tl.getX() - r, by + bh - r*2 - 2, r*2, r*2);
        gc.setEffect(null);
        
        if (tl.shouldShowCountdown()) {
            gc.setFill(Color.WHITE); 
            gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 9));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.valueOf((int)Math.ceil(tl.getRemainingSeconds())), tl.getX(), tl.getY() + 16);
        }
    }

    private void drawVehicle(GraphicsContext gc, Vehicle v) {
        double vw = v.getWidth(), vh = v.getLength();
        double amb = SimConfig.getAmbientLight();
        
        this.drawingVehicle = true;
        try {
            // Draw headlights if night
            if (amb < 0.9 && !(v instanceof com.trafficsim.model.vehicle.Bicycle)) {
                double alpha = (1.0 - amb) * 0.28;
                gc.save();
                gc.translate(v.getRenderX(), v.getRenderY());
                gc.rotate(v.getHeadingAngleDeg());
                gc.setEffect(new Glow(0.6));
                gc.setFill(Color.rgb(255, 255, 200, alpha));
                // Left headlight cone
                gc.fillPolygon(new double[]{vh/2, vh/2 + 65, vh/2 + 65}, new double[]{-vw/3, -vw/3 - 18, -vw/3 + 12}, 3);
                // Right headlight cone
                gc.fillPolygon(new double[]{vh/2, vh/2 + 65, vh/2 + 65}, new double[]{vw/3, vw/3 - 12, vw/3 + 18}, 3);
                gc.restore();
            }
            
            gc.save();
            gc.translate(v.getRenderX(), v.getRenderY());
            gc.rotate(v.getHeadingAngleDeg());
            
            // Shadow for 3D effect
            gc.setFill(Color.rgb(0, 0, 0, 0.45));
            gc.fillRoundRect(-vh/2 + 2, -vw/2 + 3, vh, vw, 4, 4);
            
            if (v instanceof com.trafficsim.model.vehicle.PoliceCar) {
                drawPoliceCar(gc, v, vh, vw);
            } else if (v instanceof com.trafficsim.model.vehicle.Motorbike || v instanceof com.trafficsim.model.vehicle.Bicycle) {
                drawTwoWheeler(gc, v, vh, vw);
            } else if (v instanceof com.trafficsim.model.vehicle.Ambulance) {
                drawAmbulance(gc, v, vh, vw);
            } else if (v instanceof com.trafficsim.model.vehicle.FireTruck) {
                drawFireTruck(gc, v, vh, vw);
            } else if (v instanceof com.trafficsim.model.vehicle.Bus) {
                drawBus(gc, v, vh, vw);
            } else {
                drawCar(gc, v, vh, vw);
            }
            
            // Thin white highlight outline at night
            if (amb < 0.9) {
                gc.setStroke(Color.rgb(255, 255, 255, 0.40));
                gc.setLineWidth(0.8);
                gc.strokeRoundRect(-vh/2, -vw/2, vh, vw, 4, 4);
            }
            
            if (v.isYieldingForPriority()) {
                gc.setStroke(Color.ORANGE); 
                gc.setLineWidth(1.5);
                gc.strokeRoundRect(-vh/2, -vw/2, vh, vw, 4, 4);
            }
            if (v.isSignalOn()) {
                gc.setFill(Color.rgb(255, 180, 0, 0.9));
                double bSize = Math.max(2.0, Math.min(4.5, vw * 0.25));
                double offset = vw * 0.05 + 0.5; // slight padding from edge
                
                if (v.isSignalLeftOn())  gc.fillOval(vh/2 - bSize*1.2, -vw/2 + offset, bSize, bSize); // Left blinker
                if (v.isSignalRightOn()) gc.fillOval(vh/2 - bSize*1.2, vw/2 - bSize - offset, bSize, bSize); // Right blinker
            }
            gc.restore();
        } finally {
            this.drawingVehicle = false;
        }
    }
 
    private Color c(Color base) {
        double amb = SimConfig.getAmbientLight();
        double factor = drawingVehicle ? Math.max(0.60, amb) : amb;
        return Color.color(base.getRed() * factor, base.getGreen() * factor, base.getBlue() * factor, base.getOpacity());
    }
    @Override public String getModeName() { return "Do hoa 2.0"; }
}
