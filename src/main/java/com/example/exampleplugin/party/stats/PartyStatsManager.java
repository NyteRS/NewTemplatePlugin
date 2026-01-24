package com.example.exampleplugin.party.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import com.example.exampleplugin.party.config.PartyProConfig;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.systems.PartyHealthTracker;

public class PartyStatsManager {
   private static final PartyStatsManager INSTANCE = new PartyStatsManager();
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final Type STATS_MAP_TYPE = (new TypeToken<Map<String, PartyStatsManager.PartyStatsData>>() {
   }).getType();
   private static final Type RESET_DATA_TYPE = (new TypeToken<PartyStatsManager.StatsResetData>() {
   }).getType();
   private final Map<UUID, PartyStats> partyStats = new ConcurrentHashMap();
   private Path statsPath;
   private Path resetDataPath;
   private boolean dirty = false;
   private LocalDate lastDailyReset;
   private LocalDate lastMonthlyReset;

   public static PartyStatsManager getInstance() {
      return INSTANCE;
   }

   private PartyStatsManager() {
   }

   public void initialize(Path configFolder) {
      this.statsPath = configFolder.resolve("party_stats.json");
      this.resetDataPath = configFolder.resolve("stats_reset_data.json");
      this.loadResetData();
      this.loadStats();
      this.checkAndPerformReset();
      Thread thread = new Thread(() -> {
         long lastResetCheck = System.currentTimeMillis();

         while(true) {
            try {
               Thread.sleep(5000L);
               this.updateAllPartyTimes();
               if (System.currentTimeMillis() - lastResetCheck > 3600000L) {
                  this.checkAndPerformReset();
                  lastResetCheck = System.currentTimeMillis();
               }

               if (this.dirty) {
                  this.saveStats();
                  this.dirty = false;
               }
            } catch (InterruptedException var4) {
               return;
            }
         }
      });
      thread.setDaemon(true);
      thread.setName("PartyPro-StatsManager");
      thread.start();
      System.out.println("[PartyPro] PartyStatsManager initialized");
   }

   private void loadResetData() {
      if (this.resetDataPath != null && Files.exists(this.resetDataPath, new LinkOption[0])) {
         try {
            String json = Files.readString(this.resetDataPath);
            PartyStatsManager.StatsResetData data = (PartyStatsManager.StatsResetData)GSON.fromJson(json, RESET_DATA_TYPE);
            if (data != null) {
               this.lastDailyReset = data.lastDailyReset != null ? LocalDate.parse(data.lastDailyReset) : LocalDate.now();
               this.lastMonthlyReset = data.lastMonthlyReset != null ? LocalDate.parse(data.lastMonthlyReset) : LocalDate.now();
            } else {
               this.lastDailyReset = LocalDate.now();
               this.lastMonthlyReset = LocalDate.now();
            }
         } catch (Exception var3) {
            this.lastDailyReset = LocalDate.now();
            this.lastMonthlyReset = LocalDate.now();
         }

      } else {
         this.lastDailyReset = LocalDate.now();
         this.lastMonthlyReset = LocalDate.now();
      }
   }

   private void saveResetData() {
      if (this.resetDataPath != null) {
         try {
            PartyStatsManager.StatsResetData data = new PartyStatsManager.StatsResetData();
            data.lastDailyReset = this.lastDailyReset.toString();
            data.lastMonthlyReset = this.lastMonthlyReset.toString();
            Files.createDirectories(this.resetDataPath.getParent());
            Files.writeString(this.resetDataPath, GSON.toJson(data), new OpenOption[0]);
         } catch (IOException var2) {
            System.out.println("[PartyPro] Failed to save reset data: " + var2.getMessage());
         }

      }
   }

   private void checkAndPerformReset() {
      PartyProConfig config = PartyProConfig.getInstance();
      LocalDate today = LocalDate.now();
      boolean resetPerformed = false;
      int resetDays = config.getStatsResetDays();
      if (resetDays > 0) {
         long daysSinceReset = ChronoUnit.DAYS.between(this.lastDailyReset, today);
         if (daysSinceReset >= (long)resetDays) {
            System.out.println("[PartyPro] Performing daily stats reset (every " + resetDays + " days)");
            this.resetAllStats();
            this.lastDailyReset = today;
            resetPerformed = true;
         }
      }

      int resetMonths = config.getStatsResetMonths();
      if (resetMonths > 0 && !resetPerformed) {
         long monthsSinceReset = ChronoUnit.MONTHS.between(this.lastMonthlyReset, today);
         if (monthsSinceReset >= (long)resetMonths) {
            System.out.println("[PartyPro] Performing monthly stats reset (every " + resetMonths + " months)");
            this.resetAllStats();
            this.lastMonthlyReset = today;
            resetPerformed = true;
         }
      }

      if (resetPerformed) {
         this.saveResetData();
         this.saveStats();
      }

   }

