package com.example.exampleplugin;

import com.example.exampleplugin.custominstance.*;
import com.example.exampleplugin.darkvalehud.data.ScoreboardManager;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;

import com.example.exampleplugin.darkvalehud.command.DebugCommand;
import com.example.exampleplugin.darkvalehud.data.DebugManager;
import com.example.exampleplugin.darkvalehud.hud.DarkvaleHudSystem;
import com.example.exampleplugin.spawner.*;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.List;
import java.util.logging.Level;

/**
 * Main plugin entry. Registers systems and enables the Debug HUD when the player is ready
 * by listening to PlayerReadyEvent (the engine event fired when client is ready).
 *
 * Also loads spawn definitions from spawns.json in plugin data folder and registers proximity spawn systems
 * after all worlds have finished loading.
 */
public class ExamplePlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private DebugManager debugManager;    // Dungeon subsystem
    private DungeonManager dungeonManager;
    private ScoreboardManager scoreboardManager;

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


        this.getLogger().at(Level.INFO).log("Simple Debug Info HUD Plugin loaded successfully!");
    }

    @Override
    protected void start() {
        super.start();

        // Register player-ready listener
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);

        // Load spawn definitions once worlds are fully loaded, then register proximity systems
        getEventRegistry().registerGlobal(AllWorldsLoadedEvent.class, event -> {
            try {
                loadSpawnsOnServer();
            } catch (Throwable t) {
                LOGGER.atWarning().withCause(t).log("Failed to load/activate spawns from spawns.json");
            }
        });
    }

    /**
     * Fired when a player becomes "ready" on the server (client finished setup).
     * We enable the debug HUD here for the player's entity reference.
     */
    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayer().getReference();
        if (ref == null) return;
        Store<EntityStore> store = ref.getStore();
        if (store == null) return;

        World world;
        try {
            world = ((EntityStore) store.getExternalData()).getWorld();
        } catch (Throwable t) {
            return;
        }
        if (world == null) return;

        world.execute(() -> {
            try {
                if (!ref.isValid()) return;

                Player player = (Player) store.getComponent(ref, Player.getComponentType());
                if (player == null) return;
                if (player.getHudManager() == null) return;

                this.debugManager.setDebugEnabled(ref, true);
            } catch (Throwable ignored) {}
        });
    }

    /**
     * Loads spawns.json from plugin data folder and registers ProximitySpawnSystem systems for each enabled spawn.
     */
    private void loadSpawnsOnServer() {
        List<SpawnDefinition> defs = SpawnConfigLoader.load(this);
        if (defs == null || defs.isEmpty()) {
            LOGGER.atInfo().log("No spawns defined in spawns.json");
            return;
        }

        for (SpawnDefinition def : defs) {
            if (def == null || !def.enabled) continue;

            ProximitySpawnSystem.SpawnStrategy strategy;
            if (def.commandTemplate != null && !def.commandTemplate.isBlank()) {
                strategy = new ProximitySpawnSystem.CommandSpawnStrategy(def.commandTemplate, true);
            } else if (def.mob != null && !def.mob.isBlank()) {
                strategy = new ProximitySpawnSystem.ReflectionSpawnStrategy(def.mob);
            } else {
                LOGGER.atWarning().log("Spawn definition %s has neither commandTemplate nor mob; skipping", def.id);
                continue;
            }

            // Create system with optional world restriction
            ProximitySpawnSystem sys = new ProximitySpawnSystem(def.x, def.y, def.z, def.radius, strategy, def.cooldownMillis, def.world);
            this.getEntityStoreRegistry().registerSystem(sys);

            LOGGER.atInfo().log("Registered proximity spawn %s at %s,%s,%s radius=%s world=%s", def.id, def.x, def.y, def.z, def.radius, def.world);
        }
    }
}