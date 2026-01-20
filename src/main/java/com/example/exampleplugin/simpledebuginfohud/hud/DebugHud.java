package com.example.exampleplugin.simpledebuginfohud.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.davidhenk.simpledebuginfohud.data.DebugManager;
import me.davidhenk.simpledebuginfohud.data.PlayerDebugSettings;

public class DebugHud extends CustomUIHud {
    private final DebugManager debugManager;
    private final Ref<EntityStore> playerEntityRef;
    private int posX = 0;
    private int posY = 0;
    private int posZ = 0;
    private int chunkX = 0;
    private int chunkZ = 0;
    private String biomeName = "Unknown";
    private float health = 100.0F;
    private float maxHealth = 100.0F;
    private float stamina = 100.0F;
    private float maxStamina = 100.0F;
    private float mana = 100.0F;
    private float maxMana = 100.0F;

    public DebugHud(PlayerRef playerRef, DebugManager debugManager, Ref<EntityStore> playerEntityRef) {
        super(playerRef);
        this.debugManager = debugManager;
        this.playerEntityRef = playerEntityRef;
    }

    public void setPosition(int x, int y, int z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.chunkX = x >> 4;
        this.chunkZ = z >> 4;
    }

    public void setBiomeName(String biomeName) {
        this.biomeName = biomeName != null ? biomeName : "Unknown";
    }

    public void setHealth(float health, float maxHealth) {
        this.health = health;
        this.maxHealth = maxHealth;
    }

    public void setStamina(float stamina, float maxStamina) {
        this.stamina = stamina;
        this.maxStamina = maxStamina;
    }

    public void setMana(float mana, float maxMana) {
        this.mana = mana;
        this.maxMana = maxMana;
    }

    public boolean isDebugEnabled() {
        PlayerDebugSettings settings = this.debugManager.getSettings(this.playerEntityRef);
        return settings.isDebugEnabled();
    }

    protected void build(UICommandBuilder builder) {
        PlayerDebugSettings settings = this.debugManager.getSettings(this.playerEntityRef);
        if (settings.isDebugEnabled()) {
            builder.append("Hud/SimpleDebugInfoHud/DebugHud.ui");
            builder.set("#PosXLabel.Text", "X: " + this.posX);
            builder.set("#PosYLabel.Text", "Y: " + this.posY);
            builder.set("#PosZLabel.Text", "Z: " + this.posZ);
            builder.set("#ChunkLabel.Text", "Chunk: " + this.chunkX + ", " + this.chunkZ);
            builder.set("#BiomeLabel.Text", "Biome: " + this.biomeName);
            builder.set("#HealthLabel.Text", String.format("Health: %.0f/%.0f", this.health, this.maxHealth));
            builder.set("#StaminaLabel.Text", String.format("Stamina: %.0f/%.0f", this.stamina, this.maxStamina));
            builder.set("#ManaLabel.Text", String.format("Mana: %.0f/%.0f", this.mana, this.maxMana));
        }
    }
}