package com.example.exampleplugin;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * Small per-player scoreboard HUD.
 * Keeps a few lines of text and supports a simple refresh() which replaces the panel.
 *
 * Note: This implementation re-builds the whole scoreboard panel on refresh using appendInline/clear.
 * You can optimize by using set() if you want delta updates.
 */
public class ScoreboardHud extends CustomUIHud {
    private String title = "ExampleSMP";
    private String line1 = "Money 0";
    private String line2 = "Shards 0";
    private String line3 = "Kills 0";
    private String line4 = "Playtime 0m";

    public ScoreboardHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    /**
     * Called by show() or update() to build the HUD commands.
     * We append a small panel with four labels. IDs are included so you can target them if you later want
     * to update individual labels instead of re-appending the whole panel.
     */
    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        // Clear any existing scoreboard node and append our simple layout inline.
        // UI document syntax examples in the unpacked repo used things like: Label { Text: "..." ; }
        commandBuilder.clear("#ScoreboardRoot");
        StringBuilder doc = new StringBuilder();
        doc.append("Panel { Id: ScoreboardRoot; Position: (Alignment: TopRight); Padding: (Right: 6); }")
                .append("Label { Id: Title; Text: \"").append(escape(title)).append("\"; Style: (FontSize: 14; Bold: true); }")
                .append("Label { Id: Line1; Text: \"").append(escape(line1)).append("\"; }")
                .append("Label { Id: Line2; Text: \"").append(escape(line2)).append("\"; }")
                .append("Label { Id: Line3; Text: \"").append(escape(line3)).append("\"; }")
                .append("Label { Id: Line4; Text: \"").append(escape(line4)).append("\"; }");

        commandBuilder.appendInline("#HudRoot", doc.toString());
    }

    // Simple helpers to set lines and refresh
    public void setTitle(String t) { this.title = t; }
    public void setLine1(String s) { this.line1 = s; }
    public void setLine2(String s) { this.line2 = s; }
    public void setLine3(String s) { this.line3 = s; }
    public void setLine4(String s) { this.line4 = s; }

    /**
     * Rebuilds the HUD for the player. Call when values change.
     */
    public void refresh() {
        UICommandBuilder builder = new UICommandBuilder();
        // clear then append again (same as build would do via show/update)
        builder.clear("#ScoreboardRoot");
        StringBuilder doc = new StringBuilder();
        doc.append("Panel { Id: ScoreboardRoot; Position: (Alignment: TopRight); Padding: (Right: 6); }")
                .append("Label { Id: Title; Text: \"").append(escape(title)).append("\"; Style: (FontSize: 14; Bold: true); }")
                .append("Label { Id: Line1; Text: \"").append(escape(line1)).append("\"; }")
                .append("Label { Id: Line2; Text: \"").append(escape(line2)).append("\"; }")
                .append("Label { Id: Line3; Text: \"").append(escape(line3)).append("\"; }")
                .append("Label { Id: Line4; Text: \"").append(escape(line4)).append("\"; }");
        builder.appendInline("#HudRoot", doc.toString());

        // update(false, builder) will send incremental UI commands for this HUD instance
        // The API in CustomUIHud includes update(boolean, UICommandBuilder)
        // first param indicates clear (we already cleared specific selector above), pass false to not clear everything
        update(false, builder);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"");
    }
}