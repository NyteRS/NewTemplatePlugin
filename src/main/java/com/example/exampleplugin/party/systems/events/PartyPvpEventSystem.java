package com.example.exampleplugin.party.systems.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage.EntitySource;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage.Source;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;

public class PartyPvpEventSystem extends DamageEventSystem {
   public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Damage damage) {
      Ref victimRef = archetypeChunk.getReferenceTo(index);
      PlayerRef victimPlayerRef = (PlayerRef)store.getComponent(victimRef, PlayerRef.getComponentType());
      if (victimPlayerRef != null) {
         Source source = damage.getSource();
         if (source instanceof EntitySource) {
            EntitySource damageEntitySource = (EntitySource)source;
            Ref attackerRef = damageEntitySource.getRef();
            if (attackerRef.isValid()) {
               Player attackerPlayer = (Player)commandBuffer.getComponent(attackerRef, Player.getComponentType());
               if (attackerPlayer != null) {
                  PlayerRef attackerPlayerRef = (PlayerRef)commandBuffer.getComponent(attackerRef, PlayerRef.getComponentType());
                  if (attackerPlayerRef != null) {
                     PartyManager manager = PartyManager.getInstance();
                     PartyInfo victimParty = manager.getPartyFromPlayer(victimPlayerRef.getUuid());
                     PartyInfo attackerParty = manager.getPartyFromPlayer(attackerPlayerRef.getUuid());
                     if (victimParty != null && attackerParty != null && victimParty.getId().equals(attackerParty.getId()) && !victimParty.isPvpEnabled()) {
                        damage.setCancelled(true);
                     }

                  }
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
