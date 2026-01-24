package com.example.exampleplugin.party.compass;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager.MarkerProvider;
import com.hypixel.hytale.server.core.util.PositionUtil;
import java.util.UUID;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.systems.PartyHealthTracker;

public class PartyCompassMarkerProvider implements MarkerProvider {
   private final int chunkViewRadius;
   private static final String[] MEMBER_ICONS = new String[]{"RedUser_Tracker.png", "BlueUser_Tracker.png", "GreenUser_Tracker.png", "YellowUser_Tracker.png", "CyanUser_Tracker.png", "PurpleUser_Tracker.png", "OrangeUser_Tracker.png", "PinkUser_Tracker.png", "BlueUser_Tracker.png", "OrangeUser_Tracker.png", "GreenUser_Tracker.png", "CyanUser_Tracker.png", "RedUser_Tracker.png", "PurpleUser_Tracker.png", "CyanUser_Tracker.png", "YellowUser_Tracker.png"};

   public PartyCompassMarkerProvider(int chunkViewRadius) {
      this.chunkViewRadius = chunkViewRadius;
   }

   public void update(World world, GameplayConfig gameplayConfig, WorldMapTracker tracker, int chunkViewRadius, int playerChunkX, int playerChunkZ) {
      PlayerRef viewerRef = tracker.getPlayer().getPlayerRef();
      UUID viewerUuid = viewerRef.getUuid();
      PartyInfo party = PartyManager.getInstance().getPartyFromPlayer(viewerUuid);
      if (party != null) {
         Vector3d myPos = PartyHealthTracker.getPosition(viewerUuid);
         if (myPos != null) {
            UUID leaderUuid = party.getLeader();
            if (!leaderUuid.equals(viewerUuid)) {
               this.showMemberMarker(world, tracker, myPos, leaderUuid, 0, playerChunkX, playerChunkZ);
            }

            UUID[] members = party.getMembers();

            for(int i = 0; i < members.length && i < 15; ++i) {
               UUID memberUuid = members[i];
               if (!memberUuid.equals(viewerUuid)) {
                  this.showMemberMarker(world, tracker, myPos, memberUuid, i + 1, playerChunkX, playerChunkZ);
               }
            }

         }
      }
   }

   private void showMemberMarker(World world, WorldMapTracker tracker, Vector3d myPos, UUID memberUuid, int slotIndex, int playerChunkX, int playerChunkZ) {
      if (PartyHealthTracker.isOnline(memberUuid)) {
         PlayerRef otherPlayer = Universe.get().getPlayer(memberUuid);
         if (otherPlayer != null && otherPlayer.isValid()) {
            Vector3d otherPos = PartyHealthTracker.getPosition(memberUuid);
            if (otherPos != null) {
               try {
                  Transform otherTransform = otherPlayer.getTransform();
                  float yaw = otherPlayer.getHeadRotation().getYaw();
                  double dx = otherPos.getX() - myPos.getX();
                  double dz = otherPos.getZ() - myPos.getZ();
                  int distance = (int)Math.sqrt(dx * dx + dz * dz);
                  String var10000 = otherPlayer.getUsername();
                  String displayName = var10000 + " (" + distance + "m)";
                  String icon = MEMBER_ICONS[Math.min(slotIndex, MEMBER_ICONS.length - 1)];
                  tracker.trySendMarker(this.chunkViewRadius, playerChunkX, playerChunkZ, otherPos, yaw, "PartyMember-" + memberUuid.toString(), displayName, otherPlayer, (id, name, op) -> {
                     return new MapMarker(id, name, icon, PositionUtil.toTransformPacket(op.getTransform()), (ContextMenuItem[])null);
                  });
               } catch (Exception var19) {
               }

            }
         }
      }
   }
}
