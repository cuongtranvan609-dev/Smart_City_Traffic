package com.trafficsim.service;

import com.trafficsim.config.SimConfig;
import com.trafficsim.model.vehicle.Vehicle;
import javafx.scene.media.AudioClip;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Dịch vụ âm thanh – load và phát các clip.
 * Nếu file âm thanh không tồn tại thì bỏ qua (graceful degradation).
 */
public class SoundService {

    private final Map<String, AudioClip> clips = new HashMap<>();
    private boolean enabled = true;

    // Cooldown để tránh phát liên tục
    private final Map<String, Double> cooldown = new HashMap<>();
    private static final double HORN_COOLDOWN   = 3.0;
    private static final double SIGNAL_COOLDOWN = 0.6;

    public SoundService() {
        loadClip("horn",     "/sounds/horn.wav");
        loadClip("siren",    "/sounds/siren.wav");
        loadClip("signal",   "/sounds/signal.wav");
        loadClip("engine",   "/sounds/engine.wav");
    }

    private void loadClip(String key, String path) {
        try {
            URL url = getClass().getResource(path);
            if (url != null) {
                AudioClip clip = new AudioClip(url.toExternalForm());
                clip.setVolume(SimConfig.MASTER_VOLUME);
                clips.put(key, clip);
            }
        } catch (Exception e) {
            // Âm thanh không bắt buộc – chạy tiếp không có âm thanh
            System.out.println("[Sound] Không tải được: " + path);
        }
    }

    public void playHorn(Vehicle v) {
        String key = v.isPriorityVehicle() ? "siren" : "horn";
        playCooldown(key + "_" + v.getId(), key,
                v.isPriorityVehicle() ? SimConfig.SIREN_VOLUME : SimConfig.HORN_VOLUME,
                HORN_COOLDOWN);
    }

    public void playSignal(Vehicle v) {
        playCooldown("signal_" + v.getId(), "signal",
                SimConfig.SIGNAL_VOLUME, SIGNAL_COOLDOWN);
    }

    private void playCooldown(String cooldownKey, String clipKey,
                              double volume, double cooldownSec) {
        if (!enabled) return;
        double now = System.currentTimeMillis() / 1000.0;
        if (cooldown.getOrDefault(cooldownKey, 0.0) > now) return;
        cooldown.put(cooldownKey, now + cooldownSec);

        AudioClip clip = clips.get(clipKey);
        if (clip != null && !clip.isPlaying()) {
            clip.setVolume(volume);
            clip.play();
        }
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled()              { return enabled; }
}
