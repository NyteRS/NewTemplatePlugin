package com.example.exampleplugin;

import com.example.exampleplugin.custominstance.*;
import com.example.exampleplugin.darkvalehud.command.DebugCommand;
import com.example.exampleplugin.darkvalehud.data.DebugManager;
import com.example.exampleplugin.darkvalehud.data.ScoreboardManager;
import com.example.exampleplugin.darkvalehud.hud.DarkvaleHudSystem;
import com.example.exampleplugin.spawner.*;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Main plugin entry.
 */
public class ExamplePlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private DebugManager debugManager;
    private DungeonManager dungeonManager;
    private ScoreboardManager scoreboardManager;
    private AutoLootPickupSystem autoLootPickupSystem;
    private DeathRecorderSystem deathRecorderSystem;
    // single manager system instance
    private ProximitySpawnSystem spawnManager;

    // ensure we only load spawns once
    private final AtomicBoolean spawnsLoaded = new AtomicBoolean(false);

    public ExamplePlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Starting %s v%s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        this.debugManager = new DebugManager();
        this.scoreboardManager = new ScoreboardManager();
        this.dungeonManager = new DungeonManager();

        this.getEntityStoreRegistry().registerSystem(new DarkvaleHudSystem(this.debugManager));
        this.autoLootPickupSystem = new AutoLootPickupSystem();
        this.deathRecorderSystem = new DeathRecorderSystem(this.autoLootPickupSystem);

        this.getEntityStoreRegistry().registerSystem(this.autoLootPickupSystem);
        this.getEntityStoreRegistry().registerSystem(this.deathRecorderSystem);
        // Commands (existing)
        this.getCommandRegistry().registerCommand(new CustomInstancesNewCommand());
        this.getCommandRegistry().registerCommand(new CustomInstancesCopyCommand());
        this.getCommandRegistry().registerCommand(new CustomInstancesSaveCurrentCommand());
        this.getCommandRegistry().registerCommand(new ListInstancesCommand());
        this.getCommandRegistry().registerCommand(new JoinInstanceCommand());
        this.getCommandRegistry().registerCommand(new DebugCommand(this, this.debugManager));
        this.getCommandRegistry().registerCommand(new SpawnerCreateCommand(this));
        this.getCommandRegistry().registerCommand(new SpawnersCommand(this));
        this.getCommandRegistry().registerCommand(new DeleteSpawnerCommand(this));
        this.getCommandRegistry().registerCommand(new ReloadSpawnersCommand(this));

        // Create and register the single spawn manager system
        this.spawnManager = new ProximitySpawnSystem();
        this.getEntityStoreRegistry().registerSystem(this.spawnManager);

        this.getLogger().at(Level.INFO).log("Simple Debug Info HUD Plugin loaded successfully!");
    }

    @Override
    protected void start() {
        super.start();


        // Auto-pickup fallback for spawned item entities (uses death markers)

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);

        // Load spawn definitions once worlds are fully loaded, then register spawn entries
        getEventRegistry().registerGlobal(AllWorldsLoadedEvent.class, event -> {
            try {
                if (spawnsLoaded.compareAndSet(false, true)) {
                    loadSpawnsOnServer();
                } else {
                    LOGGER.atInfo().log("Spawns already loaded, skipping");
                }
            } catch (Throwable t) {
                LOGGER.atWarning().withCause(t).log("Failed to load/activate spawns from spawns.json");
            }
        });
    }

    /**
     * Register a single spawn definition immediately into the running spawn manager.
     * Returns true if the spawn was registered (or false if spawnManager missing or duplicate).
     */
    public boolean registerSpawn(SpawnDefinition def) {
        if (def == null) return false;
        if (this.spawnManager == null) return false;

        ProximitySpawnSystem.SpawnStrategy strategy;
        if (def.commandTemplate != null && !def.commandTemplate.isBlank()) {
            strategy = new CommandSpawnStrategy(def.commandTemplate, true);
        } else if (def.mob != null && !def.mob.isBlank()) {
            int spawnCount = (def.spawnCount != null) ? def.spawnCount : 1;
            int maxNearby = (def.maxNearby != null) ? def.maxNearby : 6;
            int maxAttempts = (def.maxAttempts != null) ? def.maxAttempts : 8;
            boolean debug = (def.debug != null) ? def.debug : false;
            boolean spawnOnExact = (def.spawnOnExact != null) ? def.spawnOnExact : false;
            strategy = new ProgrammaticSpawnStrategy(def.mob, spawnCount, (int) Math.round(def.radius), maxNearby, maxAttempts, debug, spawnOnExact);
        } else {
            LOGGER.atWarning().log("Spawn definition %s has neither commandTemplate nor mob; skipping", def.id);
            return false;
        }

        return this.spawnManager.addSpawn(def, strategy);
    }

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

    public int reloadSpawns() {
        if (this.spawnManager != null) {
            this.spawnManager.clearSpawns();
        }
        loadSpawnsOnServer();
        return (this.spawnManager != null) ? this.spawnManager.getSpawnCount() : 0;
    }

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
                strategy = new CommandSpawnStrategy(def.commandTemplate, true);
            } else if (def.mob != null && !def.mob.isBlank()) {
                int spawnCount = (def.spawnCount != null) ? def.spawnCount : 1;
                int maxNearby = (def.maxNearby != null) ? def.maxNearby : 6;
                int maxAttempts = (def.maxAttempts != null) ? def.maxAttempts : 8;
                boolean debug = (def.debug != null) ? def.debug : false;
                boolean spawnOnExact = (def.spawnOnExact != null) ? def.spawnOnExact : false;

                strategy = new ProgrammaticSpawnStrategy(def.mob, spawnCount, (int) Math.round(def.radius), maxNearby, maxAttempts, debug, spawnOnExact);
            } else {
                LOGGER.atWarning().log("Spawn definition %s has neither commandTemplate nor mob; skipping", def.id);
                continue;
            }

            boolean added = this.spawnManager.addSpawn(def, strategy);
            if (!added) {
                LOGGER.atInfo().log("Skipped adding spawn %s (already present)", def.id);
            }
        }
    }
}