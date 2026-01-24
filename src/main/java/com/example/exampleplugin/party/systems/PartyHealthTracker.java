package com.example.exampleplugin.party.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

public class PartyHealthTracker extends EntityTickingSystem<EntityStore> {
   private static final ConcurrentHashMap<UUID, PartyHealthTracker.PlayerStats> statsCache = new ConcurrentHashMap();
   private static final ConcurrentHashMap<UUID, Boolean> onlineCache = new ConcurrentHashMap();
   private static final ConcurrentHashMap<UUID, Vector3d> positionCache = new ConcurrentHashMap();

   public static boolean isOnline(UUID playerId) {
      return (Boolean)onlineCache.getOrDefault(playerId, false);
   }

   public static void setOnline(UUID playerId, boolean online) {
      onlineCache.put(playerId, online);
   }

   public static void setFakeStats(UUID playerId, float currentHealth, float maxHealth, float currentEnergy, float maxEnergy) {
      statsCache.put(playerId, new PartyHealthTracker.PlayerStats(currentHealth, maxHealth, currentEnergy, maxEnergy, false));
   }

   public static void removePlayer(UUID playerId) {
      onlineCache.remove(playerId);
      statsCache.remove(playerId);
      positionCache.remove(playerId);
   }

   public static PartyHealthTracker.PlayerHealth getHealth(UUID playerId) {
      PartyHealthTracker.PlayerStats stats = (PartyHealthTracker.PlayerStats)statsCache.getOrDefault(playerId, new PartyHealthTracker.PlayerStats(100.0F, 100.0F, 100.0F, 100.0F, false));
      return new PartyHealthTracker.PlayerHealth(stats.currentHealth, stats.maxHealth);
   }

   public static PartyHealthTracker.PlayerStats getStats(UUID playerId) {
      return (PartyHealthTracker.PlayerStats)statsCache.getOrDefault(playerId, new PartyHealthTracker.PlayerStats(100.0F, 100.0F, 100.0F, 100.0F, false));
   }

   public static Vector3d getPosition(UUID playerId) {
      return (Vector3d)positionCache.get(playerId);
   }

   public void tick(float deltaTime, int index, @NonNullDecl ArchetypeChunk<EntityStore> archetypeChunk, @NonNullDecl Store<EntityStore> store, @NonNullDecl CommandBuffer<EntityStore> commandBuffer) {
      Ref ref = archetypeChunk.getReferenceTo(index);
      PlayerRef playerRef = (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
      if (playerRef != null) {
         float currentHealth = 100.0F;
         float maxHealth = 100.0F;
         float currentEnergy = 100.0F;
         float maxEnergy = 100.0F;
         boolean isCreative = false;
         Player player = (Player)store.getComponent(ref, Player.getComponentType());
         if (player != null) {
            isCreative = player.getGameMode() == GameMode.Creative;
         }

         EntityStatMap statMap = (EntityStatMap)store.getComponent(ref, EntityStatMap.getComponentType());
         if (statMap != null) {
            int healthIndex = DefaultEntityStatTypes.getHealth();
            EntityStatValue healthStat = statMap.get(healthIndex);
            if (healthStat != null) {
               currentHealth = healthStat.get();
               maxHealth = healthStat.getMax();
            }

            int energyIndex = DefaultEntityStatTypes.getStamina();
            EntityStatValue energyStat = statMap.get(energyIndex);
            if (energyStat != null) {
               currentEnergy = energyStat.get();
               maxEnergy = energyStat.getMax();
            }
         }

         TransformComponent transform = (TransformComponent)store.getComponent(ref, TransformComponent.getComponentType());
         if (transform != null) {
            Vector3d pos = transform.getPosition();
            positionCache.put(playerRef.getUuid(), new Vector3d(pos.getX(), pos.getY(), pos.getZ()));
         }

         statsCache.put(playerRef.getUuid(), new PartyHealthTracker.PlayerStats(currentHealth, maxHealth, currentEnergy, maxEnergy, isCreative));
      }
   }

   @NullableDecl
   public Query<EntityStore> getQuery() {
      return PlayerRef.getComponentType();
   }

   public static class PlayerStats {
      public float currentHealth;
      public float maxHealth;
      public float currentEnergy;
      public float maxEnergy;
      public boolean isCreative;

      public PlayerStats(float currentHealth, float maxHealth, float currentEnergy, float maxEnergy, boolean isCreative) {
         this.currentHealth = currentHealth;
         this.maxHealth = maxHealth;
         this.currentEnergy = currentEnergy;
         this.maxEnergy = maxEnergy;
         this.isCreative = isCreative;
      }

      public float getHealthPercent() {
         return this.maxHealth > 0.0F ? this.currentHealth / this.maxHealth * 100.0F : 0.0F;
      }

      public float getEnergyPercent() {
         return this.maxEnergy > 0.0F ? this.currentEnergy / this.maxEnergy * 100.0F : 0.0F;
      }
   }

   public static class PlayerHealth {
      public float current;
      public float max;

      public PlayerHealth(float current, float max) {
         this.current = current;
         this.max = max;
      }
   }
}
