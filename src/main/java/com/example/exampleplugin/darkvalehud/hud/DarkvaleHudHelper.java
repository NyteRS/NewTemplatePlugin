package com.example.exampleplugin.darkvalehud.hud;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nullable;

/**
 * Helper utilities for safely attaching/detaching the Darkvale HUD using MultipleHUD.
 *
 * Usage:
 *  - DarkvaleHudHelper.attachDarkvaleHud(player, playerRef, hud, world);
 *  - DarkvaleHudHelper.detachDarkvaleHud(player, playerRef);
 *
 * The attach method calls DarkvaleHudRegistrar.showHud(...) then schedules hud.show() on the provided world thread.
 * Scheduling reduces race conditions during player login/world transfer which commonly cause crashes.
 */
public final class DarkvaleHudHelper {
    private DarkvaleHudHelper() {}

    /**
     * Attach and show the HUD safely.
     *
     * @param player    Player instance (server-side entity)
     * @param playerRef PlayerRef component for the current entity
     * @param hud       CustomUIHud instance (your DarkvaleHud)
     * @param world     World instance used to schedule on server/world thread; may be null
     */
    public static void attachDarkvaleHud(Player player, PlayerRef playerRef, CustomUIHud hud, @Nullable World world) {
        try {
            // Register HUD with MultipleHUD (or fallback)
            DarkvaleHudRegistrar.showHud(player, playerRef, hud);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // Show the HUD on the world thread if possible to avoid race conditions
        if (world != null) {
            try {
                world.execute(() -> {
                    try {
                        hud.show();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                });
                return;
            } catch (Throwable t) {
                // scheduling failed - fall through to direct call below
                t.printStackTrace();
            }
        }

        // Fallback: immediate show (guarded)
        try {
            hud.show();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Detach/hide the hud using MultipleHUD (or fallback).
     *
     * @param player    Player instance
     * @param playerRef PlayerRef component
     */
    public static void detachDarkvaleHud(Player player, PlayerRef playerRef) {
        try {
            DarkvaleHudRegistrar.hideHud(player, playerRef);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}