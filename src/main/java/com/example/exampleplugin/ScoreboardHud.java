package com.example.exampleplugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
//import com.hypixel.hytale.server.core.hud.HudManager; // may be optional depending on API; used indirectly via Player
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.concurrent.CancellationException;

/**
 * ScoreboardHud — uses a single full-document write pattern (like BetterScoreBoardHud).
 *
 * - append("Hud/Scoreboard.ui") and write all selectors in one builder writeHud(...)
 * - expose openFor(player) and refreshFor(player) and hideFor(player) convenience methods that
 *   always run on the player's world thread (they schedule world.execute(...) for safety).
 *
 * This mirrors the success pattern from the working plugin: update the HUD only via world-thread tasks,
 * send a single full write per refresh, and attach/hide HUD from one place.
 */
public final class ScoreboardHud extends CustomUIHud {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    // cached text values (updated by setters; used to build full writes)
    private volatile String serverName = "Darkvale";
    private volatile String gold = "Gold: 0";
    private volatile String rank = "Rank: Member";
    private volatile String playtime = "Playtime: 0m";
    private volatile String coords = "Coords: 0, 0, 0";
    private volatile String footer = "www.darkvale.com";

    public ScoreboardHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    /**
     * Called when the HUD is first appended (the framework will call build() during show()/setCustomHud).
     * We append the UI document and provide initial values so the client receives a consistent document.
     */
    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        // Append the UI document packaged under Common/UI/Custom/Hud/Scoreboard.ui -> document path "Hud/Scoreboard.ui"
        commandBuilder.append("Hud/Scoreboard.ui");

        // Provide initial document values (matching element IDs in your Scoreboard.ui)
        commandBuilder.set("#ScoreboardRoot #ServerName.Text", serverName);
        commandBuilder.set("#ScoreboardRoot #Gold.Text", gold);
        commandBuilder.set("#ScoreboardRoot #Rank.Text", rank);
        commandBuilder.set("#ScoreboardRoot #Playtime.Text", playtime);
        commandBuilder.set("#ScoreboardRoot #Coords.Text", coords);
        commandBuilder.set("#ScoreboardRoot #Footer.Text", footer);
    }

    // --- Simple setters update local cache only (use refreshFor to push) ---
    public void setServerName(@Nonnull String s) { this.serverName = s; }
    public void setGold(@Nonnull String s) { this.gold = s; }
    public void setRank(@Nonnull String s) { this.rank = s; }
    public void setPlaytime(@Nonnull String s) { this.playtime = s; }
    public void setCoords(@Nonnull String s) { this.coords = s; }
    public void setFooter(@Nonnull String s) { this.footer = s; }

    /**
     * Build the full HUD document (one-shot) into the provided builder.
     * This mirrors BetterScoreBoardHud.writeHud(...) style: we write all fields/visibility in one command set.
     */
    private void writeHud(@Nonnull UICommandBuilder builder) {
        // append isn't required here if the client already has the document (append happens on show),
        // but sending append here ensures client will receive the document when we open the HUD.
        builder.append("Hud/Scoreboard.ui");

        // Populate the document with current values (selectors match Scoreboard.ui)
        builder.set("#ScoreboardRoot #ServerName.Text", serverName);
        builder.set("#ScoreboardRoot #Gold.Text", gold);
        builder.set("#ScoreboardRoot #Rank.Text", rank);
        builder.set("#ScoreboardRoot #Playtime.Text", playtime);
        builder.set("#ScoreboardRoot #Coords.Text", coords);
        builder.set("#ScoreboardRoot #Footer.Text", footer);

        // Ensure root visible in case previously hidden
        builder.set("#ScoreboardRoot.Visible", true);
    }

    /**
     * Open the HUD for the given player (attach and send initial document).
     * Always schedules the work on the player's world thread for safety.
     */
    public void openFor(@Nonnull Player player) {
        if (player == null) return;
        World world = player.getWorld();
        if (world == null) return;

        world.execute(() -> {
            try {
                // attach this hud to the player (will cause the framework to call build())
                try {
                    player.getHudManager().setCustomHud(player.getPlayerRef(), this);
                } catch (Throwable t) {
                    // older API variations / safety fallback
                    LOG.atWarning().withCause(t).log("[SCOREBOARD] Failed to setCustomHud, continuing to send update");
                }

                // send full initial document (append + values)
                UICommandBuilder b = new UICommandBuilder();
                writeHud(b);
                try {
                    // use a full clear=true for initial write to ensure a consistent state client-side
                    update(true, b);
                } catch (CancellationException ce) {
                    LOG.atWarning().withCause(ce).log("[SCOREBOARD] Initial HUD update canceled for player=%s", player.getDisplayName());
                } catch (Throwable t) {
                    LOG.atWarning().withCause(t).log("[SCOREBOARD] Failed to send initial HUD update for player=%s", player.getDisplayName());
                }
            } catch (Throwable t) {
                LOG.atWarning().withCause(t).log("[SCOREBOARD] Unexpected error in openFor()");
            }
        });
    }

    /**
     * Refresh the HUD for the given player — sends a one-shot update with the cached fields.
     * Schedules on the world thread.
     */
    public void refreshFor(@Nonnull Player player) {
        if (player == null) return;
        World world = player.getWorld();
        if (world == null) return;

        world.execute(() -> {
            try {
                UICommandBuilder b = new UICommandBuilder();
                writeHud(b);
                try {
                    // incremental update (clear=false) so we don't wipe entire UI assets; we still set all fields
                    update(false, b);
                } catch (CancellationException ce) {
                    LOG.atWarning().withCause(ce).log("[SCOREBOARD] HUD refresh canceled for player=%s", player.getDisplayName());
                } catch (Throwable t) {
                    LOG.atWarning().withCause(t).log("[SCOREBOARD] HUD refresh failed for player=%s", player.getDisplayName());
                }
            } catch (Throwable t) {
                LOG.atWarning().withCause(t).log("[SCOREBOARD] Unexpected error in refreshFor()");
            }
        });
    }

    /**
     * Hide the HUD for the given player (sets root visible = false and detaches).
     */
    public void hideFor(@Nonnull Player player) {
        if (player == null) return;
        World world = player.getWorld();
        if (world == null) return;

        world.execute(() -> {
            try {
                UICommandBuilder b = new UICommandBuilder();
                b.append("Hud/Scoreboard.ui");
                b.set("#ScoreboardRoot.Visible", false);
                try {
                    update(false, b);
                } catch (Throwable t) {
                    LOG.atWarning().withCause(t).log("[SCOREBOARD] Failed to hide HUD for player=%s", player.getDisplayName());
                }

                try {
                    player.getHudManager().setCustomHud(player.getPlayerRef(), null);
                } catch (Throwable t) {
                    // ignore
                }
            } catch (Throwable t) {
                LOG.atWarning().withCause(t).log("[SCOREBOARD] Unexpected error in hideFor()");
            }
        });
    }
}