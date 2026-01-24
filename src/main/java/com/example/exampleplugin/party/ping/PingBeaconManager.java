package com.example.exampleplugin.party.ping;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

public class PingBeaconManager {
   private static PingBeaconManager instance;
   private static final String PING_BEACON_PARTICLE = "PingBeacon";
   private static final Color[] SLOT_COLORS = new Color[]{new Color((byte)-23, (byte)69, (byte)96), new Color((byte)88, (byte)-90, (byte)-1), new Color((byte)86, (byte)-45, (byte)100), new Color((byte)-16, (byte)-64, (byte)64), new Color((byte)0, (byte)-68, (byte)-44), new Color((byte)-100, (byte)39, (byte)-80), new Color((byte)-1, (byte)-104, (byte)0), new Color((byte)-23, (byte)30, (byte)99), new Color((byte)96, (byte)125, (byte)-117), new Color((byte)121, (byte)85, (byte)72), new Color((byte)-117, (byte)-61, (byte)74), new Color((byte)3, (byte)-87, (byte)-12), new Color((byte)-1, (byte)87, (byte)34), new Color((byte)103, (byte)58, (byte)-73), new Color((byte)0, (byte)-106, (byte)-120), new Color((byte)-51, (byte)-36, (byte)57)};
   private final Map<UUID, UUID> activeBeacons = new ConcurrentHashMap();

   private PingBeaconManager() {
   }

   public static PingBeaconManager getInstance() {
      if (instance == null) {
         instance = new PingBeaconManager();
      }

      return instance;
   }

   public void startBeacon(@Nonnull Ping ping) {
      this.activeBeacons.put(ping.getOwnerUuid(), ping.getPingId());
   }

   public void stopBeacon(@Nonnull UUID playerUuid) {
      this.activeBeacons.remove(playerUuid);
   }

   public boolean isBeaconActive(@Nonnull UUID playerUuid) {
      return this.activeBeacons.containsKey(playerUuid);
   }

   public void tickBeacons(@Nonnull Store<EntityStore> store) {
      PingManager pingManager = PingManager.getInstance();
      Iterator var3 = this.activeBeacons.entrySet().iterator();

      while(true) {
         while(var3.hasNext()) {
            Entry<UUID, UUID> entry = (Entry)var3.next();
            UUID playerUuid = (UUID)entry.getKey();
            UUID pingId = (UUID)entry.getValue();
            Ping ping = this.findPingById(pingManager, playerUuid, pingId);
            if (ping != null && !ping.isExpired()) {
               try {
                  this.spawnBeaconParticle(ping, store);
               } catch (Exception var9) {
               }
            } else {
               this.activeBeacons.remove(playerUuid, pingId);
            }
         }

         return;
      }
   }

   private Ping findPingById(@Nonnull PingManager pingManager, @Nonnull UUID playerUuid, @Nonnull UUID pingId) {
      Iterator var4 = pingManager.getPingsForPlayer(playerUuid).iterator();

      Ping ping;
      do {
         if (!var4.hasNext()) {
            return null;
         }

         ping = (Ping)var4.next();
      } while(!ping.getPingId().equals(pingId) || !ping.getOwnerUuid().equals(playerUuid));

      return ping;
   }

   private void spawnBeaconParticle(@Nonnull Ping ping, @Nonnull Store<EntityStore> store) {
      Vector3d position = ping.getPosition();
      int ownerIndex = ping.getOwnerIndex();
      Color particleColor = SLOT_COLORS[Math.min(ownerIndex, SLOT_COLORS.length - 1)];
      SpatialResource<Ref<EntityStore>, EntityStore> playerSpatialResource = (SpatialResource)store.getResource(EntityModule.get().getPlayerSpatialResourceType());
      ObjectList<Ref<EntityStore>> playerRefs = SpatialResource.getThreadLocalReferenceList();
      playerSpatialResource.getSpatialStructure().collect(position, 1000.0D, playerRefs);
      ParticleUtil.spawnParticleEffect("PingBeacon", position.getX(), position.getY(), position.getZ(), 0.0F, 0.0F, 0.0F, 2.0F, particleColor, (Ref)null, playerRefs, store);
   }

   public void cleanupPlayer(@Nonnull UUID playerUuid) {
      this.activeBeacons.remove(playerUuid);
   }

   public void cleanupAll() {
      this.activeBeacons.clear();
   }
}
