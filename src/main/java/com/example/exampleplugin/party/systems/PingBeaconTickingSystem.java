package com.example.exampleplugin.party.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import com.example.exampleplugin.party.ping.PingBeaconManager;
import com.example.exampleplugin.party.ping.PingManager;

public class PingBeaconTickingSystem extends EntityTickingSystem<EntityStore> {
   private float tickTimer = 0.0F;
   private static final float TICK_INTERVAL = 1.0F;
   private boolean hasTickedThisInterval = false;

   public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
      this.tickTimer += dt;
      if (this.tickTimer >= 1.0F && !this.hasTickedThisInterval) {
         this.hasTickedThisInterval = true;
         PingManager.getInstance().cleanupExpiredPings();
         PingBeaconManager.getInstance().tickBeacons(store);
      }

      if (this.tickTimer >= 1.0F) {
         this.tickTimer = 0.0F;
         this.hasTickedThisInterval = false;
      }

   }

   @Nonnull
   public ComponentType<EntityStore, ?> getQuery() {
      return Player.getComponentType();
   }
}
