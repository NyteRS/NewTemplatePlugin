package com.example.exampleplugin.darkvalehud.data;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DebugManager {
    private final Map<Ref<EntityStore>, PlayerDebugSettings> playerSettings = new ConcurrentHashMap();

    public DebugManager() {
    }

    public PlayerDebugSettings getSettings(Ref<EntityStore> ref) {
        return ref == null ? new PlayerDebugSettings() : (PlayerDebugSettings)this.playerSettings.computeIfAbsent(ref, (k) -> new PlayerDebugSettings());
    }

    public boolean isDebugEnabled(Ref<EntityStore> ref) {
        return this.getSettings(ref).isDebugEnabled();
    }

    public void setDebugEnabled(Ref<EntityStore> ref, boolean debugEnabled) {
        this.getSettings(ref).setDebugEnabled(debugEnabled);
    }

    public void cleanup() {
        this.playerSettings.clear();
    }
}
