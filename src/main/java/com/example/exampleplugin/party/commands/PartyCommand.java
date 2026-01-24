package com.example.exampleplugin.party.commands;

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
import com.example.exampleplugin.party.commands.subcommand.AcceptPartyCommand;
import com.example.exampleplugin.party.commands.subcommand.ChatPartyCommand;
import com.example.exampleplugin.party.commands.subcommand.CreatePartyCommand;
import com.example.exampleplugin.party.commands.subcommand.DebugPartyCommand;
import com.example.exampleplugin.party.commands.subcommand.DeclinePartyCommand;
import com.example.exampleplugin.party.commands.subcommand.DisbandPartyCommand;
import com.example.exampleplugin.party.commands.subcommand.InvitePartyCommand;
import com.example.exampleplugin.party.commands.subcommand.InvitesPartyCommand;
import com.example.exampleplugin.party.commands.subcommand.KickPartyCommand;
import com.example.exampleplugin.party.commands.subcommand.LeavePartyCommand;
import com.example.exampleplugin.party.commands.subcommand.RenamePartyCommand;
import com.example.exampleplugin.party.commands.subcommand.TeleportPartyCommand;
import com.example.exampleplugin.party.gui.PartyBrowserGui;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class PartyCommand extends AbstractAsyncCommand {
   public PartyCommand() {
      super("p", "Party management commands");
      this.setPermissionGroup(GameMode.Adventure);
      this.addAliases(new String[]{"party"});
      this.addSubCommand(new CreatePartyCommand());
      this.addSubCommand(new ChatPartyCommand());
      this.addSubCommand(new RenamePartyCommand());
      this.addSubCommand(new InvitePartyCommand());
      this.addSubCommand(new InvitesPartyCommand());
      this.addSubCommand(new AcceptPartyCommand());
      this.addSubCommand(new DeclinePartyCommand());
      this.addSubCommand(new LeavePartyCommand());
      this.addSubCommand(new KickPartyCommand());
      this.addSubCommand(new DisbandPartyCommand());
      this.addSubCommand(new TeleportPartyCommand());
      this.addSubCommand(new DebugPartyCommand());
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
                  PartyBrowserGui gui = new PartyBrowserGui(playerRef);
                  player.getPageManager().openCustomPage(ref, store, gui);
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
