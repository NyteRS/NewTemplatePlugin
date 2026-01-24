package com.example.exampleplugin.party.commands.subcommand;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
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
import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.example.exampleplugin.party.config.PartyProConfig;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.systems.PartyHealthTracker;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class DebugPartyCommand extends AbstractAsyncCommand {
   public DebugPartyCommand() {
      super("debug", "Debug commands for testing party system (OP only)");
      this.setPermissionGroup(GameMode.Creative);
      this.addSubCommand(new DebugPartyCommand.AddFakeCommand());
      this.addSubCommand(new DebugPartyCommand.RemoveFakeCommand());
      this.addSubCommand(new DebugPartyCommand.ListCommand());
      this.addSubCommand(new DebugPartyCommand.ClearCommand());
   }

   @NonNullDecl
   protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
      CommandSender sender = commandContext.sender();
      if (sender instanceof Player) {
         Player player = (Player)sender;
         player.sendMessage(Message.raw("Usage: /p debug <addfake|removefake|list|clear>").color(Color.YELLOW));
      }

      return CompletableFuture.completedFuture((Object)null);
   }

   public static class AddFakeCommand extends AbstractAsyncCommand {
      private RequiredArg<String> nameArg;

      public AddFakeCommand() {
         super("addfake", "Add a fake player to your party");
         this.setPermissionGroup(GameMode.Creative);
         this.nameArg = this.withRequiredArg("name", "Fake player name", ArgTypes.STRING);
      }

      @NonNullDecl
      protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
         CommandSender sender = commandContext.sender();
         if (!(sender instanceof Player)) {
            return CompletableFuture.completedFuture((Object)null);
         } else {
            Player player = (Player)sender;
            Ref ref = player.getReference();
            if (ref != null && ref.isValid()) {
               Store store = ref.getStore();
               World world = ((EntityStore)store.getExternalData()).getWorld();
               return CompletableFuture.runAsync(() -> {
                  PlayerRef playerRef = (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
                  if (playerRef != null) {
                     String name = (String)commandContext.get(this.nameArg);
                     PartyInfo party = PartyManager.getInstance().getPartyFromPlayer(playerRef.getUuid());
                     if (party == null) {
                        player.sendMessage(Message.raw("Create a party first with /p create").color(Color.RED));
                     } else if (!party.canAddMember()) {
                        int var10001 = party.getTotalMemberCount();
                        player.sendMessage(Message.raw("Party is full! (" + var10001 + "/" + PartyProConfig.getInstance().getMaxPartySize() + ")").color(Color.RED));
                     } else {
                        UUID fakeUuid = UUID.nameUUIDFromBytes(("fake_" + name).getBytes());
                        PartyManager.getInstance().getPlayerNameTracker().setPlayerName(fakeUuid, name);
                        party.addMember(fakeUuid);
                        PartyHealthTracker.setOnline(fakeUuid, true);
                        float fakeHealth = 50.0F + (float)(Math.random() * 50.0D);
                        float fakeEnergy = 50.0F + (float)(Math.random() * 50.0D);
                        PartyHealthTracker.setFakeStats(fakeUuid, fakeHealth, 100.0F, fakeEnergy, 100.0F);
                        PartyManager.getInstance().markDirty();
                        player.sendMessage(Message.join(new Message[]{Message.raw("Added fake player: ").color(Color.GREEN), Message.raw(name).color(Color.YELLOW)}));
                     }
                  }
               }, world);
            } else {
               commandContext.sendMessage(InventorySeeCommand.MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
               return CompletableFuture.completedFuture((Object)null);
            }
         }
      }
   }

   public static class RemoveFakeCommand extends AbstractAsyncCommand {
      private RequiredArg<String> nameArg;

      public RemoveFakeCommand() {
         super("removefake", "Remove a fake player from your party");
         this.setPermissionGroup(GameMode.Creative);
         this.nameArg = this.withRequiredArg("name", "Fake player name", ArgTypes.STRING);
      }

      @NonNullDecl
      protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
         CommandSender sender = commandContext.sender();
         if (!(sender instanceof Player)) {
            return CompletableFuture.completedFuture((Object)null);
         } else {
            Player player = (Player)sender;
            Ref ref = player.getReference();
            if (ref != null && ref.isValid()) {
               Store store = ref.getStore();
               World world = ((EntityStore)store.getExternalData()).getWorld();
               return CompletableFuture.runAsync(() -> {
                  PlayerRef playerRef = (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
                  if (playerRef != null) {
                     String name = (String)commandContext.get(this.nameArg);
                     PartyInfo party = PartyManager.getInstance().getPartyFromPlayer(playerRef.getUuid());
                     if (party == null) {
                        player.sendMessage(Message.raw("You're not in a party!").color(Color.RED));
                     } else {
                        UUID fakeUuid = UUID.nameUUIDFromBytes(("fake_" + name).getBytes());
                        if (!party.isMember(fakeUuid)) {
                           player.sendMessage(Message.raw("Fake player not found: " + name).color(Color.RED));
                        } else {
                           party.removeMember(fakeUuid);
                           PartyHealthTracker.removePlayer(fakeUuid);
                           PartyManager.getInstance().markDirty();
                           player.sendMessage(Message.join(new Message[]{Message.raw("Removed fake player: ").color(Color.GREEN), Message.raw(name).color(Color.YELLOW)}));
                        }
                     }
                  }
               }, world);
            } else {
               commandContext.sendMessage(InventorySeeCommand.MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
               return CompletableFuture.completedFuture((Object)null);
            }
         }
      }
   }

   public static class ListCommand extends AbstractAsyncCommand {
      public ListCommand() {
         super("list", "List all party members");
         this.setPermissionGroup(GameMode.Creative);
      }

      @NonNullDecl
      protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
         CommandSender sender = commandContext.sender();
         if (!(sender instanceof Player)) {
            return CompletableFuture.completedFuture((Object)null);
         } else {
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
                        player.sendMessage(Message.raw("You're not in a party!").color(Color.RED));
                     } else {
                        player.sendMessage(Message.raw("=== Party Members ===").color(Color.YELLOW));
                        player.sendMessage(Message.join(new Message[]{Message.raw("Leader: ").color(Color.GRAY), Message.raw(PartyManager.getInstance().getPlayerNameTracker().getPlayerName(party.getLeader())).color(Color.GREEN)}));
                        UUID[] arr$ = party.getMembers();
                        int len$ = arr$.length;

                        for(int i$ = 0; i$ < len$; ++i$) {
                           UUID memberUuid = arr$[i$];
                           String memberName = PartyManager.getInstance().getPlayerNameTracker().getPlayerName(memberUuid);
                           boolean isFake = memberUuid.equals(UUID.nameUUIDFromBytes(("fake_" + memberName).getBytes()));
                           player.sendMessage(Message.join(new Message[]{Message.raw("  - ").color(Color.DARK_GRAY), Message.raw(memberName).color(Color.WHITE), isFake ? Message.raw(" [FAKE]").color(Color.MAGENTA) : Message.raw(" [REAL]").color(Color.CYAN)}));
                        }

                        int var10001 = party.getTotalMemberCount();
                        player.sendMessage(Message.raw("Size: " + var10001 + "/" + PartyProConfig.getInstance().getMaxPartySize()).color(Color.GRAY));
                     }
                  }
               }, world);
            } else {
               commandContext.sendMessage(InventorySeeCommand.MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
               return CompletableFuture.completedFuture((Object)null);
            }
         }
      }
   }

   public static class ClearCommand extends AbstractAsyncCommand {
      public ClearCommand() {
         super("clear", "Remove all fake players from your party");
         this.setPermissionGroup(GameMode.Creative);
      }

      @NonNullDecl
      protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
         CommandSender sender = commandContext.sender();
         if (!(sender instanceof Player)) {
            return CompletableFuture.completedFuture((Object)null);
         } else {
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
                        player.sendMessage(Message.raw("You're not in a party!").color(Color.RED));
                     } else {
                        int removed = 0;
                        UUID[] arr$ = (UUID[])party.getMembers().clone();
                        int len$ = arr$.length;

                        for(int i$ = 0; i$ < len$; ++i$) {
                           UUID memberUuid = arr$[i$];
                           String memberName = PartyManager.getInstance().getPlayerNameTracker().getPlayerName(memberUuid);
                           if (memberUuid.equals(UUID.nameUUIDFromBytes(("fake_" + memberName).getBytes()))) {
                              party.removeMember(memberUuid);
                              PartyHealthTracker.removePlayer(memberUuid);
                              ++removed;
                           }
                        }

                        PartyManager.getInstance().markDirty();
                        player.sendMessage(Message.raw("Removed " + removed + " fake player(s).").color(Color.GREEN));
                     }
                  }
               }, world);
            } else {
               commandContext.sendMessage(InventorySeeCommand.MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
               return CompletableFuture.completedFuture((Object)null);
            }
         }
      }
   }
}
