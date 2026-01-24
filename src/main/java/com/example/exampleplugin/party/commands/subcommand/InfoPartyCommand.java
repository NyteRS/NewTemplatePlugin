package com.example.exampleplugin.party.commands.subcommand;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.commands.player.inventory.InventorySeeCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.example.exampleplugin.party.config.PartyProConfig;
import com.example.exampleplugin.party.lang.LanguageManager;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.util.NotificationHelper;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class InfoPartyCommand extends AbstractAsyncCommand {
   public InfoPartyCommand() {
      super("info", "Shows party info in chat");
      this.addAliases(new String[]{"status", "list", "members"});
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
                  PartyInfo party = PartyManager.getInstance().getPartyFromPlayer(playerRef.getUuid());
                  if (party == null) {
                     NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.not_in_party"));
                  } else {
                     this.showPartyInfo(player, playerRef, party);
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

   private void showPartyInfo(Player player, PlayerRef playerRef, PartyInfo party) {
      PartyManager manager = PartyManager.getInstance();
      player.sendMessage(Message.join(new Message[]{Message.raw("------- ").color(Color.YELLOW), Message.raw(party.getName()).color(Color.YELLOW).bold(true), Message.raw(" -------").color(Color.YELLOW)}));
      player.sendMessage(Message.join(new Message[]{Message.raw("Leader: ").color(Color.YELLOW), Message.raw(manager.getPlayerNameTracker().getPlayerName(party.getLeader())).color(Color.GREEN)}));
      if (party.getMembers().length > 0) {
         player.sendMessage(Message.raw("Members:").color(Color.YELLOW));
         UUID[] var5 = party.getMembers();
         int var6 = var5.length;

         for(int var7 = 0; var7 < var6; ++var7) {
            UUID memberUuid = var5[var7];
            player.sendMessage(Message.join(new Message[]{Message.raw("  - ").color(Color.YELLOW), Message.raw(manager.getPlayerNameTracker().getPlayerName(memberUuid)).color(Color.WHITE)}));
         }
      }

      Message[] var10001 = new Message[]{Message.raw("Size: ").color(Color.YELLOW), null};
      int var10004 = party.getTotalMemberCount();
      var10001[1] = Message.raw(var10004 + "/" + PartyProConfig.getInstance().getMaxPartySize()).color(Color.WHITE);
      player.sendMessage(Message.join(var10001));
      player.sendMessage(Message.raw("---------------------------").color(Color.YELLOW));
   }
}
