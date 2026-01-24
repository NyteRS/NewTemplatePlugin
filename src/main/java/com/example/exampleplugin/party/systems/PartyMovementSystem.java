package com.example.exampleplugin.party.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.example.exampleplugin.party.stats.PartyStatsTracker;

public class PartyMovementSystem extends EntityTickingSystem<EntityStore> {
   private final Map<UUID, Vector3d> lastPositions = new ConcurrentHashMap();
   private final Map<UUID, Double> accumulatedDistance = new ConcurrentHashMap();
   private static final double TELEPORT_THRESHOLD = 100.0D;
   private static final double MIN_DISTANCE = 1.0E-4D;

   public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
      Ref ref = chunk.getReferenceTo(index);
      PlayerRef playerRef = (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
      TransformComponent transform = (TransformComponent)store.getComponent(ref, TransformComponent.getComponentType());
      if (playerRef != null && transform != null) {
         UUID playerUuid = playerRef.getUuid();
         Vector3d currentPos = transform.getPosition();
         Vector3d lastPos = (Vector3d)this.lastPositions.get(playerUuid);
         if (lastPos != null) {
            double distanceBlocks = lastPos.distanceTo(currentPos);
            if (distanceBlocks >= 1.0E-4D && distanceBlocks < 100.0D) {
               double accumulated = (Double)this.accumulatedDistance.getOrDefault(playerUuid, 0.0D) + distanceBlocks;
               if (accumulated >= 1.0D) {
                  long blocksToAdd = (long)accumulated;
                  this.accumulatedDistance.put(playerUuid, accumulated - (double)blocksToAdd);
                  PartyStatsTracker.getInstance().addDistanceTraveled(playerUuid, blocksToAdd);
               } else {
                  this.accumulatedDistance.put(playerUuid, accumulated);
               }
            }
         }

         this.lastPositions.put(playerUuid, new Vector3d(currentPos));
      }
   }

   public void cleanupPlayer(UUID playerUuid) {
      this.lastPositions.remove(playerUuid);
      this.accumulatedDistance.remove(playerUuid);
   }

   @Nullable
   public Query<EntityStore> getQuery() {
      return PlayerRef.getComponentType();
   }

   @Nonnull
   public Set<Dependency<EntityStore>> getDependencies() {
      return Collections.singleton(RootDependency.first());
   }
}
