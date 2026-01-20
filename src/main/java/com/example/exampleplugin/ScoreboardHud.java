package com.example.exampleplugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.concurrent.CancellationException;

/**
 * ScoreboardHud — writes the full UI document each refresh and exposes refreshNow(player)
 * for immediate, world-thread updates (avoids nested scheduling).
 */
public final class ScoreboardHud extends CustomUIHud {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    // cached text values
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
        // append UI document; ensure resource placed at Common/UI/Custom/Hud/Scoreboard.ui -> document "Hud/Scoreboard.ui"
        commandBuilder.append("Hud/Scoreboard.ui");

        // set initial values using simple selectors that match Scoreboard.ui element IDs
        commandBuilder.set("#ServerName.Text", serverName);
        commandBuilder.set("#Gold.Text", gold);
        commandBuilder.set("#Rank.Text", rank);
        commandBuilder.set("#Playtime.Text", playtime);
        commandBuilder.set("#Coords.Text", coords);
        commandBuilder.set("#Footer.Text", footer);
    }

    // Setters update the internal cache. Use refreshNow(player) to push immediately.
    public void setServerName(@Nonnull String s) { this.serverName = s; }
    public void setGold(@Nonnull String s) { this.gold = s; }
    public void setRank(@Nonnull String s) { this.rank = s; }
    public void setPlaytime(@Nonnull String s) { this.playtime = s; }
    public void setCoords(@Nonnull String s) { this.coords = s; }
    public void setFooter(@Nonnull String s) { this.footer = s; }

    /**
     * Build the full HUD write into the builder.
     */
    private void writeHud(@Nonnull UICommandBuilder builder) {
        // append ensures the document exists client-side; harmless if already appended
        builder.append("Hud/Scoreboard.ui");

        // use simple selectors matching your Scoreboard.ui IDs
        builder.set("#ServerName.Text", serverName);
        builder.set("#Gold.Text", gold);
        builder.set("#Rank.Text", rank);
        builder.set("#Playtime.Text", playtime);
        builder.set("#Coords.Text", coords);
        builder.set("#Footer.Text", footer);

        // ensure root visible
        builder.set("#ScoreboardRoot.Visible", true);
    }

    /**
     * Open HUD for player (schedules on world thread).
     */
    public void openFor(@Nonnull Player player) {
        if (player == null) return;
        World w = player.getWorld();
        if (w == null) return;

        w.execute(() -> {
            try {
                try {
                    player.getHudManager().setCustomHud(player.getPlayerRef(), this);
                } catch (Throwable t) {
                    LOG.atWarning().withCause(t).log("[SCOREBOARD] setCustomHud failed, continuing to send update");
                }

                UICommandBuilder b = new UICommandBuilder();
                writeHud(b);
                try {
                    // initial full clear to ensure clean state on client
                    update(true, b);
                } catch (CancellationException ce) {
                    LOG.atWarning().withCause(ce).log("[SCOREBOARD] initial HUD update canceled for player=%s", player.getDisplayName());
                } catch (Throwable t) {
                    LOG.atWarning().withCause(t).log("[SCOREBOARD] initial HUD update failed for player=%s", player.getDisplayName());
                }
            } catch (Throwable t) {
                LOG.atWarning().withCause(t).log("[SCOREBOARD] unexpected error in openFor()");
            }
        });
    }

    /**
     * Refresh the HUD for the player by scheduling a world-thread write (safe from any thread).
     */
    public void refreshFor(@Nonnull Player player) {
        if (player == null) return;
        World w = player.getWorld();
        if (w == null) return;

        w.execute(() -> refreshNow(player));
    }

    /**
     * Refresh immediately on the current thread (must be called on world thread).
     * This performs a single full-document update and avoids nested scheduling.
     */
    public void refreshNow(@Nonnull Player player) {
        if (player == null) return;
        try {
            UICommandBuilder b = new UICommandBuilder();
            writeHud(b);
            try {
                update(false, b);
            } catch (CancellationException ce) {
                LOG.atWarning().withCause(ce).log("[SCOREBOARD] HUD refresh canceled for player=%s", player.getDisplayName());
            } catch (Throwable t) {
                LOG.atWarning().withCause(t).log("[SCOREBOARD] HUD refresh failed for player=%s", player.getDisplayName());
            }
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[SCOREBOARD] unexpected error in refreshNow()");
        }
    }

    /**
     * Hide and detach HUD for the player (schedules on world thread).
     */
    public void hideFor(@Nonnull Player player) {
        if (player == null) return;
        World w = player.getWorld();
        if (w == null) return;

        w.execute(() -> {
            try {
                UICommandBuilder b = new UICommandBuilder();
                b.append("Hud/Scoreboard.ui");
                b.set("#ScoreboardRoot.Visible", false);
                try {
                    update(false, b);
                } catch (Throwable t) {
                    LOG.atWarning().withCause(t).log("[SCOREBOARD] hide update failed for player=%s", player.getDisplayName());
                }

                try {
                    player.getHudManager().setCustomHud(player.getPlayerRef(), null);
                } catch (Throwable ignored) {}
            } catch (Throwable t) {
                LOG.atWarning().withCause(t).log("[SCOREBOARD] unexpected error in hideFor()");
            }
        });
    }
}