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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;
import com.example.exampleplugin.party.chat.PartyChatManager;
import com.example.exampleplugin.party.lang.LanguageManager;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.util.NotificationHelper;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class ChatPartyCommand extends AbstractAsyncCommand {
   public ChatPartyCommand() {
      super("chat", "Toggle party chat mode");
      this.setPermissionGroup(GameMode.Adventure);
   }

   @NonNullDecl
   protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
      CommandSender sender = ctx.sender();
      if (!(sender instanceof Player)) {
         return CompletableFuture.completedFuture((Object)null);
      } else {
         Player player = (Player)sender;
         Ref<EntityStore> ref = player.getReference();
         if (ref != null && ref.isValid()) {
            Store<EntityStore> store = ref.getStore();
            World world = ((EntityStore)store.getExternalData()).getWorld();
            return CompletableFuture.runAsync(() -> {
               PlayerRef playerRef = (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
               if (playerRef != null) {
                  LanguageManager lang = LanguageManager.getInstance();
                  PartyInfo playerParty = PartyManager.getInstance().getPartyFromPlayer(playerRef.getUuid());
                  if (playerParty == null) {
                     NotificationHelper.sendError(playerRef, lang.get("title.party"), lang.get("error.not_in_party"));
                  } else {
                     boolean newState = PartyChatManager.getInstance().togglePartyChat(playerRef.getUuid());
                     if (newState) {
                        NotificationHelper.sendSuccess(playerRef, lang.get("title.party"), lang.get("chat.party_chat_enabled"));
                     } else {
                        NotificationHelper.sendSuccess(playerRef, lang.get("title.party"), lang.get("chat.party_chat_disabled"));
                     }

                  }
               }
            }, world);
         } else {
            ctx.sendMessage(InventorySeeCommand.MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
            return CompletableFuture.completedFuture((Object)null);
         }
      }
   }
}
