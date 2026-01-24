package com.example.exampleplugin.party.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IdleTracker {
   private static final IdleTracker INSTANCE = new IdleTracker();
   private static final long UPDATE_INTERVAL_MS = 1000L;
   private static final long IDLE_THRESHOLD_MS = 3000L;
   private final Map<UUID, Long> idleSince = new ConcurrentHashMap();
   private Thread trackerThread;

   public static IdleTracker getInstance() {
      return INSTANCE;
   }

   private IdleTracker() {
   }

   public void start() {
      if (this.trackerThread == null || !this.trackerThread.isAlive()) {
         this.trackerThread = new Thread(() -> {
            while(true) {
               try {
                  this.updateAllPlayers();
                  Thread.sleep(1000L);
               } catch (InterruptedException var2) {
                  return;
               } catch (Exception var3) {
                  System.out.println("[PartyPro] IdleTracker error: " + var3.getMessage());
               }
            }
         });
         this.trackerThread.setDaemon(true);
         this.trackerThread.setName("PartyPro-IdleTracker");
         this.trackerThread.start();
         System.out.println("[PartyPro] IdleTracker started");
      }
   }

   private void updateAllPlayers() {
      long now = System.currentTimeMillis();
      Iterator var3 = Universe.get().getPlayers().iterator();

      while(var3.hasNext()) {
         PlayerRef playerRef = (PlayerRef)var3.next();
         if (playerRef != null && playerRef.isValid()) {
            UUID uuid = playerRef.getUuid();
            boolean isIdle = this.checkPlayerIdle(playerRef);
            if (isIdle) {
               this.idleSince.putIfAbsent(uuid, now);
            } else {
               this.idleSince.remove(uuid);
            }
         }
      }

      this.idleSince.keySet().removeIf((uuidx) -> {
         PlayerRef ref = Universe.get().getPlayer(uuidx);
         return ref == null || !ref.isValid();
      });
   }

   private boolean checkPlayerIdle(PlayerRef playerRef) {
      Ref ref = playerRef.getReference();
      if (ref != null && ref.isValid()) {
         try {
            Store store = ref.getStore();
            MovementStatesComponent movementComp = (MovementStatesComponent)store.getComponent(ref, EntityModule.get().getMovementStatesComponentType());
            if (movementComp == null) {
               return true;
            } else {
               MovementStates states = movementComp.getMovementStates();
               return states.onGround && !states.falling && !states.jumping && !states.swimming && !states.climbing && !states.rolling && !states.gliding && !states.sliding;
            }
         } catch (IllegalStateException var6) {
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean isIdleLongEnough(UUID playerUuid) {
      Long since = (Long)this.idleSince.get(playerUuid);
      if (since == null) {
         return false;
      } else {
         return System.currentTimeMillis() - since >= 3000L;
      }
   }

   public long getIdleTime(UUID playerUuid) {
      Long since = (Long)this.idleSince.get(playerUuid);
      return since == null ? 0L : System.currentTimeMillis() - since;
   }
}
