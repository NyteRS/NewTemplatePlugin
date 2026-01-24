package com.example.exampleplugin.party.systems;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {
   private static CooldownManager instance;
   private final Map<UUID, Long> inviteCooldowns = new ConcurrentHashMap();
   private final Map<UUID, Long> teleportCooldowns = new ConcurrentHashMap();
   private final Map<UUID, Long> pingCooldowns = new ConcurrentHashMap();

   private CooldownManager() {
   }

   public static CooldownManager getInstance() {
      if (instance == null) {
         instance = new CooldownManager();
      }

      return instance;
   }

   public int getInviteCooldownRemaining(UUID playerId, int cooldownSeconds) {
      return this.getCooldownRemaining(this.inviteCooldowns, playerId, cooldownSeconds);
   }

   public int getTeleportCooldownRemaining(UUID playerId, int cooldownSeconds) {
      return this.getCooldownRemaining(this.teleportCooldowns, playerId, cooldownSeconds);
   }

   public int getPingCooldownRemaining(UUID playerId, int cooldownSeconds) {
      return this.getCooldownRemaining(this.pingCooldowns, playerId, cooldownSeconds);
   }

   public void setInviteCooldown(UUID playerId) {
      this.inviteCooldowns.put(playerId, System.currentTimeMillis());
   }

   public void setTeleportCooldown(UUID playerId) {
      this.teleportCooldowns.put(playerId, System.currentTimeMillis());
   }

   public void setPingCooldown(UUID playerId) {
      this.pingCooldowns.put(playerId, System.currentTimeMillis());
   }

   public void clearCooldowns(UUID playerId) {
      this.inviteCooldowns.remove(playerId);
      this.teleportCooldowns.remove(playerId);
      this.pingCooldowns.remove(playerId);
   }

   private int getCooldownRemaining(Map<UUID, Long> cooldownMap, UUID playerId, int cooldownSeconds) {
      if (cooldownSeconds <= 0) {
         return 0;
      } else {
         Long lastUse = (Long)cooldownMap.get(playerId);
         if (lastUse == null) {
            return 0;
         } else {
            long elapsed = System.currentTimeMillis() - lastUse;
            long cooldownMs = (long)cooldownSeconds * 1000L;
            if (elapsed >= cooldownMs) {
               cooldownMap.remove(playerId);
               return 0;
            } else {
               return (int)Math.ceil((double)(cooldownMs - elapsed) / 1000.0D);
            }
         }
      }
   }
}
