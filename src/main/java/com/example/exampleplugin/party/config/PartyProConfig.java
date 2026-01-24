package com.example.exampleplugin.party.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public class PartyProConfig {
   private static PartyProConfig instance;
   private static Path configFilePath;
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private String language = "EN";
   private int maxPartySize = 8;
   private boolean teleportEnabled = true;
   private boolean keepPartyOnDisconnect = true;
   private boolean showInvitePopup = false;
   private String hudSide = "RIGHT";
   private int hudOffset = 30;
   private int hudTop = 130;
   private int hudWidth = 260;
   private int hudUpdateInterval = 25;
   private boolean compassTrackingEnabled = true;
   private int compassChunkViewRadius = 100;
   private double pingMaxDistance = 1000.0D;
   private int inviteCooldown = 5;
   private int teleportCooldown = 30;
   private int pingCooldown = 3;
   private int offlineRemovalTime = 0;
   private int statsResetDays = 0;
   private int statsResetMonths = 0;
   private boolean simpleClaimsIntegration = true;
   private boolean persistPartiesOnRestart = true;

   public static PartyProConfig getInstance() {
      if (instance == null) {
         instance = new PartyProConfig();
      }

      return instance;
   }

   private int clamp(int val, int min, int max) {
      return Math.max(min, Math.min(max, val));
   }

   private double clamp(double val, double min, double max) {
      return Math.max(min, Math.min(max, val));
   }

   public String getLanguage() {
      return this.language;
   }

   public void setLanguage(String lang) {
      this.language = lang.toUpperCase();
   }

   public int getMaxPartySize() {
      return this.maxPartySize;
   }

   public void setMaxPartySize(int size) {
      this.maxPartySize = this.clamp(size, 2, 16);
   }

   public boolean isTeleportEnabled() {
      return this.teleportEnabled;
   }

   public void setTeleportEnabled(boolean enabled) {
      this.teleportEnabled = enabled;
   }

   public boolean isKeepPartyOnDisconnect() {
      return this.keepPartyOnDisconnect;
   }

   public void setKeepPartyOnDisconnect(boolean keep) {
      this.keepPartyOnDisconnect = keep;
   }

   public boolean isShowInvitePopup() {
      return this.showInvitePopup;
   }

   public void setShowInvitePopup(boolean show) {
      this.showInvitePopup = show;
   }

   public String getHudSide() {
      return this.hudSide;
   }

   public void setHudSide(String side) {
      this.hudSide = "LEFT".equalsIgnoreCase(side) ? "LEFT" : "RIGHT";
   }

   public boolean isHudOnLeft() {
      return "LEFT".equalsIgnoreCase(this.hudSide);
   }

   public int getHudOffset() {
      return this.hudOffset;
   }

   public void setHudOffset(int offset) {
      this.hudOffset = this.clamp(offset, 0, 500);
   }

   public int getHudTop() {
      return this.hudTop;
   }

   public void setHudTop(int top) {
      this.hudTop = this.clamp(top, 0, 1000);
   }

   public int getHudWidth() {
      return this.hudWidth;
   }

   public int getHudUpdateInterval() {
      return this.hudUpdateInterval;
   }

   public boolean isCompassTrackingEnabled() {
      return this.compassTrackingEnabled;
   }

   public void setCompassTrackingEnabled(boolean enabled) {
      this.compassTrackingEnabled = enabled;
   }

   public int getCompassChunkViewRadius() {
      return this.compassChunkViewRadius;
   }

   public void setCompassChunkViewRadius(int radius) {
      this.compassChunkViewRadius = this.clamp(radius, 10, 500);
   }

   public double getPingMaxDistance() {
      return this.pingMaxDistance;
   }

   public void setPingMaxDistance(double distance) {
      this.pingMaxDistance = this.clamp(distance, 50.0D, 2000.0D);
   }

   public int getInviteCooldown() {
      return this.inviteCooldown;
   }

   public void setInviteCooldown(int seconds) {
      this.inviteCooldown = Math.max(0, seconds);
   }

   public int getTeleportCooldown() {
      return this.teleportCooldown;
   }

   public void setTeleportCooldown(int seconds) {
      this.teleportCooldown = Math.max(0, seconds);
   }

   public int getPingCooldown() {
      return this.pingCooldown;
   }

   public void setPingCooldown(int seconds) {
      this.pingCooldown = Math.max(0, seconds);
   }

   public int getOfflineRemovalTime() {
      return this.offlineRemovalTime;
   }

   public void setOfflineRemovalTime(int minutes) {
      this.offlineRemovalTime = Math.max(0, minutes);
   }

   public int getStatsResetDays() {
      return this.statsResetDays;
   }

   public void setStatsResetDays(int days) {
      this.statsResetDays = Math.max(0, days);
   }

   public int getStatsResetMonths() {
      return this.statsResetMonths;
   }

   public void setStatsResetMonths(int months) {
      this.statsResetMonths = Math.max(0, months);
   }

   public boolean isSimpleClaimsIntegration() {
      return this.simpleClaimsIntegration;
   }

   public void setSimpleClaimsIntegration(boolean enabled) {
      this.simpleClaimsIntegration = enabled;
   }

   public boolean isPersistPartiesOnRestart() {
      return this.persistPartiesOnRestart;
   }

   public void setPersistPartiesOnRestart(boolean persist) {
      this.persistPartiesOnRestart = persist;
   }

   public static void load(Path configPath) {
      configFilePath = configPath;

      try {
         if (Files.exists(configPath, new LinkOption[0])) {
            String json = Files.readString(configPath);
            instance = (PartyProConfig)GSON.fromJson(json, PartyProConfig.class);
            if (instance == null) {
               instance = new PartyProConfig();
            }

            save(configPath);
            System.out.println("[PartyPro] Config loaded: maxPartySize=" + instance.maxPartySize + ", teleportEnabled=" + instance.teleportEnabled + ", hudSide=" + instance.hudSide + ", hudOffset=" + instance.hudOffset + ", language=" + instance.language);
         } else {
            instance = new PartyProConfig();
            save(configPath);
            System.out.println("[PartyPro] Config created with defaults");
         }
      } catch (IOException var2) {
         System.out.println("[PartyPro] Failed to load config: " + var2.getMessage());
         instance = new PartyProConfig();
      }

   }

   public static void save(Path configPath) {
      try {
         Files.createDirectories(configPath.getParent());
         Files.writeString(configPath, GSON.toJson(instance), new OpenOption[0]);
      } catch (IOException var2) {
         System.out.println("[PartyPro] Failed to save config: " + var2.getMessage());
      }

   }

   public static void reload() {
      if (configFilePath != null) {
         load(configFilePath);
      }

   }
}
