package com.example.exampleplugin.party.commands.subcommand;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.commands.player.inventory.InventorySeeCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;
import com.example.exampleplugin.party.hud.IPartyHud;
import com.example.exampleplugin.party.hud.PartyHudManager;
import com.example.exampleplugin.party.lang.LanguageManager;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyInvite;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.util.NotificationHelper;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class AcceptPartyCommand extends AbstractAsyncCommand {
   public AcceptPartyCommand() {
      super("accept", "Accepts a pending party invite");
      this.addAliases(new String[]{"join"});
      this.setPermissionGroup(GameMode.Adventure);
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
                  PartyInfo existingParty = PartyManager.getInstance().getPartyFromPlayer(playerRef.getUuid());
                  if (existingParty != null) {
                     NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.already_in_party"));
                  } else {
                     PartyInvite invite = PartyManager.getInstance().getPendingInvite(playerRef.getUuid());
                     if (invite == null) {
                        NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.no_pending_invite"));
                     } else {
                        PartyInfo party = PartyManager.getInstance().getPartyById(invite.partyId());
                        if (party == null) {
                           NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.party_no_longer_exists"));
                        } else if (!party.canAddMember()) {
                           NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.party_full"));
                           PartyManager.getInstance().declineInvite(playerRef.getUuid());
                        } else {
                           PartyInvite acceptedInvite = PartyManager.getInstance().acceptInvite(playerRef);
                           if (acceptedInvite == null) {
                              NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.no_pending_invite"));
                           } else {
                              NotificationHelper.sendSuccess(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.party_joined", party.getName()));
                              IPartyHud hud = PartyHudManager.getHud(playerRef.getUuid());
                              if (hud != null) {
                                 hud.refreshHud();
                              }

                              PlayerRef senderRef = Universe.get().getPlayer(acceptedInvite.sender());
                              if (senderRef != null && senderRef.isValid()) {
                                 NotificationHelper.sendSuccess(senderRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("notify.player_joined", player.getDisplayName()));
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
