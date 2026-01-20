package com.example.exampleplugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * ScoreboardHud — asset-driven HUD that appends Pages/Scoreboard.ui and exposes simple setters.
 *
 * Use setServerName / setGold / setRank / setPlaytime / setCoords / setFooter then call refresh() to update.
 */
public class ScoreboardHud extends CustomUIHud {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private String serverName = "DarkVale";
    private String gold = "Gold: 0";
    private String rank = "Rank: Member";
    private String playtime = "Playtime: 0m";
    private String coords = "Coords: 0, 0, 0";
    private String footer = "www.darkvale.play";

    public ScoreboardHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        // Append the UI asset (must be at resources/Common/UI/Custom/Pages/Scoreboard.ui)
        commandBuilder.append("Pages/Scoreboard.ui");

        // Set initial values (selectors must match the IDs declared in the .ui)
        commandBuilder.set("#ScoreboardRoot #ServerName.Text", serverName);
        commandBuilder.set("#ScoreboardRoot #Gold.Text", gold);
        commandBuilder.set("#ScoreboardRoot #Rank.Text", rank);
        commandBuilder.set("#ScoreboardRoot #Playtime.Text", playtime);
        commandBuilder.set("#ScoreboardRoot #Coords.Text", coords);
        commandBuilder.set("#ScoreboardRoot #Footer.Text", footer);
    }

    // API setters
    public void setServerName(String s) { this.serverName = s; }
    public void setGold(String s) { this.gold = s; }
    public void setRank(String s) { this.rank = s; }
    public void setPlaytime(String s) { this.playtime = s; }
    public void setCoords(String s) { this.coords = s; }
    public void setFooter(String s) { this.footer = s; }

    /**
     * Incremental update of the text fields. Call after modifying fields above.
     */
    public void refresh() {
        UICommandBuilder b = new UICommandBuilder();
        b.set("#ScoreboardRoot #ServerName.Text", serverName);
        b.set("#ScoreboardRoot #Gold.Text", gold);
        b.set("#ScoreboardRoot #Rank.Text", rank);
        b.set("#ScoreboardRoot #Playtime.Text", playtime);
        b.set("#ScoreboardRoot #Coords.Text", coords);
        b.set("#ScoreboardRoot #Footer.Text", footer);

        // incremental update
        update(false, b);
    }

    public void debugLog() {
        LOGGER.atInfo().log("ScoreboardHud: server=%s, %s, %s, %s, %s", serverName, gold, rank, playtime, coords);
    }
}