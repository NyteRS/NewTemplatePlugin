package com.example.exampleplugin;

import com.example.exampleplugin.custominstance.*;
import com.example.exampleplugin.darkvalehud.data.ScoreboardManager;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import com.example.exampleplugin.darkvalehud.command.DebugCommand;
import com.example.exampleplugin.darkvalehud.data.DebugManager;
import com.example.exampleplugin.darkvalehud.hud.DarkvaleHudSystem;

// PartyPro imports
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

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.HashMap;
import java.util.UUID;

/**
 * Main plugin entry. Registers systems and enables the Debug HUD when the player is ready
 * by listening to PlayerReadyEvent (the engine event fired when client is ready).
 */
public class ExamplePlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private DebugManager debugManager;    // Dungeon subsystem
    private DungeonManager dungeonManager;
    private ScoreboardManager scoreboardManager;
    
    // PartyPro world tracking
    public static HashMap<String, World> WORLDS = new HashMap<>();

    public ExamplePlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Starting %s v%s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        this.debugManager = new DebugManager();
        this.scoreboardManager = new ScoreboardManager();

// create the dungeon manager first
        this.dungeonManager = new DungeonManager();

// register systems that depend on managers
        this.getEntityStoreRegistry().registerSystem(new DarkvaleHudSystem(this.debugManager));

        // Commands (existing)
        this.getCommandRegistry().registerCommand(new CustomInstancesNewCommand());
        this.getCommandRegistry().registerCommand(new CustomInstancesCopyCommand());
        this.getCommandRegistry().registerCommand(new CustomInstancesSaveCurrentCommand());
        this.getCommandRegistry().registerCommand(new ListInstancesCommand());
        this.getCommandRegistry().registerCommand(new JoinInstanceCommand());
        this.getCommandRegistry().registerCommand(new DebugCommand(this, this.debugManager));

        // ===== PartyPro Integration =====
        // Initialize PartyPro configurations and managers
        PartyProConfig.load(this.getFile().getParent().resolve("PartyPro").resolve("config.json"));
        LanguageManager.getInstance().initialize(this.getFile().getParent().resolve("PartyPro"));
        PartyStorage.initialize(this.getFile().getParent().resolve("PartyPro").resolve("party_storage.json"));
        PartyHudManager.initialize(this.getFile().getParent().resolve("PartyPro"));
        PartyStatsManager.getInstance().initialize(this.getFile().getParent().resolve("PartyPro"));
        
        // Register PartyPro entity systems
        this.getEntityStoreRegistry().registerSystem(new PartyHealthTracker());
        this.getEntityStoreRegistry().registerSystem(new PartyPvpEventSystem());
        this.getEntityStoreRegistry().registerSystem(new PingBeaconTickingSystem());
        this.getEntityStoreRegistry().registerSystem(new PartyBlockEventSystem());
        this.getEntityStoreRegistry().registerSystem(new PartyPlaceBlockEventSystem());
        this.getEntityStoreRegistry().registerSystem(new PartyCraftEventSystem());
        this.getEntityStoreRegistry().registerSystem(new PartyDamageEventSystem());
        this.getEntityStoreRegistry().registerSystem(new PartyMovementSystem());
        
        // Register PartyPro commands
        this.getCommandRegistry().registerCommand(new PartyCommand());
        this.getCommandRegistry().registerCommand(new PingCommand());
        
        // Load parties from storage
        PartyManager.getInstance().loadParties();
        
        // Initialize SimpleClaimsIntegration if enabled
        if (PartyProConfig.getInstance().isSimpleClaimsIntegration()) {
            SimpleClaimsIntegration.getInstance().initialize();
        }
        
        // Start IdleTracker
        IdleTracker.getInstance().start();
        
        // Register packet listener for ping system
        PacketAdapters.registerInbound(new PingPacketListener());

        this.getLogger().at(Level.INFO).log("Simple Debug Info HUD Plugin loaded successfully!");
        this.getLogger().at(Level.INFO).log("PartyPro integration loaded successfully!");
    }

    @Override
    protected void start() {
        super.start();

        // Register player-ready listener. This uses the server event registry and will be invoked
        // when the engine dispatches PlayerReadyEvent for a player (client finished loading).
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        
        // ===== PartyPro Event Listeners =====
        // World tracking
        getEventRegistry().registerGlobal(AddWorldEvent.class, (event) -> {
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
        
        getEventRegistry().registerGlobal(RemoveWorldEvent.class, (event) -> {
            WORLDS.remove(event.getWorld().getName());
        });
        
        // Party chat handler
        getEventRegistry().registerGlobal(PlayerChatEvent.class, (event) -> {
            PartyChatEventHandler.handleChatEvent(event);
        });
        
        // Player name tracking
        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, (event) -> {
            Player player = (Player) event.getHolder().getComponent(Player.getComponentType());
            PlayerRef playerRef = (PlayerRef) event.getHolder().getComponent(PlayerRef.getComponentType());
            if (player != null && playerRef != null) {
                PartyManager.getInstance().getPlayerNameTracker().setPlayerName(playerRef.getUuid(), player.getDisplayName());
                PartyManager.getInstance().markDirty();
            }
        });
        
        // Player disconnect handler
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, (event) -> {
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
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Fired when a player becomes "ready" on the server (client finished setup).
     * We enable the debug HUD here for the player's entity reference.
     */
    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        // The event provides a Ref<EntityStore> and Player instance.
        Ref<EntityStore> ref = event.getPlayer().getReference(); // or event.getPlayer().getReference()
        if (ref == null) return;
        Store<EntityStore> store = ref.getStore();
        if (store == null) return;

        // Extract world (external data of the store is the EntityStore, from which we can get the world)
        World world;
        try {
            world = ((EntityStore) store.getExternalData()).getWorld();
        } catch (Throwable t) {
            return;
        }
        if (world == null) return;

        // Schedule enabling on the world thread to be safe with other world operations.
        world.execute(() -> {
            try {
                if (!ref.isValid()) return;

                // Optional: sanity-check Player + HudManager presence
                Player player = (Player) store.getComponent(ref, Player.getComponentType());
                if (player == null) return;
                if (player.getHudManager() == null) return;

                // Enable the debug HUD for this player's entity Ref. DarkvaleHudSystem reads this flag and
                // will attach/show the HUD in its ticking logic.
                this.debugManager.setDebugEnabled(ref, true);
                
                // ===== PartyPro HUD Creation =====
                UUID playerId = player.getUuid();
                PartyHealthTracker.setOnline(playerId, true);
                
                EntityStore entityStore = world.getEntityStore();
                PlayerRef playerRef = (PlayerRef) entityStore.getStore().getComponent(ref, PlayerRef.getComponentType());
                if (playerRef != null) {
                    createPartyHudForPlayer(playerRef, player);
                }
            } catch (Throwable ignored) {
                // Fail silently — do not crash world init
            }
        });
    }
    
    /**
     * Creates and registers a party HUD for the given player.
     */
    private void createPartyHudForPlayer(PlayerRef playerRef, Player player) {
        if (playerRef != null && playerRef.isValid()) {
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