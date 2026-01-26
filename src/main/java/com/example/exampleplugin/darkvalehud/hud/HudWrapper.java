package com.example.exampleplugin.darkvalehud.hud;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.lang.reflect.Method;

/**
 * HudWrapper: optional runtime integration with Buuz135's MultipleHUD.
 *
 * Behavior:
 * - If MultipleHUD is present at runtime, HudWrapper will attempt to call
 *   com.buuz135.mhud.MultipleHUD.getInstance().setCustomHud(...) and hideCustomHud(...) via reflection.
 * - Otherwise it falls back to attempting to use the player's HudManager via reflection.
 *
 * Notes:
 * - Designed to avoid a compile-time dependency on MHUD.
 * - Caches reflection lookups for performance.
 */
public class HudWrapper {
    private static volatile Boolean multipleHudAvailable = null;
    private static volatile Object multipleHudInstance = null;
    private static volatile Method setCustomHudMethod = null;
    private static volatile Method hideCustomHudMethod = null;
    private static volatile int checkAttempts = 0;
    private static final int MAX_RECHECK_ATTEMPTS = 10;

    private static synchronized boolean ensureMultipleHudAvailable() {
        if (multipleHudAvailable != null && multipleHudAvailable) return true;
        if (multipleHudAvailable != null && !multipleHudAvailable && checkAttempts >= MAX_RECHECK_ATTEMPTS) return false;
        ++checkAttempts;
        try {
            Class<?> multipleHudClass = Class.forName("com.buuz135.mhud.MultipleHUD");
            Method getInstance = multipleHudClass.getMethod("getInstance");
            Object instance = getInstance.invoke(null);

            Method setMethod = null;
            Method hideMethod = null;

            // Preferred signature for set: (Player, PlayerRef, String, CustomUIHud)
            try {
                setMethod = multipleHudClass.getMethod("setCustomHud", Player.class, PlayerRef.class, String.class, CustomUIHud.class);
            } catch (NoSuchMethodException ignored) {
            }

            // Try common hide signatures
            try {
                hideMethod = multipleHudClass.getMethod("hideCustomHud", Player.class, PlayerRef.class, String.class);
            } catch (NoSuchMethodException e1) {
                try {
                    hideMethod = multipleHudClass.getMethod("hideCustomHud", Player.class, String.class);
                } catch (NoSuchMethodException ignored) {}
            }

            multipleHudInstance = instance;
            setCustomHudMethod = setMethod;
            hideCustomHudMethod = hideMethod;
            multipleHudAvailable = true;
            return true;
        } catch (Throwable t) {
            if (checkAttempts >= MAX_RECHECK_ATTEMPTS) multipleHudAvailable = false;
            return false;
        }
    }

    public static void setCustomHud(Player player, PlayerRef playerRef, String hudId, CustomUIHud hud) {
        if (ensureMultipleHudAvailable() && multipleHudInstance != null && setCustomHudMethod != null) {
            try {
                setCustomHudMethod.invoke(multipleHudInstance, player, playerRef, hudId, hud);
                return;
            } catch (Throwable t) {
                System.out.println("[HudWrapper] Error invoking MultipleHUD.setCustomHud: " + t.getMessage());
                // fall through to fallback
            }
        }

        // Fallback: attempt via player's HudManager using reflection to support different server versions
        try {
            Method getHudManager = Player.class.getMethod("getHudManager");
            Object hudManager = getHudManager.invoke(player);
            if (hudManager != null) {
                try {
                    // common: setCustomHud(PlayerRef, CustomUIHud)
                    Method setCustomHud = hudManager.getClass().getMethod("setCustomHud", PlayerRef.class, CustomUIHud.class);
                    setCustomHud.invoke(hudManager, playerRef, hud);
                    return;
                } catch (NoSuchMethodException nsme) {
                    try {
                        // alternative: setCustomHud(PlayerRef, String, CustomUIHud)
                        Method setCustomHud2 = hudManager.getClass().getMethod("setCustomHud", PlayerRef.class, String.class, CustomUIHud.class);
                        setCustomHud2.invoke(hudManager, playerRef, hudId, hud);
                        return;
                    } catch (NoSuchMethodException ignored) {}
                }
            }
        } catch (Throwable ignored) {
        }

        System.out.println("[HudWrapper] Could not set HUD for " + hudId + " - no compatible API found.");
    }

    public static void hideCustomHud(Player player, PlayerRef playerRef, String hudId) {
        if (ensureMultipleHudAvailable() && multipleHudInstance != null && hideCustomHudMethod != null) {
            try {
                Class<?>[] params = hideCustomHudMethod.getParameterTypes();
                if (params.length == 3) {
                    // Player, PlayerRef, String
                    hideCustomHudMethod.invoke(multipleHudInstance, player, playerRef, hudId);
                } else if (params.length == 2) {
                    // Player, String
                    hideCustomHudMethod.invoke(multipleHudInstance, player, hudId);
                } else {
                    hideCustomHudMethod.invoke(multipleHudInstance, player, playerRef, hudId);
                }
                return;
            } catch (Throwable t) {
                System.out.println("[HudWrapper] Error invoking MultipleHUD.hideCustomHud: " + t.getMessage());
            }
        }

        // Fallback: try player's HudManager
        try {
            Method getHudManager = Player.class.getMethod("getHudManager");
            Object hudManager = getHudManager.invoke(player);
            if (hudManager != null) {
                try {
                    Method hideCustomHud = hudManager.getClass().getMethod("hideCustomHud", PlayerRef.class, String.class);
                    hideCustomHud.invoke(hudManager, playerRef, hudId);
                    return;
                } catch (NoSuchMethodException nsme) {
                    try {
                        Method hideCustomHud2 = hudManager.getClass().getMethod("hideCustomHud", PlayerRef.class);
                        hideCustomHud2.invoke(hudManager, playerRef);
                        return;
                    } catch (NoSuchMethodException ignored) {}
                }
            }
        } catch (Throwable ignored) {
        }

        System.out.println("[HudWrapper] Could not hide HUD for " + hudId + " - no compatible API found.");
    }
}