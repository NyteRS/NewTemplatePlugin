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
 * Main plugin entry. Registers systems (including BleedSystems) and attaches scoreboard on PlayerReady.
 */
public class ExamplePlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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

        // Register bleed damage event handler so dagger hits add bleed stacks
        registry.registerSystem(new BleedSystems.BleedOnDamage());

        // Commands
        this.getCommandRegistry().registerCommand(new TestRankCommand());
        this.getCommandRegistry().registerCommand(new DungeonUICommand());
        this.getCommandRegistry().registerCommand(new ScoreboardCommand());

        // Register persistent / ticking systems on the entity-store registry (per-world)
        // AutoScoreboardSystem handles HUD updates
        this.getEntityStoreRegistry().registerSystem(new AutoScoreboardSystem());

        // Register the bleed ticking system so periodic bleed damage is applied
        this.getEntityStoreRegistry().registerSystem(new BleedSystems.BleedTicking());
    }

    @Override
    protected void start() {
        super.start();
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
    }

    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayer().getReference();
        if (ref == null) return;
        Store<EntityStore> store = ref.getStore();
        if (store == null) return;
        World world = ((EntityStore) store.getExternalData()).getWorld();
        if (world == null) return;

        // Small delay to let client process assets
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            world.execute(() -> {
                try {
                    if (!ref.isValid()) return;

                    Player p = (Player) store.getComponent(ref, Player.getComponentType());
                    PlayerRef pref = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
                    if (p == null || pref == null) return;

                    var hm = p.getHudManager();
                    if (hm == null) return;

                    // Attach a scoreboard hud on join (this will not override if one exists)
                    if (!(hm.getCustomHud() instanceof ScoreboardHud)) {
                        ScoreboardHud hud = new ScoreboardHud(pref);
                        // ScoreboardHud defaults already set to Darkvale/www.darkvale.com
                        hud.setGold("Gold: 0");
                        hud.setRank("Rank: Member");
                        hud.setPlaytime("Playtime: 0m");
                        hud.setCoords("Coords: 0, 0, 0");

                        hm.setCustomHud(pref, hud);
                        hud.show();
                    }
                } catch (Throwable t) {
                    LOGGER.atSevere().withCause(t).log("PlayerReady attach failed");
                }
            });
        }, 350L, TimeUnit.MILLISECONDS);
    }
}