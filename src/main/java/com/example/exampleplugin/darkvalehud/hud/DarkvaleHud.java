package com.example.exampleplugin.darkvalehud.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.example.exampleplugin.darkvalehud.data.DebugManager;
import com.example.exampleplugin.darkvalehud.data.PlayerDebugSettings;

import javax.annotation.Nonnull;

/**
 * DarkvaleHud — adapted to show scoreboard content using the existing DarkvaleHud.ui label IDs.
 *
 * Keeps the original setPosition API (posX/posY/posZ) so the tick system can continue to call it.
 * build() writes to the original DarkvaleHud UI labels (TitleLabel, PosXLabel, PosYLabel, PosZLabel,
 * ChunkLabel, BiomeLabel, HealthLabel, StaminaLabel, ManaLabel).
 */
public class DarkvaleHud extends CustomUIHud {
    private final DebugManager debugManager;
    private final Ref<EntityStore> playerEntityRef;

    // Position fields (original DarkvaleHud pattern)
    private int posX = 0;
    private int posY = 0;
    private int posZ = 0;
    private int chunkX = 0;
    private int chunkZ = 0;

    // Biome (original)
    private String biomeName = "Unknown";

    // Server / scoreboard fields
    private volatile String serverName = "Darkvale";
    private volatile String rankText = "Rank: Member";
    private volatile String playtimeText = "Playtime: 0m";
    private volatile String footer = "www.darkvale.com";

    public DarkvaleHud(PlayerRef playerRef, DebugManager debugManager, Ref<EntityStore> playerEntityRef) {
        super(playerRef);
        this.debugManager = debugManager;
        this.playerEntityRef = playerEntityRef;
    }

    // Preserve original API: update integer position fields
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

    // Scoreboard setters (mapped into existing labels)
    public void setServerName(@Nonnull String s) { this.serverName = (s == null) ? "" : s; }
    public void setRank(@Nonnull String r) { this.rankText = (r == null) ? "" : r; }
    public void setPlaytime(@Nonnull String p) { this.playtimeText = (p == null) ? "" : p; }
    public void setFooter(@Nonnull String f) { this.footer = (f == null) ? "" : f; }

    public boolean isDebugEnabled() {
        PlayerDebugSettings settings = this.debugManager.getSettings(this.playerEntityRef);
        return settings.isDebugEnabled();
    }

    @Override
    protected void build(UICommandBuilder builder) {
        PlayerDebugSettings settings = this.debugManager.getSettings(this.playerEntityRef);

        // Append the DarkvaleHud UI asset
        builder.append("Hud/SimpleDebugInfoHud/DarkvaleHud.ui");

        if (!settings.isDebugEnabled()) {
            // When disabled, clear header and do not show contents
            builder.set("#TitleLabel.Text", "");
            return;
        }

        // Map values to the UI labels
        builder.set("#TitleLabel.Text", this.serverName); // header
        builder.set("#PosXLabel.Text", "X: " + this.posX);
        builder.set("#PosYLabel.Text", "Y: " + this.posY);
        builder.set("#PosZLabel.Text", "Z: " + this.posZ);
        builder.set("#ChunkLabel.Text", "Chunk: " + this.chunkX + ", " + this.chunkZ);
        builder.set("#BiomeLabel.Text", "Biome: " + this.biomeName);

        // Map scoreboard fields into the lower labels:
        builder.set("#HealthLabel.Text", this.rankText);      // show Rank here
        builder.set("#StaminaLabel.Text", this.playtimeText); // show Playtime here
        builder.set("#ManaLabel.Text", this.footer);          // use ManaLabel as footer location
    }
}
