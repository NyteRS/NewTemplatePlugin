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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.example.exampleplugin.party.lang.LanguageManager;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.util.NotificationHelper;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class TransferPartyCommand extends AbstractAsyncCommand {
   private RequiredArg<String> usernameArg;

   public TransferPartyCommand() {
      super("transfer", "Transfer party leadership to another member");
      this.addAliases(new String[]{"promote"});
      this.setPermissionGroup(GameMode.Adventure);
      this.usernameArg = this.withRequiredArg("username", "The player to transfer leadership to", ArgTypes.STRING);
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
                  PartyManager manager = PartyManager.getInstance();
                  PartyInfo party = manager.getPartyFromPlayer(playerRef.getUuid());
                  if (party == null) {
                     NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.not_in_party"));
                  } else if (!party.isLeader(playerRef.getUuid())) {
                     NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.leader_only_transfer"));
                  } else {
                     String targetUsername = (String)commandContext.get(this.usernameArg);
                     UUID targetUuid = manager.getPlayerNameTracker().getUuidByName(targetUsername);
                     if (targetUuid == null) {
                        NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.player_not_found_name", targetUsername));
                     } else if (!party.isMember(targetUuid)) {
                        NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.player_not_member", targetUsername));
                     } else {
                        party.removeMember(targetUuid);
                        party.addMember(party.getLeader());
                        party.setLeader(targetUuid);
                        manager.markDirty();
                        NotificationHelper.sendSuccess(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.ownership_transferred", targetUsername));
                        NotificationHelper.sendSuccessByUuid(targetUuid, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.you_are_now_leader"));
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
