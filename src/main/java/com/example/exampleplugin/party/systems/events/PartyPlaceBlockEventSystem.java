package com.example.exampleplugin.party.systems.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.example.exampleplugin.party.stats.PartyStatsTracker;

public class PartyPlaceBlockEventSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
   public PartyPlaceBlockEventSystem() {
      super(PlaceBlockEvent.class);
   }

   public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlaceBlockEvent event) {
      if (!event.isCancelled()) {
         Ref ref = archetypeChunk.getReferenceTo(index);
         Player player = (Player)store.getComponent(ref, Player.getComponentType());
         PlayerRef playerRef = (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
         if (player != null && playerRef != null) {
            PartyStatsTracker.getInstance().onBlockPlaced(playerRef.getUuid());
         }
      }

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
