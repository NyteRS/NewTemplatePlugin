package com.example.exampleplugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * ScoreboardHud — asset-driven HUD that appends Pages/Scoreboard.ui and exposes simple setters.
 *
 * Fields are volatile to make cross-thread visibility safer; HUD writes should still be performed on the
 * player's world thread (the system will call refresh() on the world/entity tick thread).
 */
public final class ScoreboardHud extends CustomUIHud {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Cached values the server maintains and sends to client
    private volatile String serverName = "Darkvale";
    private volatile String gold = "Gold: 0";
    private volatile String rank = "Rank: Member";
    private volatile String playtime = "Playtime: 0m";
    private volatile String coords = "Coords: 0, 0, 0";
    private volatile String footer = "www.darkvale.com";

    public ScoreboardHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        // Append the UI asset (adjust path if you move Scoreboard.ui)
        commandBuilder.append("Pages/Scoreboard.ui");

        // Populate fields — these selectors must match your Scoreboard.ui
        commandBuilder.set("#ScoreboardRoot #ServerName.Text", serverName);
        commandBuilder.set("#ScoreboardRoot #Gold.Text", gold);
        commandBuilder.set("#ScoreboardRoot #Rank.Text", rank);
        commandBuilder.set("#ScoreboardRoot #Playtime.Text", playtime);
        commandBuilder.set("#ScoreboardRoot #Coords.Text", coords);
        commandBuilder.set("#ScoreboardRoot #Footer.Text", footer);

        // Ensure root visible
        commandBuilder.set("#ScoreboardRoot.Visible", true);
    }

    // -------------------------
    // Setters (update server cache)
    // -------------------------
    public void setServerName(@Nonnull String s) { this.serverName = s; }
    public void setGold(@Nonnull String s) { this.gold = s; }
    public void setRank(@Nonnull String s) { this.rank = s; }
    public void setPlaytime(@Nonnull String s) { this.playtime = s; }
    public void setCoords(@Nonnull String s) { this.coords = s; }
    public void setFooter(@Nonnull String s) { this.footer = s; }

    /**
     * Incremental update of the text fields. Call after modifying fields above.
     *
     * Must be called on the player's current world thread.
     */
    public void refresh() {
        UICommandBuilder b = new UICommandBuilder();
        b.set("#ScoreboardRoot #ServerName.Text", serverName);
        b.set("#ScoreboardRoot #Gold.Text", gold);
        b.set("#ScoreboardRoot #Rank.Text", rank);
        b.set("#ScoreboardRoot #Playtime.Text", playtime);
        b.set("#ScoreboardRoot #Coords.Text", coords);
        b.set("#ScoreboardRoot #Footer.Text", footer);

        update(false, b);
    }

    public void debugLog() {
        LOGGER.atInfo().log("ScoreboardHud: server=%s, %s, %s, %s, %s", serverName, gold, rank, playtime, coords);
    }
}