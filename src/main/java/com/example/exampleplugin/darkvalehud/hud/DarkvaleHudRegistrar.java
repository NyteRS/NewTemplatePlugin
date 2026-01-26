package com.example.exampleplugin.darkvalehud.hud;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Simple registrar for the Darkvale HUD. Uses HudWrapper for optional MultipleHUD support.
 */
public class DarkvaleHudRegistrar {
    public static final String HUD_ID = "DarkvaleHud";

    public static void showHud(Player player, PlayerRef playerRef, CustomUIHud hud) {
        HudWrapper.setCustomHud(player, playerRef, HUD_ID, hud);
    }

    public static void hideHud(Player player, PlayerRef playerRef) {
        HudWrapper.hideCustomHud(player, playerRef, HUD_ID);
    }
}