package com.example.exampleplugin;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

/**
 * ExamplePlugin - attaches the Scoreboard HUD on PlayerReady using HudManager#setCustomHud.
 *
 * This follows the API you quoted:
 * - CustomUIHud subclass (ScoreboardHud) overrides build(UICommandBuilder) and appends a .ui asset.
 * - Use HudManager#setCustomHud to show the custom HUD.
 * - If you want to hide built-in HUD components (hotbar/health), call HudManager#hideHudComponents.
 */
public class ExamplePlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public ExamplePlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from %s version %s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        // Keep other registrations as you need them
        ComponentRegistryProxy<EntityStore> registry = getEntityStoreRegistry();
        registry.registerSystem(new LifestealSystems.LifestealOnDamage());

        this.getCommandRegistry().registerCommand(new DungeonUICommand());
        this.getCommandRegistry().registerCommand(new ScoreboardCommand());
        this.getCommandRegistry().registerCommand(new ExampleCommand(this.getName(), this.getManifest().getVersion().toString()));
    }

    @Override
    protected void start() {
        super.start();

        // Register PlayerReadyEvent listener so HUD is attached once client is ready
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
    }

    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> playerRef = event.getPlayer().getReference();
        if (playerRef == null) return;

        Store<EntityStore> store = playerRef.getStore();
        if (store == null) return;

        World world = ((EntityStore) store.getExternalData()).getWorld();
        if (world == null) return;

        // Slight delay to ensure assets have arrived on the client. Tune as needed (250 - 500ms recommended).
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            world.execute(() -> {
                try {
                    if (!playerRef.isValid()) return;

                    Player playerComponent = (Player) store.getComponent(playerRef, Player.getComponentType());
                    if (playerComponent == null) return;

                    PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
                    if (playerRefComponent == null) return;

                    var hudManager = playerComponent.getHudManager();
                    if (hudManager == null) return;

                    // Optionally hide base HUD components (hotbar etc.) if you want a clean screen
                    // hudManager.hideHudComponents(playerRefComponent, HudComponent.Hotbar, HudComponent.Health);

                    // Attach and show our CustomUIHud that appends a .ui asset
                    ScoreboardHud hud = new ScoreboardHud(playerRefComponent);
                    hud.setLine1("Money: 0");
                    hud.setLine2("Shards: 0");
                    hud.setLine3("Kills: 0");
                    hud.setLine4("Playtime: 0m");

                    hudManager.setCustomHud(playerRefComponent, hud);
                    hud.show();

                    LOGGER.atInfo().log("Scoreboard HUD attached for player %s", playerRefComponent);
                } catch (Throwable t) {
                    LOGGER.atSevere().withCause(t).log("Failed to attach Scoreboard HUD");
                }
            });
        }, 30000L, TimeUnit.MILLISECONDS);
    }
}