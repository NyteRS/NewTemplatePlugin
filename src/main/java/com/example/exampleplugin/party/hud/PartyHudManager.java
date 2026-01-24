package com.example.exampleplugin.party.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

public class PartyHudManager {
   private static final Map<UUID, IPartyHud> activeHuds = new ConcurrentHashMap();
   private static final Map<UUID, PartyHudManager.HudSettings> playerSettings = new ConcurrentHashMap();
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final Type SETTINGS_MAP_TYPE = (new TypeToken<Map<String, PartyHudManager.HudSettings>>() {
   }).getType();
   private static final long SAVE_INTERVAL_MS = 30000L;
   private static Path settingsPath;
   private static boolean dirty = false;

   public static void initialize(Path configFolder) {
      settingsPath = configFolder.resolve("player_settings.json");
      loadSettings();
      Thread saveThread = new Thread(() -> {
         while(true) {
            try {
               Thread.sleep(30000L);
               if (dirty) {
                  saveSettings();
                  dirty = false;
               }
            } catch (InterruptedException var1) {
               return;
            }
         }
      });
      saveThread.setDaemon(true);
      saveThread.setName("PartyPro-SettingsSaver");
      saveThread.start();
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         if (dirty) {
            saveSettings();
         }

      }, "PartyPro-SettingsShutdown"));
   }

   private static void loadSettings() {
      if (settingsPath != null && Files.exists(settingsPath, new LinkOption[0])) {
         try {
            String json = Files.readString(settingsPath);
            Map<String, PartyHudManager.HudSettings> loaded = (Map)GSON.fromJson(json, SETTINGS_MAP_TYPE);
            if (loaded != null) {
               Iterator var2 = loaded.entrySet().iterator();

               while(var2.hasNext()) {
                  Entry entry = (Entry)var2.next();

                  try {
                     UUID uuid = UUID.fromString((String)entry.getKey());
                     playerSettings.put(uuid, (PartyHudManager.HudSettings)entry.getValue());
                  } catch (IllegalArgumentException var5) {
                  }
               }
            }

            System.out.println("[PartyPro] Loaded " + playerSettings.size() + " player settings");
         } catch (IOException var6) {
            System.out.println("[PartyPro] Failed to load player settings: " + var6.getMessage());
         }

      }
   }

   public static void saveSettings() {
      if (settingsPath != null) {
         try {
            Map<String, PartyHudManager.HudSettings> toSave = new HashMap();
            Iterator var1 = playerSettings.entrySet().iterator();

            while(var1.hasNext()) {
               Entry<UUID, PartyHudManager.HudSettings> entry = (Entry)var1.next();
               toSave.put(((UUID)entry.getKey()).toString(), (PartyHudManager.HudSettings)entry.getValue());
            }

            Files.createDirectories(settingsPath.getParent());
            Files.writeString(settingsPath, GSON.toJson(toSave), new OpenOption[0]);
         } catch (IOException var3) {
            System.out.println("[PartyPro] Failed to save player settings: " + var3.getMessage());
         }

      }
   }

   public static void registerHud(@Nonnull UUID playerId, @Nonnull IPartyHud hud) {
      activeHuds.put(playerId, hud);
   }

   public static void unregisterHud(@Nonnull UUID playerId) {
      IPartyHud hud = (IPartyHud)activeHuds.remove(playerId);
      if (hud != null) {
         hud.stopAutoUpdate();
      }

   }

   public static IPartyHud getHud(@Nonnull UUID playerId) {
      return (IPartyHud)activeHuds.get(playerId);
   }

   public static void refreshHud(@Nonnull UUID playerId) {
      IPartyHud hud = (IPartyHud)activeHuds.get(playerId);
      if (hud != null) {
         hud.stopAutoUpdate();
         hud.startAutoUpdate();
      }

   }

   public static PartyHudManager.HudSettings getSettings(@Nonnull UUID playerId) {
      return (PartyHudManager.HudSettings)playerSettings.computeIfAbsent(playerId, (k) -> {
         return new PartyHudManager.HudSettings();
      });
   }

   public static void setSettings(@Nonnull UUID playerId, @Nonnull PartyHudManager.HudSettings settings) {
      playerSettings.put(playerId, settings);
      dirty = true;
      saveSettings();
      IPartyHud hud = (IPartyHud)activeHuds.get(playerId);
      if (hud != null) {
         hud.refreshHud();
      }

   }

   public static class HudSettings {
      public boolean enabled = true;
      public boolean showOnlyOnline = false;
      public PartyHudManager.InviteMode inviteMode;
      public String hudSide;
      public PartyHudManager.HudMode hudMode;

      public HudSettings() {
         this.inviteMode = PartyHudManager.InviteMode.NORMAL;
         this.hudSide = "RIGHT";
         this.hudMode = PartyHudManager.HudMode.NORMAL;
      }

      public HudSettings(boolean enabled) {
         this.inviteMode = PartyHudManager.InviteMode.NORMAL;
         this.hudSide = "RIGHT";
         this.hudMode = PartyHudManager.HudMode.NORMAL;
         this.enabled = enabled;
      }

      public boolean isHudOnLeft() {
         return "LEFT".equalsIgnoreCase(this.hudSide);
      }

      public void setHudSide(String side) {
         this.hudSide = "LEFT".equalsIgnoreCase(side) ? "LEFT" : "RIGHT";
      }

      public boolean isCompactMode() {
         return this.hudMode == PartyHudManager.HudMode.COMPACT;
      }
   }

   public static enum InviteMode {
      NORMAL,
      AUTO_ACCEPT,
      AUTO_DECLINE;

      // $FF: synthetic method
      private static PartyHudManager.InviteMode[] $values() {
         return new PartyHudManager.InviteMode[]{NORMAL, AUTO_ACCEPT, AUTO_DECLINE};
      }
   }

   public static enum HudMode {
      NORMAL,
      COMPACT;

      // $FF: synthetic method
      private static PartyHudManager.HudMode[] $values() {
         return new PartyHudManager.HudMode[]{NORMAL, COMPACT};
      }
   }
}