   public long getDaysUntilReset() {
      PartyProConfig config = PartyProConfig.getInstance();
      LocalDate today = LocalDate.now();
      long daysUntilReset = -1L;
      int resetDays = config.getStatsResetDays();
      if (resetDays > 0) {
         long daysSinceReset = ChronoUnit.DAYS.between(this.lastDailyReset, today);
         daysUntilReset = (long)resetDays - daysSinceReset;
      }

      int resetMonths = config.getStatsResetMonths();
      if (resetMonths > 0) {
         LocalDate nextMonthlyReset = this.lastMonthlyReset.plusMonths((long)resetMonths);
         long daysUntilMonthly = ChronoUnit.DAYS.between(today, nextMonthlyReset);
         if (daysUntilReset < 0L || daysUntilMonthly < daysUntilReset) {
            daysUntilReset = daysUntilMonthly;
         }
      }

      return Math.max(daysUntilReset, 0L);
   }

   public boolean isResetEnabled() {
      PartyProConfig config = PartyProConfig.getInstance();
      return config.getStatsResetDays() > 0 || config.getStatsResetMonths() > 0;
   }

   public void resetAllStats() {
      this.partyStats.clear();
      this.dirty = true;
      System.out.println("[PartyPro] All party stats have been reset");
   }

   public void resetPartyStats(UUID partyId) {
      this.partyStats.put(partyId, new PartyStats(partyId));
      this.dirty = true;
   }

   private void updateAllPartyTimes() {
      Iterator var1 = PartyManager.getInstance().getParties().values().iterator();

      while(var1.hasNext()) {
         PartyInfo party = (PartyInfo)var1.next();
         PartyStats stats = this.getStats(party.getId());
         int onlineCount = this.countOnlineMembers(party);
         stats.updateTimeTogether(onlineCount);
         if (onlineCount >= 2) {
            this.dirty = true;
         }
      }

   }

   private int countOnlineMembers(PartyInfo party) {
      int count = 0;
      UUID[] var3 = party.getAllPartyMembers();
      int var4 = var3.length;

      for(int var5 = 0; var5 < var4; ++var5) {
         UUID memberId = var3[var5];
         if (PartyHealthTracker.isOnline(memberId)) {
            ++count;
         }
      }

      return count;
   }

   public PartyStats getStats(UUID partyId) {
      return (PartyStats)this.partyStats.computeIfAbsent(partyId, PartyStats::new);
   }

   public void removeStats(UUID partyId) {
      this.partyStats.remove(partyId);
      this.dirty = true;
   }

   public void markDirty() {
      this.dirty = true;
   }

   private void loadStats() {
      if (this.statsPath != null && Files.exists(this.statsPath, new LinkOption[0])) {
         try {
            String json = Files.readString(this.statsPath);
            Map<String, PartyStatsManager.PartyStatsData> loaded = (Map)GSON.fromJson(json, STATS_MAP_TYPE);
            if (loaded != null) {
               Iterator var3 = loaded.entrySet().iterator();

               while(var3.hasNext()) {
                  Entry entry = (Entry)var3.next();

                  try {
                     UUID partyId = UUID.fromString((String)entry.getKey());
                     PartyStats stats = ((PartyStatsManager.PartyStatsData)entry.getValue()).toPartyStats(partyId);
                     this.partyStats.put(partyId, stats);
                  } catch (IllegalArgumentException var7) {
                  }
               }
            }

            System.out.println("[PartyPro] Loaded stats for " + this.partyStats.size() + " parties");
         } catch (IOException var8) {
            System.out.println("[PartyPro] Failed to load party stats: " + var8.getMessage());
         }

      }
   }

   public void saveStats() {
      if (this.statsPath != null) {
         try {
            Map<String, PartyStatsManager.PartyStatsData> toSave = new HashMap();
            Iterator var2 = this.partyStats.entrySet().iterator();

            while(var2.hasNext()) {
               Entry<UUID, PartyStats> entry = (Entry)var2.next();
               toSave.put(((UUID)entry.getKey()).toString(), PartyStatsManager.PartyStatsData.fromPartyStats((PartyStats)entry.getValue()));
            }

            Files.createDirectories(this.statsPath.getParent());
            Files.writeString(this.statsPath, GSON.toJson(toSave), new OpenOption[0]);
         } catch (IOException var4) {
            System.out.println("[PartyPro] Failed to save party stats: " + var4.getMessage());
         }

      }
   }

