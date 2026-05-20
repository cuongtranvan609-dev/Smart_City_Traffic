package com.trafficsim.view.renderer;

import com.trafficsim.model.SimScene;
import javafx.scene.canvas.GraphicsContext;

public interface SceneRenderer {
    /**
     * @param gc    canvas context
     * @param scene model
     * @param zoom  zoom factor (1.0 = 100%)
     * @param panX  horizontal pan offset (pixels)
     * @param panY  vertical pan offset (pixels)
     */
    void render(GraphicsContext gc, SimScene scene, double zoom, double panX, double panY);
    String getModeName();
}
