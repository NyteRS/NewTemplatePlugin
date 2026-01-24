package com.example.exampleplugin.party.commands.subcommand;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.example.exampleplugin.party.config.PartyProConfig;
import com.example.exampleplugin.party.lang.LanguageManager;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.ping.Ping;
import com.example.exampleplugin.party.ping.PingManager;
import com.example.exampleplugin.party.systems.CooldownManager;
import com.example.exampleplugin.party.util.NotificationHelper;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class PingCommand extends AbstractPlayerCommand {
   public PingCommand() {
      super("ping", "Ping a location for your party members");
      this.addAliases(new String[]{"p"});
   }

   protected void execute(@NonNullDecl CommandContext commandContext, @NonNullDecl Store<EntityStore> store, @NonNullDecl Ref<EntityStore> ref, @NonNullDecl PlayerRef playerRef, @NonNullDecl World world) {
      PartyInfo party = PartyManager.getInstance().getPartyFromPlayer(playerRef.getUuid());
      if (party == null) {
         NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.ping"), LanguageManager.getInstance().get("error.ping_need_party"));
      } else {
         int cooldownRemaining = CooldownManager.getInstance().getPingCooldownRemaining(playerRef.getUuid(), PartyProConfig.getInstance().getPingCooldown());
         if (cooldownRemaining > 0) {
            NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.ping"), LanguageManager.getInstance().get("error.cooldown_ping", cooldownRemaining));
         } else {
            double maxPingDistance = PartyProConfig.getInstance().getPingMaxDistance();
            Vector3i targetBlock = TargetUtil.getTargetBlock(ref, maxPingDistance, store);
            if (targetBlock == null) {
               NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.ping"), LanguageManager.getInstance().get("error.ping_look_at_block"));
            } else {
               Vector3d pingPosition = new Vector3d((double)targetBlock.x + 0.5D, (double)targetBlock.y + 1.0D, (double)targetBlock.z + 0.5D);
               CooldownManager.getInstance().setPingCooldown(playerRef.getUuid());
               Ping ping = PingManager.getInstance().createPing(playerRef.getUuid(), pingPosition, world.getName());
               if (ping == null) {
                  NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.ping"), "Failed to create ping");
               } else {
                  NotificationHelper.sendSuccess(playerRef, LanguageManager.getInstance().get("title.ping"), LanguageManager.getInstance().get("success.ping_created", (int)pingPosition.x, (int)pingPosition.y, (int)pingPosition.z));
               }
            }
         }
      }
   }
}
