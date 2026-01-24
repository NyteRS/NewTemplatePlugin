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
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage.EntitySource;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage.Source;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.example.exampleplugin.party.stats.PartyStatsTracker;

public class PartyDamageEventSystem extends EntityEventSystem<EntityStore, Damage> {
   public PartyDamageEventSystem() {
      super(Damage.class);
   }

   public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Damage event) {
      if (!event.isCancelled()) {
         Ref ref = archetypeChunk.getReferenceTo(index);
         Player player = (Player)store.getComponent(ref, Player.getComponentType());
         PlayerRef playerRef = (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
         if (playerRef != null && player != null) {
            PartyStatsTracker.getInstance().onDamageTaken(playerRef.getUuid(), (double)Math.round(event.getAmount()));
         }

         Source source = event.getSource();
         if (source instanceof EntitySource) {
            Ref attackerRef = ((EntitySource)source).getRef();
            if (attackerRef.isValid()) {
               Store attackerStore = attackerRef.getStore();
               PlayerRef attackerPlayerRef = (PlayerRef)attackerStore.getComponent(attackerRef, PlayerRef.getComponentType());
               if (attackerPlayerRef != null) {
                  PartyStatsTracker.getInstance().onDamageDealt(attackerPlayerRef.getUuid(), (double)Math.round(event.getAmount()));
               }
            }
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
