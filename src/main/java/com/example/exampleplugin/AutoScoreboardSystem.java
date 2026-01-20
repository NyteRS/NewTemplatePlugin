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
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.HytaleServer;

// Correct UUIDComponent import (server core entity package)
import com.hypixel.hytale.server.core.entity.UUIDComponent;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * AutoScoreboardSystem
 *
 * - Attaches ScoreboardHud after a short delay and schedules periodic refreshes.
 * - Playtime is persisted across sessions using PlaytimeStore.
 * - Coords update frequency: once per second (tunable).
 * - Integrates with LuckPerms: sets Rank: <primaryGroup> using the LP API.
 */
public final class AutoScoreboardSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Scheduled updater tasks per player ref
    private final Map<Ref<EntityStore>, ScheduledFuture<?>> updaters = new ConcurrentHashMap<>();

    // Join timestamps (ms) used to compute session playtime
    private final Map<Ref<EntityStore>, Long> joinTimestamps = new ConcurrentHashMap<>();

    // Mapping from ref -> player UUID for persistence & bookkeeping
    private final Map<Ref<EntityStore>, UUID> refToUuid = new ConcurrentHashMap<>();

    // Playtime persistence store
    private final PlaytimeStore playtimeStore = new PlaytimeStore();

    // Refresh freq in seconds (1s for responsive coords)
    private static final long REFRESH_PERIOD_SECONDS = 1L;

    public AutoScoreboardSystem() {
        // Load persisted playtimes at construction so store is ready when players join
        try {
            playtimeStore.load();
        } catch (Throwable t) {
            // best-effort
        }
    }

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

        // Attempt to get player UUID (persistent key) from commandBuffer fast path
        UUID playerUuid = null;
        try {
            UUIDComponent uuidComponent = (UUIDComponent) commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComponent != null) playerUuid = uuidComponent.getUuid();
        } catch (Throwable ignored) {}

        // Record join timestamp immediately (used for session playtime)
        long joinedMs = System.currentTimeMillis();
        joinTimestamps.put(ref, joinedMs);
        if (playerUuid != null) refToUuid.put(ref, playerUuid);

        // We schedule a short delay, then execute on the world thread to attach the HUD safely
        EntityStore external = (EntityStore) store.getExternalData();
        World world = external.getWorld();
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            world.execute(() -> {
                try {
                    if (!ref.isValid()) return;

                    // Re-check components on world thread and ensure UUID is present in refToUuid
                    Player p = (Player) store.getComponent(ref, Player.getComponentType());
                    PlayerRef pref = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
                    if (p == null || pref == null) return;

                    // If we didn't obtain UUID earlier, try to read it from the store on the world thread
                    if (!refToUuid.containsKey(ref)) {
                        try {
                            UUIDComponent storeUuid = (UUIDComponent) store.getComponent(ref, UUIDComponent.getComponentType());
                            if (storeUuid != null) refToUuid.put(ref, storeUuid.getUuid());
                        } catch (Throwable ignored) {}
                    }

                    HudManager hm = p.getHudManager();
                    if (hm == null) return;
                    if (hm.getCustomHud() instanceof ScoreboardHud) return;

                    ScoreboardHud hud = new ScoreboardHud(pref);

                    // Load persisted total playtime and show combined initial playtime
                    long storedTotal = 0L;
                    UUID uuid = refToUuid.get(ref);
                    if (uuid != null) {
                        storedTotal = playtimeStore.getTotalMillis(uuid);
                    }

                    hud.setServerName("Darkvale");
                    hud.setGold("Gold: 0");
                    hud.setRank("Rank: Member"); // fallback until LP resolves
                    hud.setPlaytime(formatPlaytime(storedTotal + (System.currentTimeMillis() - joinedMs)));
                    hud.setCoords("Coords: 0, 0, 0");
                    hud.setFooter("www.darkvale.com");

                    hm.setCustomHud(pref, hud);
                    hud.show();

                    // schedule refresh task (every REFRESH_PERIOD_SECONDS seconds)
                    ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
                        world.execute(() -> {
                            try {
                                if (!ref.isValid()) return;

                                // Update coords from TransformComponent
                                TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
                                if (transform != null) {
                                    Vector3d pos = transform.getPosition();
                                    int x = (int) Math.floor(pos.getX());
                                    int y = (int) Math.floor(pos.getY());
                                    int z = (int) Math.floor(pos.getZ());
                                    hud.setCoords(String.format("Coords: %d, %d, %d", x, y, z));
                                }

                                // Update playtime using stored total + session elapsed
                                UUID u = refToUuid.get(ref);
                                long stored = (u != null) ? playtimeStore.getTotalMillis(u) : 0L;
                                long joinedNow = joinTimestamps.getOrDefault(ref, System.currentTimeMillis());
                                hud.setPlaytime(formatPlaytime(stored + (System.currentTimeMillis() - joinedNow)));

                                // Try LuckPerms: fast-path cached user -> set rank; if not loaded, load async.
                                if (u != null) {
                                    try {
                                        LuckPerms lp = LuckPermsProvider.get();
                                        if (lp != null) {
                                            User user = lp.getUserManager().getUser(u);
                                            if (user != null) {
                                                String primary = user.getPrimaryGroup();
                                                hud.setRank("Rank: " + primary);
                                            } else {
                                                // Asynchronously load the user and update when ready
                                                lp.getUserManager().loadUser(u).thenAccept(loadedUser -> {
                                                    if (loadedUser == null) return;
                                                    String primary = loadedUser.getPrimaryGroup();
                                                    world.execute(() -> {
                                                        try {
                                                            if (!ref.isValid()) return;
                                                            if (hm.getCustomHud() instanceof ScoreboardHud) {
                                                                ScoreboardHud sb = (ScoreboardHud) hm.getCustomHud();
                                                                sb.setRank("Rank: " + primary);
                                                                sb.refresh();
                                                            }
                                                        } catch (Throwable t) {
                                                            LOGGER.atWarning().withCause(t).log("Failed to apply loaded LuckPerms user to HUD");
                                                        }
                                                    });
                                                }).exceptionally(exc -> {
                                                    // swallow exceptions
                                                    return null;
                                                });
                                            }
                                        }
                                    } catch (Throwable ignoreLp) {
                                        // LuckPerms may not be available on runtime classpath; ignore
                                    }
                                }

                                // Always refresh the HUD each tick so coords/playtime always update,
                                // even if LuckPerms is missing or throwing.
                                hud.refresh();

                            } catch (Throwable t) {
                                LOGGER.atWarning().withCause(t).log("AutoScoreboard refresh failed");
                            }
                        });
                    }, REFRESH_PERIOD_SECONDS, REFRESH_PERIOD_SECONDS, TimeUnit.SECONDS);

                    updaters.put(ref, future);

                } catch (Throwable t) {
                    LOGGER.atWarning().withCause(t).log("AutoScoreboard attach failed");
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
        // Cancel scheduled updater if present
        ScheduledFuture<?> fut = updaters.remove(ref);
        if (fut != null) fut.cancel(false);

        // Persist playtime: add session elapsed to stored total and save
        try {
            UUID uuid = refToUuid.remove(ref);
            Long joined = joinTimestamps.remove(ref);
            if (uuid != null && joined != null) {
                long sessionElapsed = Math.max(0L, System.currentTimeMillis() - joined);
                playtimeStore.addMillis(uuid, sessionElapsed);
                // persist immediately to disk
                playtimeStore.save();
            }
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("Failed to persist playtime on remove");
        }

        try {
            Player p = (Player) store.getComponent(ref, Player.getComponentType());
            PlayerRef pref = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
            if (p != null && pref != null) {
                HudManager hm = p.getHudManager();
                if (hm != null && hm.getCustomHud() instanceof ScoreboardHud) {
                    hm.setCustomHud(pref, null);
                }
            }
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("AutoScoreboard cleanup failed");
        }
    }

    /**
     * Format playtime string from milliseconds (total across sessions)
     * into "Playtime: Xh Ym" or "Playtime: Xm".
     */
    private static String formatPlaytime(long totalMillis) {
        long totalSeconds = totalMillis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;

        if (hours > 0) {
            return String.format("Playtime: %dh %dm", hours, minutes);
        } else {
            return String.format("Playtime: %dm", Math.max(0L, minutes));
        }
    }
}