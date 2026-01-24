package com.example.exampleplugin.party.commands.subcommand;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.commands.player.inventory.InventorySeeCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;
import com.example.exampleplugin.party.config.PartyProConfig;
import com.example.exampleplugin.party.lang.LanguageManager;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.systems.CooldownManager;
import com.example.exampleplugin.party.util.NotificationHelper;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class TeleportPartyCommand extends AbstractAsyncCommand {
   private RequiredArg<PlayerRef> playerArg;

   public TeleportPartyCommand() {
      super("tp", "Teleport to a party member");
      this.addAliases(new String[]{"teleport", "goto"});
      this.setPermissionGroup(GameMode.Adventure);
      this.playerArg = this.withRequiredArg("player", "The party member to teleport to", ArgTypes.PLAYER_REF);
   }

   @NonNullDecl
   protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
      CommandSender sender = commandContext.sender();
      if (sender instanceof Player) {
         Player player = (Player)sender;
         Ref ref = player.getReference();
         if (ref != null && ref.isValid()) {
            Store store = ref.getStore();
            World world = ((EntityStore)store.getExternalData()).getWorld();
            return CompletableFuture.runAsync(() -> {
               PlayerRef playerRef = (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
               if (playerRef != null) {
                  if (!PartyProConfig.getInstance().isTeleportEnabled()) {
                     NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.teleport_disabled"));
                  } else {
                     int cooldownRemaining = CooldownManager.getInstance().getTeleportCooldownRemaining(playerRef.getUuid(), PartyProConfig.getInstance().getTeleportCooldown());
                     if (cooldownRemaining > 0) {
                        NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.cooldown_teleport", cooldownRemaining));
                     } else {
                        PartyInfo party = PartyManager.getInstance().getPartyFromPlayer(playerRef.getUuid());
                        if (party == null) {
                           NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.not_in_party"));
                        } else {
                           PlayerRef targetPlayerRef = (PlayerRef)commandContext.get(this.playerArg);
                           if (targetPlayerRef == null) {
                              NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.player_not_found"));
                           } else if (targetPlayerRef.getUuid().equals(playerRef.getUuid())) {
                              NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.cannot_tp_self"));
                           } else if (!party.isLeaderOrMember(targetPlayerRef.getUuid())) {
                              NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.player_not_member", PartyManager.getInstance().getPlayerNameTracker().getPlayerName(targetPlayerRef.getUuid())));
                           } else {
                              Ref targetRef = targetPlayerRef.getReference();
                              if (targetRef != null && targetRef.isValid()) {
                                 Store targetStore = targetRef.getStore();
                                 TransformComponent targetTransform = (TransformComponent)targetStore.getComponent(targetRef, TransformComponent.getComponentType());
                                 if (targetTransform == null) {
                                    NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.target_not_in_world"));
                                 } else {
                                    CooldownManager.getInstance().setTeleportCooldown(playerRef.getUuid());
                                    String targetName = PartyManager.getInstance().getPlayerNameTracker().getPlayerName(targetPlayerRef.getUuid());
                                    Vector3d pos = targetTransform.getPosition();
                                    Vector3f rot = targetTransform.getRotation();
                                    Teleport teleport = new Teleport(world, pos, rot);
                                    store.addComponent(ref, Teleport.getComponentType(), teleport);
                                    NotificationHelper.sendSuccess(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.teleporting", targetName));
                                 }
                              } else {
                                 NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.target_not_in_world"));
                              }
                           }
                        }
                     }
                  }
               }
            }, world);
         } else {
            commandContext.sendMessage(InventorySeeCommand.MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
            return CompletableFuture.completedFuture((Object)null);
         }
      } else {
         return CompletableFuture.completedFuture((Object)null);
      }
   }
}
