package com.example.exampleplugin.party.hud;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.lang.reflect.Method;

public class HudWrapper {
   private static final PluginIdentifier MULTIPLE_HUD_ID = new PluginIdentifier("Buuz135", "MultipleHUD");
   private static volatile Boolean multipleHudAvailable = null;
   private static volatile Object multipleHudInstance = null;
   private static volatile Method setCustomHudMethod = null;
   private static volatile Method hideCustomHudMethod = null;
   private static volatile int checkAttempts = 0;
   private static final int MAX_RECHECK_ATTEMPTS = 10;

   private static synchronized boolean checkMultipleHudAvailable() {
      if (multipleHudAvailable != null && multipleHudAvailable) {
         return true;
      } else if (multipleHudAvailable != null && !multipleHudAvailable && checkAttempts >= 10) {
         return false;
      } else {
         ++checkAttempts;

         try {
            PluginBase multipleHudPlugin = PluginManager.get().getPlugin(MULTIPLE_HUD_ID);
            if (multipleHudPlugin != null) {
               Class<?> multipleHudClass = Class.forName("com.buuz135.mhud.MultipleHUD");
               Method getInstanceMethod = multipleHudClass.getMethod("getInstance");
               multipleHudInstance = getInstanceMethod.invoke((Object)null);
               setCustomHudMethod = multipleHudClass.getMethod("setCustomHud", Player.class, PlayerRef.class, String.class, CustomUIHud.class);
               hideCustomHudMethod = multipleHudClass.getMethod("hideCustomHud", Player.class, PlayerRef.class, String.class);
               multipleHudAvailable = true;
               System.out.println("[PartyPro] MultipleHUD detected - using multi-HUD support (attempt " + checkAttempts + ")");
               return true;
            } else {
               if (checkAttempts == 1) {
                  System.out.println("[PartyPro] MultipleHUD not found yet - will retry on next HUD call");
               } else if (checkAttempts >= 10) {
                  multipleHudAvailable = false;
                  System.out.println("[PartyPro] MultipleHUD not found after " + checkAttempts + " attempts - using standard HUD mode");
               }

               return false;
            }
         } catch (Exception var3) {
            if (checkAttempts >= 10) {
               multipleHudAvailable = false;
               System.out.println("[PartyPro] Error checking for MultipleHUD: " + var3.getMessage() + " - using standard HUD mode");
            }

            return false;
         }
      }
   }

   public static boolean isMultipleHudAvailable() {
      return checkMultipleHudAvailable();
   }

   public static void setCustomHud(Player player, PlayerRef playerRef, String hudId, CustomUIHud hud) {
      if (checkMultipleHudAvailable() && multipleHudInstance != null && setCustomHudMethod != null) {
         try {
            setCustomHudMethod.invoke(multipleHudInstance, player, playerRef, hudId, hud);
            return;
         } catch (Exception var5) {
            System.out.println("[PartyPro] Error setting HUD via MultipleHUD: " + var5.getMessage());
         }
      }

      player.getHudManager().setCustomHud(playerRef, hud);
   }

   public static void hideCustomHud(Player player, PlayerRef playerRef, String hudId) {
      if (checkMultipleHudAvailable() && multipleHudInstance != null && hideCustomHudMethod != null) {
         try {
            hideCustomHudMethod.invoke(multipleHudInstance, player, playerRef, hudId);
            return;
         } catch (Exception var4) {
            System.out.println("[PartyPro] Error hiding HUD via MultipleHUD: " + var4.getMessage());
         }
      }

      player.getHudManager().setCustomHud(playerRef, (CustomUIHud)null);
   }

   public static synchronized void reset() {
      multipleHudAvailable = null;
      multipleHudInstance = null;
      setCustomHudMethod = null;
      hideCustomHudMethod = null;
      checkAttempts = 0;
   }
}
