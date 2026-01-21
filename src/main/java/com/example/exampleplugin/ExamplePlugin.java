package com.example.exampleplugin;

import com.example.exampleplugin.simpledebuginfohud.system.EnableHudOnPlayerAddSystem;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.HytaleServer;

import com.example.exampleplugin.simpledebuginfohud.command.DebugCommand;
import com.example.exampleplugin.simpledebuginfohud.command.ScoreboardCommand;
import com.example.exampleplugin.simpledebuginfohud.data.DebugManager;
import com.example.exampleplugin.simpledebuginfohud.data.ScoreboardManager;
import com.example.exampleplugin.simpledebuginfohud.hud.DebugHudSystem;


import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Main plugin entry. Registers systems and ensures the Debug/Scoreboard HUD is auto-enabled once the
 * player's client and world-thread state are ready.
 */
public class ExamplePlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private DebugManager debugManager;
    private ScoreboardManager scoreboardManager;

    public ExamplePlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Starting %s v%s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        // Component registry proxy - register event-driven systems here
        ComponentRegistryProxy<EntityStore> registry = getEntityStoreRegistry();

        // Example existing registration
        registry.registerSystem(new LifestealSystems.LifestealOnDamage());

        // Managers
        this.debugManager = new DebugManager();
        this.scoreboardManager = new ScoreboardManager();

        // Systems
        this.getEntityStoreRegistry().registerSystem(new EnableHudOnPlayerAddSystem(this.debugManager));

        // Commands
        this.getCommandRegistry().registerCommand(new DebugCommand(this, this.debugManager));
        this.getCommandRegistry().registerCommand(new TestRankCommand());
        this.getCommandRegistry().registerCommand(new DungeonUICommand());
        // Use the ScoreboardCommand that expects (ExamplePlugin, ScoreboardManager)
        this.getCommandRegistry().registerCommand(new ScoreboardCommand(this, this.scoreboardManager));

        this.getLogger().at(Level.INFO).log("Simple Debug Info HUD Plugin loaded successfully!");
        this.getLogger().at(Level.INFO).log("Use /debug to toggle the debug HUD.");

        // Register bleed damage event handler so dagger hits add bleed stacks
        registry.registerSystem(new BleedSystems.BleedOnDamage());

        // Register the bleed ticking system so periodic bleed damage is applied
        this.getEntityStoreRegistry().registerSystem(new BleedSystems.BleedTicking());
    }

    @Override
    protected void start() {
        super.start();
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
    }

    /**
     * PlayerReady handler: schedule a safe attempt to enable the HUD for the player's entity ref once the
     * client/world-thread state is ready. This avoids enabling too early (which caused disconnects).
     */
    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayer().getReference();
        if (ref == null) return;
        Store<EntityStore> store = ref.getStore();
        if (store == null) return;
        World world = ((EntityStore) store.getExternalData()).getWorld();
        if (world == null) return;

        // Kick off the polling enable sequence with attempt 0
        scheduleEnableHud(ref, store, world, 0);
    }

    /**
     * Polling helper: attempts to enable the HUD for the given ref when the world-thread player and packet handler exist.
     *
     * - Attempts up to MAX_ATTEMPTS times, spaced by DELAY_MS.
     * - Uses HytaleServer.SCHEDULED_EXECUTOR to schedule a runnable that executes on the world thread via world.execute(...)
     * - This ensures we don't enable the HUD too early (client loading) which previously caused disconnects.
     */
    private void scheduleEnableHud(Ref<EntityStore> ref, Store<EntityStore> store, World world, int attempt) {
        final int MAX_ATTEMPTS = 12;        // try for ~12 * DELAY_MS (3s if DELAY_MS=250)
        final long DELAY_MS = 250L;

        if (attempt > MAX_ATTEMPTS) {
            // give up silently if attempts exhausted
            return;
        }

        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            // Execute on the world thread for safety with component access and HUD operations
            world.execute(() -> {
                try {
                    if (!ref.isValid()) return;

                    Player p = null;
                    try {
                        p = (Player) store.getComponent(ref, Player.getComponentType());
                    } catch (Throwable ignored) {}

                    if (p == null) {
                        // Not yet available on the world thread — schedule another attempt
                        scheduleEnableHud(ref, store, world, attempt + 1);
                        return;
                    }

                    PlayerRef playerRef = null;
                    try {
                        playerRef = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
                    } catch (Throwable ignored) {}

                    // Check for HUD manager and packet handler readiness
                    boolean hudManagerReady = false;
                    try {
                        hudManagerReady = (p.getHudManager() != null);
                    } catch (Throwable ignored) {}

                    boolean packetHandlerReady = false;
                    try {
                        if (playerRef != null && playerRef.getPacketHandler() != null) packetHandlerReady = true;
                    } catch (Throwable ignored) {}

                    if (!hudManagerReady || !packetHandlerReady) {
                        // Not ready yet — retry
                        scheduleEnableHud(ref, store, world, attempt + 1);
                        return;
                    }

                    // Safe to enable the HUD on this world-thread
                    try {
                        this.debugManager.setDebugEnabled(ref, true);
                    } catch (Throwable t) {
                        // If enabling fails, attempt again a few times
                        scheduleEnableHud(ref, store, world, attempt + 1);
                    }
                } catch (Throwable ignored) {
                    // Swallow and retry until attempts exhausted
                    scheduleEnableHud(ref, store, world, attempt + 1);
                }
            });
        }, DELAY_MS, TimeUnit.MILLISECONDS);
    }
}