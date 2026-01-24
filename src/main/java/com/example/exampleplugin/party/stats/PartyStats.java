package com.example.exampleplugin.party.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PartyStats {
   private final UUID partyId;
   private final Map<UUID, PlayerStats> memberStats = new HashMap();
   private long timeTogetherMs = 0L;
   private int membersJoined = 0;
   private int membersLeft = 0;
   private long lastUpdateTime = System.currentTimeMillis();

   public PartyStats(UUID partyId) {
      this.partyId = partyId;
   }

   public UUID getPartyId() {
      return this.partyId;
   }

   public PlayerStats getPlayerStats(UUID playerId) {
      return (PlayerStats)this.memberStats.computeIfAbsent(playerId, PlayerStats::new);
   }

   public Map<UUID, PlayerStats> getAllMemberStats() {
      return this.memberStats;
   }

   public int getMobKills() {
      return this.memberStats.values().stream().mapToInt(PlayerStats::getMobKills).sum();
   }

   public int getPlayerKills() {
      return this.memberStats.values().stream().mapToInt(PlayerStats::getPlayerKills).sum();
   }

   public int getDeaths() {
      return this.memberStats.values().stream().mapToInt(PlayerStats::getDeaths).sum();
   }

   public long getDamageDealt() {
      return this.memberStats.values().stream().mapToLong(PlayerStats::getDamageDealt).sum();
   }

   public long getDamageTaken() {
      return this.memberStats.values().stream().mapToLong(PlayerStats::getDamageTaken).sum();
   }

   public int getBlocksPlaced() {
      return this.memberStats.values().stream().mapToInt(PlayerStats::getBlocksPlaced).sum();
   }

   public int getBlocksBroken() {
      return this.memberStats.values().stream().mapToInt(PlayerStats::getBlocksBroken).sum();
   }

   public double getDistanceTraveledBlocks() {
      return this.memberStats.values().stream().mapToDouble(PlayerStats::getDistanceTraveledBlocks).sum();
   }

   public int getPingsSent() {
      return this.memberStats.values().stream().mapToInt(PlayerStats::getPingsSent).sum();
   }

   public int getTeleportsUsed() {
      return this.memberStats.values().stream().mapToInt(PlayerStats::getTeleportsUsed).sum();
   }

   public int getItemsCollected() {
      return this.memberStats.values().stream().mapToInt(PlayerStats::getItemsCollected).sum();
   }

   public int getItemsCrafted() {
      return this.memberStats.values().stream().mapToInt(PlayerStats::getItemsCrafted).sum();
   }

   public long getTimeTogetherMs() {
      return this.timeTogetherMs;
   }

   public String getTimeTogetherFormatted() {
      long seconds = this.timeTogetherMs / 1000L;
      long minutes = seconds / 60L;
      long hours = minutes / 60L;
      minutes %= 60L;
      seconds %= 60L;
      if (hours > 0L) {
         return String.format("%dh %dm", hours, minutes);
      } else {
         return minutes > 0L ? String.format("%dm %ds", minutes, seconds) : String.format("%ds", seconds);
      }
   }

   public void updateTimeTogether(int onlineMembers) {
      long now = System.currentTimeMillis();
      if (onlineMembers >= 2) {
         this.timeTogetherMs += now - this.lastUpdateTime;
      }

      this.lastUpdateTime = now;
   }

   public int getMembersJoined() {
      return this.membersJoined;
   }

   public void addMemberJoined() {
      ++this.membersJoined;
   }

   public int getMembersLeft() {
      return this.membersLeft;
   }

   public void addMemberLeft() {
      ++this.membersLeft;
   }

   public void addMobKill() {
   }

   public void addPlayerKill() {
   }

   public void addDeath() {
   }

   public void addDamageDealt(long amount) {
   }

   public void addDamageTaken(long amount) {
   }

   public void addBlockPlaced() {
   }

   public void addBlockBroken() {
   }

   public void addDistanceTraveled(double blocks) {
   }

   public void addPingSent() {
   }

   public void addTeleportUsed() {
   }

   public void addItemsCollected(int count) {
   }

   public void addItemCrafted() {
   }
}
