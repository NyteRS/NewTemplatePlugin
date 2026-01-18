package com.example.exampleplugin;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;

/**
 * ScoreboardHud - follows the CustomUIHud API:
 * - build() appends an existing .ui file: Pages/Scoreboard.ui
 * - refresh() updates fields using UICommandBuilder.set(...) (incremental updates)
 */
public class ScoreboardHud extends CustomUIHud {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private String title = "Scoreboard";
    private String line1 = "Money: 0";
    private String line2 = "Shards: 0";
    private String line3 = "Kills: 0";
    private String line4 = "Playtime: 0m";

    public ScoreboardHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    /**
     * Append the .ui file that must be placed at:
     *   src/main/resources/Common/UI/Custom/Pages/Scoreboard.ui
     *
     * After appending, set the initial values into the UI using selectors.
     */
    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        // This follows the docs: append("MyUI.ui") resolves to resources/Common/UI/Custom/MyUI.ui
        commandBuilder.append("Pages/Scoreboard.ui");

        // Set initial values - selectors must match IDs in the .ui file
        commandBuilder.set("#ScoreboardRoot #Title.Text", title);
        commandBuilder.set("#ScoreboardRoot #Line1.Text", line1);
        commandBuilder.set("#ScoreboardRoot #Line2.Text", line2);
        commandBuilder.set("#ScoreboardRoot #Line3.Text", line3);
        commandBuilder.set("#ScoreboardRoot #Line4.Text", line4);
    }

    // Simple setters
    public void setTitle(String t) { this.title = t; }
    public void setLine1(String s) { this.line1 = s; }
    public void setLine2(String s) { this.line2 = s; }
    public void setLine3(String s) { this.line3 = s; }
    public void setLine4(String s) { this.line4 = s; }

    /**
     * Incremental update of label texts.
     * Call after modifying fields above.
     */
    public void refresh() {
        UICommandBuilder builder = new UICommandBuilder();
        builder.set("#ScoreboardRoot #Title.Text", title);
        builder.set("#ScoreboardRoot #Line1.Text", line1);
        builder.set("#ScoreboardRoot #Line2.Text", line2);
        builder.set("#ScoreboardRoot #Line3.Text", line3);
        builder.set("#ScoreboardRoot #Line4.Text", line4);

        // update(false, builder) sends incremental changes without clearing entire HUD
        update(false, builder);
    }

    // Optional helper to show everything in logs when needed
    public void debugLogInitial() {
        LOGGER.atInfo().log("ScoreboardHud initial: %s / %s / %s / %s", line1, line2, line3, line4);
    }
}