package com.example.exampleplugin;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.HytaleServer;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * AutoScoreboardSystem
 * - Attaches ScoreboardHud and periodically refreshes it with placeholder data.
 * - Cancels scheduled updates on removal and clears the HUD.
 */
public final class AutoScoreboardSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<Ref<EntityStore>, ScheduledFuture<?>> updaters = new ConcurrentHashMap<>();

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
        ComponentType<EntityStore, Player> playerType = Player.getComponentType();
        if (playerRefType == null || playerType == null) return Query.any();
        @SuppressWarnings("unchecked")
        Query<EntityStore> q = (Query<EntityStore>) Query.and(new Query[] { (Query) playerRefType, (Query) playerType });
        return q;
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Player playerComponent = (Player) commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) return;
        PlayerRef playerRefComponent = (PlayerRef) commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRefComponent == null) return;

        HudManager hudManager = playerComponent.getHudManager();
        if (hudManager == null) return;
        if (hudManager.getCustomHud() instanceof ScoreboardHud) return;

        // Attach after a short delay on the world's thread to avoid asset-race client crashes
        EntityStore external = (EntityStore) store.getExternalData();
        World world = external.getWorld();
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            world.execute(() -> {
                try {
                    if (!ref.isValid()) return;
                    // Re-check components on world thread
                    Player p = (Player) store.getComponent(ref, Player.getComponentType());
                    PlayerRef pref = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
                    if (p == null || pref == null) return;
                    HudManager hm = p.getHudManager();
                    if (hm == null) return;
                    if (hm.getCustomHud() instanceof ScoreboardHud) return;

                    ScoreboardHud hud = new ScoreboardHud(pref);
                    hud.setServerName("MyServer");
                    hud.setGold("Gold: 0");
                    hud.setRank("Rank: Member");
                    hud.setPlaytime("Playtime: 0m");
                    hud.setCoords("Coords: 0, 0, 0");
                    hud.setFooter("www.example.server");

                    hm.setCustomHud(pref, hud);
                    hud.show();

                    // Periodic refresh task (example: every 5s)
                    ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
                        world.execute(() -> {
                            try {
                                if (!ref.isValid()) return;
                                // Update placeholder values here, replace with real lookups later
                                hud.setGold("Gold: 0");
                                hud.setRank("Rank: Member");
                                hud.setPlaytime("Playtime: 0m");
                                hud.setCoords("Coords: 0, 0, 0");
                                hud.refresh();
                            } catch (Throwable t) {
                                LOGGER.atWarning().withCause(t).log("AutoScoreboard refresh failed");
                            }
                        });
                    }, 5L, 5L, TimeUnit.SECONDS);

                    updaters.put(ref, future);

                } catch (Throwable t) {
                    LOGGER.atWarning().withCause(t).log("AutoScoreboardSystem attach failed");
                }
            });
        }, 350L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        ScheduledFuture<?> fut = updaters.remove(ref);
        if (fut != null) fut.cancel(false);

        try {
            Player player = (Player) store.getComponent(ref, Player.getComponentType());
            PlayerRef pref = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null && pref != null) {
                HudManager hm = player.getHudManager();
                if (hm != null && hm.getCustomHud() instanceof ScoreboardHud) {
                    hm.setCustomHud(pref, null);
                }
            }
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("AutoScoreboardSystem cleanup failed");
        }
    }
}