   public static class StatsResetData {
      public String lastDailyReset;
      public String lastMonthlyReset;
   }

   public static class PartyStatsData {
      public long timeTogetherMs;
      public int membersJoined;
      public int membersLeft;
      public Map<String, PartyStatsManager.PlayerStatsData> memberStats = new HashMap();

      public static PartyStatsManager.PartyStatsData fromPartyStats(PartyStats stats) {
         PartyStatsManager.PartyStatsData data = new PartyStatsManager.PartyStatsData();
         data.timeTogetherMs = stats.getTimeTogetherMs();
         data.membersJoined = stats.getMembersJoined();
         data.membersLeft = stats.getMembersLeft();
         Iterator var2 = stats.getAllMemberStats().entrySet().iterator();

         while(var2.hasNext()) {
            Entry<UUID, PlayerStats> entry = (Entry)var2.next();
            data.memberStats.put(((UUID)entry.getKey()).toString(), PartyStatsManager.PlayerStatsData.fromPlayerStats((PlayerStats)entry.getValue()));
         }

         return data;
      }

      public PartyStats toPartyStats(UUID partyId) {
         PartyStats stats = new PartyStats(partyId);

         try {
            Field field = PartyStats.class.getDeclaredField("timeTogetherMs");
            field.setAccessible(true);
            field.setLong(stats, this.timeTogetherMs);
         } catch (Exception var8) {
         }

         int i;
         for(i = 0; i < this.membersJoined; ++i) {
            stats.addMemberJoined();
         }

         for(i = 0; i < this.membersLeft; ++i) {
            stats.addMemberLeft();
         }

         Iterator var10 = this.memberStats.entrySet().iterator();

         while(var10.hasNext()) {
            Entry entry = (Entry)var10.next();

            try {
               UUID playerId = UUID.fromString((String)entry.getKey());
               PlayerStats playerStats = stats.getPlayerStats(playerId);
               ((PartyStatsManager.PlayerStatsData)entry.getValue()).applyToPlayerStats(playerStats);
            } catch (IllegalArgumentException var7) {
            }
         }

         return stats;
      }
   }

   public static class PlayerStatsData {
      public int mobKills;
      public int playerKills;
      public int deaths;
      public long damageDealt;
      public long damageTaken;
      public int blocksPlaced;
      public int blocksBroken;
      public long distanceTraveled;
      public int pingsSent;
      public int teleportsUsed;
      public int itemsCollected;
      public int itemsCrafted;

      public static PartyStatsManager.PlayerStatsData fromPlayerStats(PlayerStats stats) {
         PartyStatsManager.PlayerStatsData data = new PartyStatsManager.PlayerStatsData();
         data.mobKills = stats.getMobKills();
         data.playerKills = stats.getPlayerKills();
         data.deaths = stats.getDeaths();
         data.damageDealt = stats.getDamageDealt();
         data.damageTaken = stats.getDamageTaken();
         data.blocksPlaced = stats.getBlocksPlaced();
         data.blocksBroken = stats.getBlocksBroken();
         data.distanceTraveled = stats.getDistanceTraveled();
         data.pingsSent = stats.getPingsSent();
         data.teleportsUsed = stats.getTeleportsUsed();
         data.itemsCollected = stats.getItemsCollected();
         data.itemsCrafted = stats.getItemsCrafted();
         return data;
      }

      public void applyToPlayerStats(PlayerStats stats) {
         int i;
         for(i = 0; i < this.mobKills; ++i) {
            stats.addMobKill();
         }

         for(i = 0; i < this.playerKills; ++i) {
            stats.addPlayerKill();
         }

         for(i = 0; i < this.deaths; ++i) {
            stats.addDeath();
         }

         stats.addDamageDealt(this.damageDealt);
         stats.addDamageTaken(this.damageTaken);

         for(i = 0; i < this.blocksPlaced; ++i) {
            stats.addBlockPlaced();
         }

         for(i = 0; i < this.blocksBroken; ++i) {
            stats.addBlockBroken();
         }

         try {
            Field field = PlayerStats.class.getDeclaredField("distanceTraveled");
            field.setAccessible(true);
            field.setLong(stats, this.distanceTraveled);
         } catch (Exception var3) {
         }

         for(i = 0; i < this.pingsSent; ++i) {
            stats.addPingSent();
         }

         for(i = 0; i < this.teleportsUsed; ++i) {
            stats.addTeleportUsed();
         }

         stats.addItemsCollected(this.itemsCollected);

         for(i = 0; i < this.itemsCrafted; ++i) {
            stats.addItemCrafted();
         }

      }
   }
}
