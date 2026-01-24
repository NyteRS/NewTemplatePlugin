package com.example.exampleplugin;

import com.example.exampleplugin.custominstance.*;
import com.example.exampleplugin.darkvalehud.data.ScoreboardManager;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;

import com.example.exampleplugin.darkvalehud.command.DebugCommand;
import com.example.exampleplugin.darkvalehud.data.DebugManager;
import com.example.exampleplugin.darkvalehud.hud.DarkvaleHudSystem;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Main plugin entry. Registers systems and enables the Debug HUD when the player is ready
 * by listening to PlayerReadyEvent (the engine event fired when client is ready).
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

        // Register player-ready listener. This uses the server event registry and will be invoked
        // when the engine dispatches PlayerReadyEvent for a player (client finished loading).
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
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
            } catch (Throwable ignored) {
                // Fail silently — do not crash world init
            }
        });
    }
}