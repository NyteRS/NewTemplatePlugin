package com.example.exampleplugin.party.systems;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.auth.PlayerAuthentication;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketWatcher;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import com.example.exampleplugin.party.config.PartyProConfig;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.ping.Ping;
import com.example.exampleplugin.party.ping.PingManager;
import com.example.exampleplugin.party.stats.PartyStatsTracker;
import com.example.exampleplugin.party.util.NotificationHelper;

public class PingPacketListener implements PacketWatcher {
   private static final double RAYCAST_STEP_SIZE = 1.0D;

   public void accept(PacketHandler packetHandler, Packet packet) {
      if (packet.getId() == 290) {
         PlayerAuthentication playerAuth = packetHandler.getAuth();
         if (playerAuth != null) {
            UUID uuid = playerAuth.getUuid();
            if (uuid != null) {
               PlayerRef playerRef = Universe.get().getPlayer(uuid);
               if (playerRef != null) {
                  SyncInteractionChains interactionChains = (SyncInteractionChains)packet;
                  this.processInteractionChains(playerRef, interactionChains);
               }
            }
         }
      }
   }

   private void processInteractionChains(PlayerRef playerRef, SyncInteractionChains packet) {
      if (playerRef != null && packet != null && packet.updates != null) {
         SyncInteractionChain[] var3 = packet.updates;
         int var4 = var3.length;

         for(int var5 = 0; var5 < var4; ++var5) {
            SyncInteractionChain chain = var3[var5];
            if (chain.interactionType == InteractionType.Pick) {
               this.handlePickInteraction(playerRef, chain);
               break;
            }
         }

      }
   }

   private void handlePickInteraction(PlayerRef playerRef, SyncInteractionChain chain) {
      UUID playerUuid = playerRef.getUuid();
      PartyInfo party = PartyManager.getInstance().getPartyFromPlayer(playerUuid);
      if (party != null) {
         int cooldownRemaining = CooldownManager.getInstance().getPingCooldownRemaining(playerUuid, PartyProConfig.getInstance().getPingCooldown());
         if (cooldownRemaining > 0) {
            NotificationHelper.sendError(playerRef, "Ping", "Please wait " + cooldownRemaining + "s before pinging again!");
         } else {
            World world = this.getPlayerWorld(playerRef);
            if (world != null) {
               world.execute(() -> {
                  this.handlePingOnWorldThread(playerRef, chain, world);
               });
            }
         }
      }
   }

   private void handlePingOnWorldThread(PlayerRef playerRef, SyncInteractionChain chain, World world) {
      Vector3d pingLocation;
      if (chain.data != null && chain.data.blockPosition != null) {
         BlockPosition blockPos = chain.data.blockPosition;
         pingLocation = new Vector3d((double)blockPos.x + 0.5D, (double)blockPos.y + 1.0D, (double)blockPos.z + 0.5D);
      } else {
         Transform transform;
         try {
            transform = playerRef.getTransform();
         } catch (Exception var11) {
            return;
         }

         if (transform == null) {
            return;
         }

         Vector3d eyePosition = new Vector3d(transform.getPosition().x, transform.getPosition().y + 1.62D, transform.getPosition().z);
         Vector3d lookDirection = this.getLookDirection(playerRef, transform);
         double maxDistance = PartyProConfig.getInstance().getPingMaxDistance();
         BlockPosition targetBlock = this.raycastToBlock(eyePosition, lookDirection, world, (int)maxDistance);
         if (targetBlock == null) {
            NotificationHelper.sendError(playerRef, "Ping", "Look at a block to ping");
            return;
         }

         pingLocation = new Vector3d((double)targetBlock.x + 0.5D, (double)targetBlock.y + 1.0D, (double)targetBlock.z + 0.5D);
      }

      CooldownManager.getInstance().setPingCooldown(playerRef.getUuid());
      Ping ping = PingManager.getInstance().createPing(playerRef.getUuid(), pingLocation, world.getName());
      if (ping != null) {
         PartyStatsTracker.getInstance().onPingSent(playerRef.getUuid());
         NotificationHelper.sendSuccess(playerRef, "Ping", String.format("Pinged at %.0f, %.0f, %.0f", pingLocation.x, pingLocation.y, pingLocation.z));
      }

   }

   private Vector3d getLookDirection(PlayerRef playerRef, Transform transform) {
      HeadRotation headRotation = (HeadRotation)playerRef.getComponent(HeadRotation.getComponentType());
      return headRotation != null ? headRotation.getDirection() : transform.getDirection();
   }

   private BlockPosition raycastToBlock(Vector3d origin, Vector3d direction, World world, int maxDistance) {
      for(int i = 0; i < maxDistance; ++i) {
         double t = (double)i * 1.0D;
         Vector3d rayPoint = new Vector3d(origin.x + direction.x * t, origin.y + direction.y * t, origin.z + direction.z * t);
         int blockX = (int)Math.floor(rayPoint.x);
         int blockY = (int)Math.floor(rayPoint.y);
         int blockZ = (int)Math.floor(rayPoint.z);
         if (world.getBlock(blockX, blockY, blockZ) != 0) {
            return new BlockPosition(blockX, blockY, blockZ);
         }
      }

      return null;
   }

   private World getPlayerWorld(PlayerRef playerRef) {
      UUID worldUuid = playerRef.getWorldUuid();
      return worldUuid == null ? null : Universe.get().getWorld(worldUuid);
   }
}
