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
 * ScoreboardHud — full-document writer + safe attach/refresh helpers tailored to your Scoreboard.ui.
 *
 * Notes:
 * - build(...) appends "Hud/Scoreboard.ui" and sets initial values for element IDs in your UI:
 *     ServerName, Gold, Rank, Playtime, Coords, Footer
 * - openFor(player) attaches the HUD and sends an initial full write (clear=true).
 * - refreshFor(player) schedules a refresh on the player's world thread.
 * - refreshNow(player) must be called on the world thread; it re-attaches only when necessary
 *   and performs an incremental update (clear=false) so the client receives updated fields promptly.
 * - hideFor(player) hides and detaches the HUD safely on the world thread.
 */
public final class ScoreboardHud extends CustomUIHud {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

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
        // Append the UI asset we ship in resources/Common/UI/Custom/Hud/Scoreboard.ui
        commandBuilder.append("Hud/Scoreboard.ui");

        // Set initial values using element IDs from Scoreboard.ui
        commandBuilder.set("#ServerName.Text", serverName);
        commandBuilder.set("#Gold.Text", gold);
        commandBuilder.set("#Rank.Text", rank);
        commandBuilder.set("#Playtime.Text", playtime);
        commandBuilder.set("#Coords.Text", coords);
        commandBuilder.set("#Footer.Text", footer);

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

    private void writeHud(@Nonnull UICommandBuilder builder) {
        // Append is safe even if already appended client-side
        builder.append("Hud/Scoreboard.ui");

        // Populate fields — these selectors match your Scoreboard.ui
        builder.set("#ServerName.Text", serverName);
        builder.set("#Gold.Text", gold);
        builder.set("#Rank.Text", rank);
        builder.set("#Playtime.Text", playtime);
        builder.set("#Coords.Text", coords);
        builder.set("#Footer.Text", footer);

        // Ensure visibility
        builder.set("#ScoreboardRoot.Visible", true);
    }

    /**
     * Attach HUD and send initial, full document write (clear=true).
     * Safe to call from any thread — schedules on world thread.
     */
    public void openFor(@Nonnull Player player) {
        if (player == null) return;
        World world = player.getWorld();
        if (world == null) return;

        world.execute(() -> {
            try {
                try {
                    player.getHudManager().setCustomHud(player.getPlayerRef(), this);
                } catch (Throwable t) {
                    LOG.atWarning().withCause(t).log("[SCOREBOARD] setCustomHud failed on openFor, continuing");
                }

                UICommandBuilder b = new UICommandBuilder();
                writeHud(b);

                try {
                    // Full initial write to ensure a clean client state
                    update(true, b);
                } catch (CancellationException ce) {
                    LOG.atWarning().withCause(ce).log("[SCOREBOARD] initial HUD update canceled for %s", player.getDisplayName());
                } catch (Throwable t) {
                    LOG.atWarning().withCause(t).log("[SCOREBOARD] initial HUD update failed for %s", player.getDisplayName());
                }
            } catch (Throwable t) {
                LOG.atWarning().withCause(t).log("[SCOREBOARD] unexpected error in openFor()");
            }
        });
    }

    /**
     * Schedules a refresh on the player's world thread (safe to call from any thread).
     */
    public void refreshFor(@Nonnull Player player) {
        if (player == null) return;
        World world = player.getWorld();
        if (world == null) return;
        world.execute(() -> refreshNow(player));
    }

    /**
     * Must be called on the world thread.
     * Re-attaches only if HudManager doesn't already have this HUD instance and performs an incremental update.
     * Using incremental update (clear=false) for periodic updates avoids forcing a full client rebuild every tick.
     */
    public void refreshNow(@Nonnull Player player) {
        if (player == null) return;

        try {
            try {
                var hm = player.getHudManager();
                if (hm == null) {
                    LOG.atWarning().log("[SCOREBOARD] No HudManager available for %s", player.getDisplayName());
                } else {
                    if (hm.getCustomHud() != this) {
                        // Only reattach if the HudManager doesn't already reference this instance
                        try {
                            hm.setCustomHud(player.getPlayerRef(), this);
                        } catch (Throwable t) {
                            LOG.atWarning().withCause(t).log("[SCOREBOARD] setCustomHud failed in refreshNow for %s", player.getDisplayName());
                        }
                    }
                }
            } catch (Throwable t) {
                LOG.atWarning().withCause(t).log("[SCOREBOARD] HudManager check failed for %s", player.getDisplayName());
            }

            // Debug: log what we're about to send (helps confirm server-side values)
            LOG.atInfo().log("[SCOREBOARD] refreshNow sending to %s: coords=%s playtime=%s gold=%s rank=%s",
                    player.getDisplayName(), coords, playtime, gold, rank);

            UICommandBuilder b = new UICommandBuilder();
            writeHud(b);

            try {
                // incremental update; the working plugin used full writes often, but incremental is typically better for steady updates.
                update(false, b);
            } catch (CancellationException ce) {
                LOG.atWarning().withCause(ce).log("[SCOREBOARD] HUD refresh canceled for %s", player.getDisplayName());
            } catch (Throwable t) {
                LOG.atWarning().withCause(t).log("[SCOREBOARD] HUD refresh failed for %s", player.getDisplayName());
            }
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[SCOREBOARD] unexpected error in refreshNow()");
        }
    }

    /**
     * Hide and detach HUD on world thread.
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
                    LOG.atWarning().withCause(t).log("[SCOREBOARD] hide update failed for %s", player.getDisplayName());
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