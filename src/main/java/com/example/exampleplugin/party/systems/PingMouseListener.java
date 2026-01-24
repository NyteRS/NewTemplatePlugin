package com.example.exampleplugin.party.systems;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;
import com.example.exampleplugin.party.config.PartyProConfig;
import com.example.exampleplugin.party.lang.LanguageManager;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.ping.Ping;
import com.example.exampleplugin.party.ping.PingManager;
import com.example.exampleplugin.party.util.NotificationHelper;

public class PingMouseListener {
   public static void onMouseButton(@Nonnull PlayerMouseButtonEvent event) {
      if (event.getMouseButton().mouseButtonType == MouseButtonType.Middle) {
         if (event.getMouseButton().state == MouseButtonState.Pressed) {
            Player player = event.getPlayer();
            PlayerRef playerRef = event.getPlayerRefComponent();
            UUID playerUuid = playerRef.getUuid();
            PartyInfo party = PartyManager.getInstance().getPartyFromPlayer(playerUuid);
            if (party == null) {
               NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.ping"), LanguageManager.getInstance().get("error.ping_need_party"));
            } else {
               int cooldownRemaining = CooldownManager.getInstance().getPingCooldownRemaining(playerUuid, PartyProConfig.getInstance().getPingCooldown());
               if (cooldownRemaining > 0) {
                  NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.ping"), LanguageManager.getInstance().get("error.cooldown_ping", cooldownRemaining));
               } else {
                  Vector3i targetBlock = event.getTargetBlock();
                  if (targetBlock == null) {
                     NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.ping"), LanguageManager.getInstance().get("error.ping_look_at_block"));
                  } else {
                     World world = player.getWorld();
                     if (world != null) {
                        Vector3d pingPosition = new Vector3d((double)targetBlock.x + 0.5D, (double)targetBlock.y + 1.0D, (double)targetBlock.z + 0.5D);
                        CooldownManager.getInstance().setPingCooldown(playerUuid);
                        Ping ping = PingManager.getInstance().createPing(playerUuid, pingPosition, world.getName());
                        if (ping != null) {
                           NotificationHelper.sendSuccess(playerRef, LanguageManager.getInstance().get("title.ping"), LanguageManager.getInstance().get("success.ping_created", String.format("%.0f", pingPosition.x), String.format("%.0f", pingPosition.y), String.format("%.0f", pingPosition.z)));
                        }

                     }
                  }
               }
            }
         }
      }
   }
}
