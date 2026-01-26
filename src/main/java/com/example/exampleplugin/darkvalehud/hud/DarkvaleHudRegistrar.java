package com.example.exampleplugin.darkvalehud.hud;

import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Registrar that uses MultipleHUD directly at compile-time.
 * Requires MHUD on the classpath (compile-time and runtime).
 *
 * Calls:
 *  MultipleHUD.getInstance().setCustomHud(player, playerRef, hudId, hud);
 *  MultipleHUD.getInstance().hideCustomHud(player, playerRef, hudId);
 */
public final class DarkvaleHudRegistrar {
    public static final String HUD_ID = "DarkvaleHud";

    private DarkvaleHudRegistrar() {}

    /**
     * Register/show the given HUD for the player via MultipleHUD.
     * If MultipleHUD is missing or throws, falls back to player's HudManager (best-effort).
     */
    public static void showHud(Player player, PlayerRef playerRef, CustomUIHud hud) {
        try {
            MultipleHUD.getInstance().setCustomHud(player, playerRef, HUD_ID, hud);
        } catch (Throwable t) {
            // Fallback: best-effort to player's hud manager
            t.printStackTrace();
            try {
                var hm = player.getHudManager();
                if (hm != null) hm.setCustomHud(playerRef, hud);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Hide/unregister the given HUD for the player via MultipleHUD.
     * If MultipleHUD is missing or throws, falls back to player's HudManager (best-effort).
     */
    public static void hideHud(Player player, PlayerRef playerRef) {
        try {
            MultipleHUD.getInstance().hideCustomHud(player, playerRef, HUD_ID);
        } catch (Throwable t) {
            t.printStackTrace();
            try {
                var hm = player.getHudManager();
                if (hm != null) hm.setCustomHud(playerRef, null);
            } catch (Throwable ignored) {}
        }
    }
}