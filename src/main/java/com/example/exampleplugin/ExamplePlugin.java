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

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

/**
 * ExamplePlugin - attaches the Scoreboard HUD on PlayerReady using HudManager#setCustomHud
 */
public class ExamplePlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public ExamplePlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from %s version %s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        ComponentRegistryProxy<EntityStore> registry = getEntityStoreRegistry();
        registry.registerSystem(new LifestealSystems.LifestealOnDamage());

        this.getCommandRegistry().registerCommand(new DungeonUICommand());
        this.getCommandRegistry().registerCommand(new ScoreboardCommand());
        this.getCommandRegistry().registerCommand(new ExampleCommand(this.getName(), this.getManifest().getVersion().toString()));

        // register autoscoreboard if desired:
        this.getEntityStoreRegistry().registerSystem(new AutoScoreboardSystem());
    }

    @Override
    protected void start() {
        super.start();
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
    }

    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> playerRef = event.getPlayer().getReference();
        if (playerRef == null) return;
        Store<EntityStore> store = playerRef.getStore();
        if (store == null) return;
        World world = ((EntityStore) store.getExternalData()).getWorld();
        if (world == null) return;

        // small delay to let client process assetpack
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            world.execute(() -> {
                try {
                    if (!playerRef.isValid()) return;
                    Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
                    PlayerRef pref = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
                    if (player == null || pref == null) return;

                    var hudManager = player.getHudManager();
                    if (hudManager == null) return;

                    // Attach scoreboard HUD with new API
                    ScoreboardHud hud = new ScoreboardHud(pref);
                    hud.setServerName("ExampleSMP");
                    hud.setGold("Gold: 0");
                    hud.setRank("Rank: Member");
                    hud.setPlaytime("Playtime: 0m");
                    hud.setCoords("Coords: 0, 0, 0");
                    hud.setFooter("www.example.server");

                    hudManager.setCustomHud(pref, hud);
                    hud.show();

                } catch (Throwable t) {
                    LOGGER.atSevere().withCause(t).log("PlayerReady attach failed");
                }
            });
        }, 350L, TimeUnit.MILLISECONDS);
    }
}