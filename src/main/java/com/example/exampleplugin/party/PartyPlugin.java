package com.example.exampleplugin.party;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import com.example.exampleplugin.party.chat.PartyChatEventHandler;
import com.example.exampleplugin.party.chat.PartyChatManager;
import com.example.exampleplugin.party.commands.PartyCommand;
import com.example.exampleplugin.party.commands.subcommand.PingCommand;
import com.example.exampleplugin.party.compass.PartyCompassMarkerProvider;
import com.example.exampleplugin.party.compass.PartyPingMarkerProvider;
import com.example.exampleplugin.party.config.PartyProConfig;
import com.example.exampleplugin.party.hud.HudWrapper;
import com.example.exampleplugin.party.hud.PartyHud;
import com.example.exampleplugin.party.hud.PartyHudCompact;
import com.example.exampleplugin.party.hud.PartyHudManager;
import com.example.exampleplugin.party.integration.SimpleClaimsIntegration;
import com.example.exampleplugin.party.lang.LanguageManager;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.party.PartyStorage;
import com.example.exampleplugin.party.stats.PartyStatsManager;
import com.example.exampleplugin.party.systems.IdleTracker;
import com.example.exampleplugin.party.systems.PartyHealthTracker;
import com.example.exampleplugin.party.systems.PartyMovementSystem;
import com.example.exampleplugin.party.systems.PingBeaconTickingSystem;
import com.example.exampleplugin.party.systems.PingPacketListener;
import com.example.exampleplugin.party.systems.events.PartyBlockEventSystem;
import com.example.exampleplugin.party.systems.events.PartyCraftEventSystem;
import com.example.exampleplugin.party.systems.events.PartyDamageEventSystem;
import com.example.exampleplugin.party.systems.events.PartyPlaceBlockEventSystem;
import com.example.exampleplugin.party.systems.events.PartyPvpEventSystem;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class PartyPlugin extends JavaPlugin {
   private static PartyPlugin INSTANCE;
   public static HashMap<String, World> WORLDS = new HashMap();

   public PartyPlugin(@NonNullDecl JavaPluginInit init) {
      super(init);
      INSTANCE = this;
   }

   public static PartyPlugin getInstance() {
      return INSTANCE;
   }

   public void createHudForPlayer(PlayerRef playerRef) {
      if (playerRef != null && playerRef.isValid()) {
         Ref ref = playerRef.getReference();
         if (ref != null && ref.isValid()) {
            Player player = (Player)ref.getStore().getComponent(ref, Player.getComponentType());
            if (player != null) {
               PartyHudManager.HudSettings settings = PartyHudManager.getSettings(playerRef.getUuid());
               if (settings.isCompactMode()) {
                  PartyHudCompact compactHud = new PartyHudCompact(playerRef);
                  HudWrapper.setCustomHud(player, playerRef, "PartyProHud", compactHud);
                  PartyHudManager.registerHud(playerRef.getUuid(), compactHud);
                  compactHud.startAutoUpdate();
               } else {
                  PartyHud partyHud = new PartyHud(playerRef);
                  HudWrapper.setCustomHud(player, playerRef, "PartyProHud", partyHud);
                  PartyHudManager.registerHud(playerRef.getUuid(), partyHud);
                  partyHud.startAutoUpdate();
               }

            }
         }
      }
   }

   protected void setup() {
      super.setup();
      PartyProConfig.load(this.getFile().getParent().resolve("PartyPro").resolve("config.json"));
      LanguageManager.getInstance().initialize(this.getFile().getParent().resolve("PartyPro"));
      PartyStorage.initialize(this.getFile().getParent().resolve("PartyPro").resolve("party_storage.json"));
      PartyHudManager.initialize(this.getFile().getParent().resolve("PartyPro"));
      PartyStatsManager.getInstance().initialize(this.getFile().getParent().resolve("PartyPro"));
      this.getEntityStoreRegistry().registerSystem(new PartyHealthTracker());
      this.getEntityStoreRegistry().registerSystem(new PartyPvpEventSystem());
      this.getEntityStoreRegistry().registerSystem(new PingBeaconTickingSystem());
      this.getEntityStoreRegistry().registerSystem(new PartyBlockEventSystem());
      this.getEntityStoreRegistry().registerSystem(new PartyPlaceBlockEventSystem());
      this.getEntityStoreRegistry().registerSystem(new PartyCraftEventSystem());
      this.getEntityStoreRegistry().registerSystem(new PartyDamageEventSystem());
      this.getEntityStoreRegistry().registerSystem(new PartyMovementSystem());
      this.getCommandRegistry().registerCommand(new PartyCommand());
      this.getCommandRegistry().registerCommand(new PingCommand());
      PartyManager.getInstance().loadParties();
      if (PartyProConfig.getInstance().isSimpleClaimsIntegration()) {
         SimpleClaimsIntegration.getInstance().initialize();
      }

      IdleTracker.getInstance().start();
      this.getEventRegistry().registerGlobal(AddWorldEvent.class, (event) -> {
         World world = event.getWorld();
         WORLDS.put(world.getName(), world);
         this.getLogger().at(Level.INFO).log("PartyPro: Registered world: " + world.getName());
         if (PartyProConfig.getInstance().isCompassTrackingEnabled()) {
            int chunkViewRadius = PartyProConfig.getInstance().getCompassChunkViewRadius();
            PartyCompassMarkerProvider markerProvider = new PartyCompassMarkerProvider(chunkViewRadius);
            world.getWorldMapManager().getMarkerProviders().put("partyCompassMarkers", markerProvider);
            world.getWorldMapManager().getMarkerProviders().put("partyPingMarkers", PartyPingMarkerProvider.INSTANCE);
            this.getLogger().at(Level.INFO).log("PartyPro: Registered compass marker provider for world: " + world.getName());
         }

      });
      this.getEventRegistry().registerGlobal(RemoveWorldEvent.class, (event) -> {
         WORLDS.remove(event.getWorld().getName());
      });
      PacketAdapters.registerInbound(new PingPacketListener());
      this.getEventRegistry().registerGlobal(PlayerChatEvent.class, (event) -> {
         PartyChatEventHandler.handleChatEvent(event);
      });
      this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, (event) -> {
         Player player = (Player)event.getHolder().getComponent(Player.getComponentType());
         PlayerRef playerRef = (PlayerRef)event.getHolder().getComponent(PlayerRef.getComponentType());
         if (player != null && playerRef != null) {
            PartyManager.getInstance().getPlayerNameTracker().setPlayerName(playerRef.getUuid(), player.getDisplayName());
            PartyManager.getInstance().markDirty();
         }

      });
      this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, (event) -> {
         try {
            Player player = event.getPlayer();
            Ref playerEntityRef = event.getPlayerRef();
            World world = player.getWorld();
            if (world == null) {
               return;
            }

            UUID playerId = player.getUuid();
            PartyHealthTracker.setOnline(playerId, true);
            world.execute(() -> {
               try {
                  if (!playerEntityRef.isValid()) {
                     return;
                  }

                  EntityStore entityStore = world.getEntityStore();
                  PlayerRef playerRef = (PlayerRef)entityStore.getStore().getComponent(playerEntityRef, PlayerRef.getComponentType());
                  if (playerRef == null) {
                     return;
                  }

                  this.createHudForPlayer(playerRef);
               } catch (Exception var5) {
               }

            });
         } catch (Exception var6) {
            this.getLogger().at(Level.SEVERE).log("Error setting up party HUD: " + var6.getMessage());
            var6.printStackTrace();
         }

      });
      this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, (event) -> {
         try {
            PlayerRef playerRef = event.getPlayerRef();
            if (playerRef != null) {
               UUID playerId = playerRef.getUuid();
               PartyHudManager.unregisterHud(playerId);
               PartyHealthTracker.setOnline(playerId, false);
               PartyChatManager.getInstance().removePlayer(playerId);
               if (!PartyProConfig.getInstance().isKeepPartyOnDisconnect()) {
                  PartyManager manager = PartyManager.getInstance();
                  PartyInfo party = manager.getPartyFromPlayer(playerId);
                  if (party != null) {
                     if (party.isLeader(playerId)) {
                        if (party.getMembers().length > 0) {
                           UUID newLeader = party.getMembers()[0];
                           party.removeMember(newLeader);
                           party.setLeader(newLeader);
                        } else {
                           manager.disbandParty(party);
                        }
                     } else {
                        party.removeMember(playerId);
                     }
                  }

                  PartyHealthTracker.removePlayer(playerId);
               }
            }
         } catch (Exception var6) {
         }

      });
      this.getLogger().at(Level.INFO).log("PartyPro loaded successfully!");
   }
}
