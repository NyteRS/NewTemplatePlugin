package com.example.exampleplugin.party.stats;

import java.util.UUID;

public class PlayerStats {
   private final UUID playerId;
   private int mobKills = 0;
   private int playerKills = 0;
   private int deaths = 0;
   private long damageDealt = 0L;
   private long damageTaken = 0L;
   private int blocksPlaced = 0;
   private int blocksBroken = 0;
   private long distanceTraveled = 0L;
   private int pingsSent = 0;
   private int teleportsUsed = 0;
   private int itemsCollected = 0;
   private int itemsCrafted = 0;

   public PlayerStats(UUID playerId) {
      this.playerId = playerId;
   }

   public UUID getPlayerId() {
      return this.playerId;
   }

   public int getMobKills() {
      return this.mobKills;
   }

   public void addMobKill() {
      ++this.mobKills;
   }

   public int getPlayerKills() {
      return this.playerKills;
   }

   public void addPlayerKill() {
      ++this.playerKills;
   }

   public int getDeaths() {
      return this.deaths;
   }

   public void addDeath() {
      ++this.deaths;
   }

   public long getDamageDealt() {
      return this.damageDealt;
   }

   public void addDamageDealt(long amount) {
      this.damageDealt += amount;
   }

   public long getDamageTaken() {
      return this.damageTaken;
   }

   public void addDamageTaken(long amount) {
      this.damageTaken += amount;
   }

   public int getBlocksPlaced() {
      return this.blocksPlaced;
   }

   public void addBlockPlaced() {
      ++this.blocksPlaced;
   }

   public int getBlocksBroken() {
      return this.blocksBroken;
   }

   public void addBlockBroken() {
      ++this.blocksBroken;
   }

   public long getDistanceTraveled() {
      return this.distanceTraveled;
   }

   public double getDistanceTraveledBlocks() {
      return (double)this.distanceTraveled / 100.0D;
   }

   public void addDistanceTraveled(long blocks) {
      this.distanceTraveled += blocks * 100L;
   }

   public int getPingsSent() {
      return this.pingsSent;
   }

   public void addPingSent() {
      ++this.pingsSent;
   }

   public int getTeleportsUsed() {
      return this.teleportsUsed;
   }

   public void addTeleportUsed() {
      ++this.teleportsUsed;
   }

   public int getItemsCollected() {
      return this.itemsCollected;
   }

   public void addItemsCollected(int count) {
      this.itemsCollected += count;
   }

   public int getItemsCrafted() {
      return this.itemsCrafted;
   }

   public void addItemCrafted() {
      ++this.itemsCrafted;
   }
}
