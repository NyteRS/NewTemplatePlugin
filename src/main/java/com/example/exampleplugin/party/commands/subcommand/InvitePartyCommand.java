package com.example.exampleplugin.party.commands.subcommand;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.commands.player.inventory.InventorySeeCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;
import com.example.exampleplugin.party.config.PartyProConfig;
import com.example.exampleplugin.party.gui.PartyInviteGui;
import com.example.exampleplugin.party.hud.PartyHudManager;
import com.example.exampleplugin.party.lang.LanguageManager;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyInvite;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.systems.CooldownManager;
import com.example.exampleplugin.party.systems.IdleTracker;
import com.example.exampleplugin.party.util.NotificationHelper;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class InvitePartyCommand extends AbstractAsyncCommand {
   private RequiredArg<PlayerRef> playerArg;

   public InvitePartyCommand() {
      super("invite", "Invites a player to your party");
      this.setPermissionGroup(GameMode.Adventure);
      this.playerArg = this.withRequiredArg("player", "The player to invite", ArgTypes.PLAYER_REF);
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
                  int cooldownRemaining = CooldownManager.getInstance().getInviteCooldownRemaining(playerRef.getUuid(), PartyProConfig.getInstance().getInviteCooldown());
                  if (cooldownRemaining > 0) {
                     NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.cooldown_invite", cooldownRemaining));
                  } else {
                     PartyInfo party = PartyManager.getInstance().getPartyFromPlayer(playerRef.getUuid());
                     if (party == null) {
                        NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.not_in_party"));
                     } else if (!party.isLeader(playerRef.getUuid())) {
                        NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.not_leader"));
                     } else if (!party.canAddMember()) {
                        NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.party_full"));
                     } else {
                        PlayerRef invitedPlayerRef = (PlayerRef)commandContext.get(this.playerArg);
                        if (invitedPlayerRef == null) {
                           NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.player_not_found"));
                        } else {
                           Player invitedPlayer = (Player)store.getComponent(invitedPlayerRef.getReference(), Player.getComponentType());
                           if (invitedPlayer == null) {
                              NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.player_not_found"));
                           } else if (party.isLeaderOrMember(invitedPlayerRef.getUuid())) {
                              NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.cannot_invite_self"));
                           } else {
                              PartyInfo existingParty = PartyManager.getInstance().getPartyFromPlayer(invitedPlayerRef.getUuid());
                              if (existingParty != null) {
                                 NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.player_in_party"));
                              } else {
                                 CooldownManager.getInstance().setInviteCooldown(playerRef.getUuid());
                                 PartyHudManager.HudSettings invitedSettings = PartyHudManager.getSettings(invitedPlayerRef.getUuid());
                                 if (invitedSettings.inviteMode == PartyHudManager.InviteMode.AUTO_DECLINE) {
                                    NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.invite_auto_declined", invitedPlayer.getDisplayName()));
                                 } else if (invitedSettings.inviteMode == PartyHudManager.InviteMode.AUTO_ACCEPT) {
                                    PartyManager.JoinResult joinResult = PartyManager.getInstance().joinParty(invitedPlayerRef.getUuid(), party);
                                    if (joinResult == PartyManager.JoinResult.SUCCESS) {
                                       NotificationHelper.sendSuccess(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.invite_sent", invitedPlayer.getDisplayName()));
                                       NotificationHelper.sendSuccess(invitedPlayerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.party_joined", party.getName()));
                                       PartyHudManager.refreshHud(invitedPlayerRef.getUuid());
                                    }

                                 } else {
                                    PartyManager.getInstance().invitePlayerToParty(invitedPlayerRef, party, playerRef);
                                    NotificationHelper.sendSuccess(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.invite_sent", invitedPlayer.getDisplayName()));
                                    if (PartyProConfig.getInstance().isShowInvitePopup()) {
                                       PartyInvite invite = PartyManager.getInstance().getPendingInvite(invitedPlayerRef.getUuid());
                                       if (invite != null) {
                                          Ref invitedRef = invitedPlayerRef.getReference();
                                          if (invitedRef != null && invitedRef.isValid()) {
                                             Store invitedStore = invitedRef.getStore();
                                             if (IdleTracker.getInstance().isIdleLongEnough(invitedPlayerRef.getUuid())) {
                                                PartyInviteGui inviteGui = new PartyInviteGui(invitedPlayerRef, invite, party);
                                                invitedPlayer.getPageManager().openCustomPage(invitedRef, invitedStore, inviteGui);
                                             } else {
                                                NotificationHelper.sendInfo(invitedPlayerRef, LanguageManager.getInstance().get("title.party_invite"), LanguageManager.getInstance().get("notify.invite_received", player.getDisplayName(), party.getName()));
                                             }
                                          }
                                       }
                                    } else {
                                       NotificationHelper.sendInfo(invitedPlayerRef, LanguageManager.getInstance().get("title.party_invite"), LanguageManager.getInstance().get("notify.invite_received", player.getDisplayName(), party.getName()));
                                    }

                                 }
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
