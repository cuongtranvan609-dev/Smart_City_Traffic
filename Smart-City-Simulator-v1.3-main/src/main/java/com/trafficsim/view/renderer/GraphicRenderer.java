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
        drawBackground(gc, minX, maxX, minY, maxY);
        
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

    private void drawBackground(GraphicsContext gc, double minX, double maxX, double minY, double maxY) {
        gc.setFill(SIDEWALK);
        gc.fillRect(minX, minY, maxX - minX, maxY - minY);
        
        // Pavement grid lines
        gc.setStroke(Color.rgb(195, 195, 195));
        gc.setLineWidth(1.5);
        // Align grid
        double startX = Math.floor(minX / 24) * 24;
        double startY = Math.floor(minY / 24) * 24;
        
        for (double i = startX; i < maxX; i += 24) {
            gc.strokeLine(i, minY, i, maxY);
        }
        for (double i = startY; i < maxY; i += 24) {
            gc.strokeLine(minX, i, maxX, i);
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
        gc.setFill(Color.rgb(115, 175, 95));
        gc.fillRect(x + pad, y + pad, size - pad*2, size - pad*2);
        // Grass border
        gc.setStroke(Color.rgb(90, 150, 70));
        gc.setLineWidth(2);
        gc.strokeRect(x + pad, y + pad, size - pad*2, size - pad*2);

        int type = rng.nextInt(5);
        if (type == 0) {
            // Park with fountain
            gc.setFill(Color.rgb(190, 190, 170)); // Paths
            gc.fillOval(x + size/2 - 25, y + size/2 - 25, 50, 50);
            gc.fillOval(x + size/2 - 35, y + size/2 - 6, 70, 12);
            gc.fillOval(x + size/2 - 6, y + size/2 - 35, 12, 70);
            
            // Fountain
            gc.setFill(Color.rgb(100, 180, 220)); 
            gc.fillOval(x + size/2 - 14, y + size/2 - 14, 28, 28);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2);
            gc.strokeOval(x + size/2 - 14, y + size/2 - 14, 28, 28);
            
            // Trees around fountain
            drawTree(gc, x + 25, y + 25, rng);
            drawTree(gc, x + size - 25, y + 25, rng);
            drawTree(gc, x + 25, y + size - 25, rng);
            drawTree(gc, x + size - 25, y + size - 25, rng);
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
            
            gc.setFill(roof);
            gc.fillRect(bx, by, bW, bH);
            gc.setStroke(roof.darker());
            gc.setLineWidth(3);
            gc.strokeRect(bx, by, bW, bH);
            
            // AC Units
            gc.setFill(Color.rgb(170, 170, 170));
            gc.fillRect(bx + 12, by + 12, 14, 14);
            gc.setFill(Color.rgb(90, 90, 90));
            gc.fillOval(bx + 14, by + 14, 10, 10);
            
            if (bW > 50 && bH > 50 && rng.nextBoolean()) {
                gc.setFill(Color.rgb(170, 170, 170));
                gc.fillRect(bx + bW - 26, by + bH - 26, 14, 14);
                gc.setFill(Color.rgb(90, 90, 90));
                gc.fillOval(bx + bW - 24, by + bH - 24, 10, 10);
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
        double fullW = lw * 6; // 3 lanes per direction
        
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

        // Curb border
        gc.setStroke(CURB); 
        gc.setLineWidth(fullW + 12);
        gc.strokeLine(lx1, ly1, lx2, ly2);
        
        // Asphalt fill
        gc.setStroke(ASPHALT); 
        gc.setLineWidth(fullW);
        gc.strokeLine(lx1, ly1, lx2, ly2);

        // Yellow double center line
        gc.setStroke(Color.rgb(240, 200, 30, 0.9)); 
        gc.setLineWidth(2.0); 
        gc.setLineDashes(null);
        gc.strokeLine(lx1, ly1, lx2, ly2);

        // White dashed lane dividers
        gc.setStroke(Color.rgb(230, 230, 230, 0.7)); 
        gc.setLineWidth(1.2); 
        gc.setLineDashes(10, 8);
        // Inner divider
        gc.strokeLine(lx1 + px * lw, ly1 + py * lw, lx2 + px * lw, ly2 + py * lw);
        gc.strokeLine(lx1 - px * lw, ly1 - py * lw, lx2 - px * lw, ly2 - py * lw);
        // Outer divider
        gc.strokeLine(lx1 + px * lw * 2, ly1 + py * lw * 2, lx2 + px * lw * 2, ly2 + py * lw * 2);
        gc.strokeLine(lx1 - px * lw * 2, ly1 - py * lw * 2, lx2 - px * lw * 2, ly2 - py * lw * 2);
        gc.setLineDashes(null);

    }

    private void drawIntersection(GraphicsContext gc, Intersection inter) {
        double r = inter.getRadius();
        
        // Curb outline
        gc.setFill(CURB);
        gc.fillOval(inter.getCx() - r - 6, inter.getCy() - r - 6, (r + 6)*2, (r + 6)*2);
        
        // Asphalt fill
        gc.setFill(ASPHALT);
        gc.fillOval(inter.getCx() - r, inter.getCy() - r, r*2, r*2);

        if (inter.getType() == Intersection.Type.FIVE_WAY) {
            // Beautiful Roundabout Center Island (Fountain / Monument)
            // Outer decorative ring
            gc.setFill(Color.rgb(180, 180, 180));
            gc.fillOval(inter.getCx() - r*0.48, inter.getCy() - r*0.48, r*0.96, r*0.96);
            gc.setStroke(CURB);
            gc.setLineWidth(2);
            gc.strokeOval(inter.getCx() - r*0.48, inter.getCy() - r*0.48, r*0.96, r*0.96);
            
            // Grass island
            gc.setFill(Color.rgb(90, 190, 80)); // Vibrant grass
            double islandR = 56;
            gc.fillOval(inter.getCx() - islandR, inter.getCy() - islandR, islandR * 2, islandR * 2);
            
            // Center fountain water pool
            gc.setFill(Color.rgb(100, 200, 255, 0.8)); // Water blue
            gc.fillOval(inter.getCx() - r*0.25, inter.getCy() - r*0.25, r*0.5, r*0.5);
            gc.setStroke(Color.rgb(200, 200, 200));
            gc.setLineWidth(3);
            gc.strokeOval(inter.getCx() - r*0.25, inter.getCy() - r*0.25, r*0.5, r*0.5);
            
            // Roundabout 3-lane dividers (dashed circles)
            gc.setStroke(Color.rgb(255, 255, 255, 0.6));
            gc.setLineWidth(1.5);
            gc.setLineDashes(10, 8);
            // Inner divider (between lane 0 and 1)
            double rDiv1 = 72;
            gc.strokeOval(inter.getCx() - rDiv1, inter.getCy() - rDiv1, rDiv1 * 2, rDiv1 * 2);
            // Outer divider (between lane 1 and 2)
            double rDiv2 = 88;
            gc.strokeOval(inter.getCx() - rDiv2, inter.getCy() - rDiv2, rDiv2 * 2, rDiv2 * 2);
            gc.setLineDashes(null);
            
            // Monument / Fountain core
            gc.setFill(Color.rgb(240, 240, 240));
            gc.fillOval(inter.getCx() - r*0.1, inter.getCy() - r*0.1, r*0.2, r*0.2);
            
            // Note: Dashed guide lines and extra markings are intentionally removed 
        } else {
            // Note: Pedestrian crosswalks have been intentionally removed by user request
            /*
            for (Direction dir : inter.getArms()) {
                drawCrosswalk(gc, inter, dir);
            }
            */
            
            // Center directional chevrons
            gc.setStroke(Color.rgb(255, 215, 0, 0.85));
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

        // Intersection text
        gc.setFill(Color.rgb(255, 255, 255, 0.6));
        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(inter.getTypeName(), inter.getCx(), inter.getCy() + 4);
    }

    private void drawCrosswalk(GraphicsContext gc, Intersection inter, Direction dir) {
        double r = inter.getRadius();
        // Position at the edge of the intersection
        double armX = inter.getCx() + dir.dx * (r - 10); 
        double armY = inter.getCy() + dir.dy * (r - 10);

        gc.save();
        gc.translate(armX, armY);
        gc.rotate(dir.angleDeg); 

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(3.5);
        
        double roadWidth = SimConfig.LANE_WIDTH * 6; 
        double stripeLen = 14;
        double stripeSpace = 6.5;
        
        for (double y = -roadWidth/2 + 4; y <= roadWidth/2 - 4; y += stripeSpace) {
            gc.strokeLine(-stripeLen/2, y, stripeLen/2, y);
        }
        gc.restore();
    }

    private void drawTwoWheeler(GraphicsContext gc, Vehicle v, double vh, double vw) {
        gc.setFill(Color.rgb(30, 30, 30));
        gc.fillOval(-vh/2, -1.5, 5, 3); // Rear wheel
        gc.fillOval(vh/2 - 5, -1.5, 5, 3); // Front wheel
        
        gc.setFill(Color.web(v.getColor()));
        gc.fillRoundRect(-vh/2 + 2, -vw/2 + 1, vh - 6, vw - 2, 3, 3);
        
        gc.setStroke(Color.DARKGRAY);
        gc.setLineWidth(1.5);
        gc.strokeLine(vh/2 - 7, -vw/2 - 0.5, vh/2 - 7, vw/2 + 0.5);
        
        gc.setFill(v instanceof com.trafficsim.model.vehicle.Motorbike ? Color.rgb(200, 50, 50) : Color.rgb(50, 150, 200)); 
        gc.fillOval(-3, -vw/2 + 1, 7, vw - 2); 
    }

    private void drawCar(GraphicsContext gc, Vehicle v, double vh, double vw) {
        gc.setFill(Color.web(v.getColor()));
        gc.fillRoundRect(-vh/2, -vw/2, vh, vw, 4, 4);
        
        gc.setFill(Color.rgb(30, 30, 35));
        gc.fillRect(-vh/2 + vh*0.65, -vw/2 + 2, vh*0.18, vw - 4); 
        gc.fillRect(-vh/2 + vh*0.15, -vw/2 + 2, vh*0.12, vw - 4); 
        
        gc.setFill(Color.web(v.getColor()).brighter());
        gc.fillRect(-vh/2 + vh*0.3, -vw/2 + 2.5, vh*0.3, vw - 5);
        
        gc.setFill(Color.LIGHTYELLOW);
        gc.fillOval(vh/2 - 2, -vw/2 + 1, 3, 3);
        gc.fillOval(vh/2 - 2, vw/2 - 4, 3, 3);
        
        gc.setFill(Color.RED);
        gc.fillOval(-vh/2, -vw/2 + 1, 2, 3);
        gc.fillOval(-vh/2, vw/2 - 4, 2, 3);
    }

    private void drawAmbulance(GraphicsContext gc, Vehicle v, double vh, double vw) {
        gc.setFill(Color.WHITE);
        gc.fillRoundRect(-vh/2, -vw/2, vh, vw, 3, 3);
        
        gc.setFill(Color.RED);
        double cw = 6, ch = 2;
        gc.fillRect(-cw/2, -ch/2, cw, ch);
        gc.fillRect(-ch/2, -cw/2, ch, cw);
        
        gc.setFill(Color.rgb(30, 30, 35));
        gc.fillRect(vh/2 - 6, -vw/2 + 1.5, 4, vw - 3);
        
        long time = System.currentTimeMillis();
        boolean flash = (time % 400) < 200;
        gc.setFill(flash ? Color.RED : Color.rgb(100,0,0));
        gc.fillOval(vh/2 - 4, -vw/2 + 1, 4, 3);
        gc.setFill(!flash ? Color.BLUE : Color.rgb(0,0,100));
        gc.fillOval(vh/2 - 4, vw/2 - 4, 4, 3);
    }

    private void drawFireTruck(GraphicsContext gc, Vehicle v, double vh, double vw) {
        gc.setFill(Color.rgb(200, 30, 30));
        gc.fillRoundRect(-vh/2, -vw/2, vh, vw, 2, 2);
        
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(-vh/2 + 4, -vw/2 + 3, vh - 12, vw - 6);
        gc.setStroke(Color.DARKGRAY);
        gc.setLineWidth(1);
        for(double i = -vh/2 + 6; i < vh/2 - 10; i+= 4) {
            gc.strokeLine(i, -vw/2 + 3, i, vw/2 - 3);
        }
        
        gc.setFill(Color.rgb(30, 30, 35));
        gc.fillRect(vh/2 - 7, -vw/2 + 1.5, 5, vw - 3);
        
        long time = System.currentTimeMillis();
        boolean flash = (time % 300) < 150;
        gc.setFill(flash ? Color.RED : Color.rgb(100,0,0));
        gc.fillRect(vh/2 - 5, -vw/2 + 1, 3, 3);
        gc.setFill(!flash ? Color.RED : Color.rgb(100,0,0));
        gc.fillRect(vh/2 - 5, vw/2 - 4, 3, 3);
    }

    private void drawBus(GraphicsContext gc, Vehicle v, double vh, double vw) {
        gc.setFill(Color.web(v.getColor()));
        gc.fillRoundRect(-vh/2, -vw/2, vh, vw, 2, 2);
        
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(-vh/2 + 6, -vw/2 + 4, 8, vw - 8);
        gc.fillRect(-vh/2 + 18, -vw/2 + 4, 8, vw - 8);
        
        gc.setFill(Color.rgb(30, 30, 35));
        gc.fillRect(vh/2 - 5, -vw/2 + 1.5, 4, vw - 3);
        
        gc.setFill(Color.rgb(40, 40, 50));
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
        gc.save();
        gc.translate(v.getRenderX(), v.getRenderY());
        gc.rotate(v.getHeadingAngleDeg());
        
        // Shadow for 3D effect
        gc.setFill(Color.rgb(0, 0, 0, 0.45));
        gc.fillRoundRect(-vh/2 + 2, -vw/2 + 3, vh, vw, 4, 4);
        
        if (v instanceof com.trafficsim.model.vehicle.Motorbike || v instanceof com.trafficsim.model.vehicle.Bicycle) {
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
    }

    @Override public String getModeName() { return "Đồ họa 2.0 (Đẹp & Chi tiết)"; }
}
