package com.example.exampleplugin.party.compass;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager.MarkerProvider;
import com.hypixel.hytale.server.core.util.PositionUtil;
import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;
import javax.annotation.Nonnull;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.ping.Ping;
import com.example.exampleplugin.party.ping.PingManager;

public class PartyPingMarkerProvider implements MarkerProvider {
   public static final PartyPingMarkerProvider INSTANCE = new PartyPingMarkerProvider();
   private static final String[] PING_ICONS = new String[]{"RedUser_Ping.png", "BlueUser_Ping.png", "GreenUser_Ping.png", "YellowUser_Ping.png", "CyanUser_Ping.png", "PurpleUser_Ping.png", "OrangeUser_Ping.png", "PinkUser_Ping.png", "BlueUser_Ping.png", "OrangeUser_Ping.png", "GreenUser_Ping.png", "CyanUser_Ping.png", "RedUser_Ping.png", "PurpleUser_Ping.png", "CyanUser_Ping.png", "YellowUser_Ping.png"};

   private PartyPingMarkerProvider() {
   }

   public void update(@Nonnull World world, @Nonnull GameplayConfig gameplayConfig, @Nonnull WorldMapTracker tracker, int chunkViewRadius, int playerChunkX, int playerChunkZ) {
      Player player = tracker.getPlayer();
      UUID playerUuid = player.getUuid();
      PartyInfo party = PartyManager.getInstance().getPartyFromPlayer(playerUuid);
      if (party != null) {
         PingManager pingManager = PingManager.getInstance();
         Collection<Ping> pings = pingManager.getPartyPings(party.getId());
         Iterator var12 = pings.iterator();

         while(var12.hasNext()) {
            Ping ping = (Ping)var12.next();
            if (!ping.isExpired() && ping.getWorldName().equals(world.getName())) {
               Vector3d pingPos = ping.getPosition();
               String markerId = "PartyPing-" + ping.getPingId().toString();
               int ownerIndex = ping.getOwnerIndex();
               String iconName = PING_ICONS[Math.min(ownerIndex, PING_ICONS.length - 1)];
               float remainingTime = ping.getRemainingTime();
               String displayName = String.format("%s's Ping (%.0fs)", ping.getOwnerName(), remainingTime);
               tracker.trySendMarker(-1, playerChunkX, playerChunkZ, pingPos, 0.0F, markerId, displayName, ping, (id, name, p) -> {
                  return new MapMarker(id, name, iconName, PositionUtil.toTransformPacket(new Transform(p.getPosition())), (ContextMenuItem[])null);
               });
            }
         }

      }
   }
}
