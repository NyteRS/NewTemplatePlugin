package com.example.exampleplugin.party.util;

import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import java.util.UUID;

public class NotificationHelper {
   private static void send(PlayerRef playerRef, String title, String message, NotificationStyle style) {
      if (playerRef != null && playerRef.isValid()) {
         PacketHandler handler = playerRef.getPacketHandler();
         if (handler != null) {
            NotificationUtil.sendNotification(handler, Message.raw(title), Message.raw(message), style);
         }

      }
   }

   public static void sendSuccess(PlayerRef playerRef, String title, String message) {
      send(playerRef, title, message, NotificationStyle.Success);
   }

   public static void sendError(PlayerRef playerRef, String title, String message) {
      send(playerRef, title, message, NotificationStyle.Danger);
   }

   public static void sendInfo(PlayerRef playerRef, String title, String message) {
      send(playerRef, title, message, NotificationStyle.Warning);
   }

   public static void sendSuccessByUuid(UUID playerId, String title, String message) {
      PlayerRef ref = Universe.get().getPlayer(playerId);
      if (ref != null && ref.isValid()) {
         sendSuccess(ref, title, message);
      }

   }

   public static void sendErrorByUuid(UUID playerId, String title, String message) {
      PlayerRef ref = Universe.get().getPlayer(playerId);
      if (ref != null && ref.isValid()) {
         sendError(ref, title, message);
      }

   }

   public static void sendInfoByUuid(UUID playerId, String title, String message) {
      PlayerRef ref = Universe.get().getPlayer(playerId);
      if (ref != null && ref.isValid()) {
         sendInfo(ref, title, message);
      }

   }
}
