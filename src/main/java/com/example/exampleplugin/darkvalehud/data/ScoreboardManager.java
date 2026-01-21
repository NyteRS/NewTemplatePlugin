package com.example.exampleplugin.darkvalehud.data;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple per-player toggle state for the scoreboard HUD.
 * Mirrors DebugManager's approach used for the Debug HUD.
 */
public class ScoreboardManager {
    private final Map<Ref<EntityStore>, Boolean> enabled = new ConcurrentHashMap<>();

    public ScoreboardManager() {}

    /**
     * Returns whether the scoreboard is enabled for the given player entity ref.
     * Returns false if ref is null or not present.
     */
    public boolean isEnabled(Ref<EntityStore> ref) {
        if (ref == null) return false;
        return enabled.getOrDefault(ref, Boolean.FALSE);
    }

    /**
     * Set enabled state for a player entity ref.
     */
    public void setEnabled(Ref<EntityStore> ref, boolean on) {
        if (ref == null) return;
        enabled.put(ref, on);
    }

    /**
     * Clear all stored state (useful on plugin unload).
     */
    public void cleanup() {
        enabled.clear();
    }
}
