package com.example.exampleplugin.party.stats;

import java.util.UUID;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;

public class PartyStatsTracker {
   private static final PartyStatsTracker INSTANCE = new PartyStatsTracker();

   public static PartyStatsTracker getInstance() {
      return INSTANCE;
   }

   private PartyStatsTracker() {
   }

   private PlayerStats getPlayerStats(UUID playerId) {
      PartyInfo party = PartyManager.getInstance().getPartyFromPlayer(playerId);
      if (party == null) {
         return null;
      } else {
         PartyStats partyStats = PartyStatsManager.getInstance().getStats(party.getId());
         return partyStats.getPlayerStats(playerId);
      }
   }

   public void onMobKill(UUID playerId) {
      PlayerStats stats = this.getPlayerStats(playerId);
      if (stats != null) {
         stats.addMobKill();
         PartyStatsManager.getInstance().markDirty();
      }

   }

   public void onPlayerKill(UUID playerId) {
      PlayerStats stats = this.getPlayerStats(playerId);
      if (stats != null) {
         stats.addPlayerKill();
         PartyStatsManager.getInstance().markDirty();
      }

   }

   public void onDeath(UUID playerId) {
      PlayerStats stats = this.getPlayerStats(playerId);
      if (stats != null) {
         stats.addDeath();
         PartyStatsManager.getInstance().markDirty();
      }

   }

   public void onDamageDealt(UUID playerId, double amount) {
      PlayerStats stats = this.getPlayerStats(playerId);
      if (stats != null) {
         stats.addDamageDealt((long)amount);
         PartyStatsManager.getInstance().markDirty();
      }

   }

   public void onDamageTaken(UUID playerId, double amount) {
      PlayerStats stats = this.getPlayerStats(playerId);
      if (stats != null) {
         stats.addDamageTaken((long)amount);
         PartyStatsManager.getInstance().markDirty();
      }

   }

   public void onBlockPlaced(UUID playerId) {
      PlayerStats stats = this.getPlayerStats(playerId);
      if (stats != null) {
         stats.addBlockPlaced();
         PartyStatsManager.getInstance().markDirty();
      }

   }

   public void onBlockBroken(UUID playerId) {
      PlayerStats stats = this.getPlayerStats(playerId);
      if (stats != null) {
         stats.addBlockBroken();
         PartyStatsManager.getInstance().markDirty();
      }

   }

   public void addDistanceTraveled(UUID playerId, long blocks) {
      PlayerStats stats = this.getPlayerStats(playerId);
      if (stats != null) {
         stats.addDistanceTraveled(blocks);
         PartyStatsManager.getInstance().markDirty();
      }

   }

   public void onPingSent(UUID playerId) {
      PlayerStats stats = this.getPlayerStats(playerId);
      if (stats != null) {
         stats.addPingSent();
         PartyStatsManager.getInstance().markDirty();
      }

   }

   public void onTeleportUsed(UUID playerId) {
      PlayerStats stats = this.getPlayerStats(playerId);
      if (stats != null) {
         stats.addTeleportUsed();
         PartyStatsManager.getInstance().markDirty();
      }

   }

   public void onMemberJoined(UUID partyId) {
      PartyStats stats = PartyStatsManager.getInstance().getStats(partyId);
      stats.addMemberJoined();
      PartyStatsManager.getInstance().markDirty();
   }

   public void onMemberLeft(UUID partyId) {
      PartyStats stats = PartyStatsManager.getInstance().getStats(partyId);
      stats.addMemberLeft();
      PartyStatsManager.getInstance().markDirty();
   }

   public void onItemCollected(UUID playerId, int count) {
      PlayerStats stats = this.getPlayerStats(playerId);
      if (stats != null) {
         stats.addItemsCollected(count);
         PartyStatsManager.getInstance().markDirty();
      }

   }

   public void onItemCrafted(UUID playerId) {
      PlayerStats stats = this.getPlayerStats(playerId);
      if (stats != null) {
         stats.addItemCrafted();
         PartyStatsManager.getInstance().markDirty();
      }

   }
}
