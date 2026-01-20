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

// Correct UUIDComponent import
import com.hypixel.hytale.server.core.entity.UUIDComponent;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * AutoScoreboardSystem — reflection-safe LuckPerms integration.
 *
 * This class avoids direct references to net.luckperms.api.* to prevent NoClassDefFoundError
 * when the LuckPerms API isn't present on the plugin classpath. If LuckPerms is available
 * at runtime, we use reflection to call the provider, user manager, getUser/loadUser and
 * read primary group.
 */
public final class AutoScoreboardSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<Ref<EntityStore>, ScheduledFuture<?>> updaters = new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>, Long> joinTimestamps = new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>, UUID> refToUuid = new ConcurrentHashMap<>();
    private final PlaytimeStore playtimeStore = new PlaytimeStore();

    private static final long REFRESH_PERIOD_SECONDS = 1L;

    public AutoScoreboardSystem() {
        try {
            playtimeStore.load();
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("Failed to load playtime store");
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

        // Fast-path: try to get UUID from commandBuffer
        UUID playerUuid = null;
        try {
            UUIDComponent uuidComponent = (UUIDComponent) commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComponent != null) playerUuid = uuidComponent.getUuid();
        } catch (Throwable ignored) {}

        long joinedMs = System.currentTimeMillis();
        joinTimestamps.put(ref, joinedMs);
        if (playerUuid != null) refToUuid.put(ref, playerUuid);

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

                    long storedTotal = 0L;
                    UUID uuid = refToUuid.get(ref);
                    if (uuid != null) storedTotal = playtimeStore.getTotalMillis(uuid);

                    hud.setServerName("Darkvale");
                    hud.setGold("Gold: 0");
                    hud.setPlaytime(formatPlaytime(storedTotal + (System.currentTimeMillis() - joinedMs)));
                    hud.setCoords("Coords: 0, 0, 0");
                    hud.setFooter("www.darkvale.com");

                    hm.setCustomHud(pref, hud);
                    hud.show();

                    // debug
                    LOGGER.atInfo().log("[AUTOSCORE] HUD attached for ref=%s uuid=%s", ref, (uuid != null ? uuid.toString() : "null"));

                    // Attempt attach-time LuckPerms resolution via reflection
                    String initialGroup = tryResolveLuckPermsPrimaryGroupReflective(uuid);
                    if (initialGroup != null) {
                        hud.setRank("Rank: " + initialGroup);
                        hud.refresh();
                        LOGGER.atInfo().log("[AUTOSCORE] LP fast-path (reflective) resolved for uuid=%s primary=%s", uuid, initialGroup);
                    } else {
                        // schedule async reflective load (if possible)
                        boolean scheduled = tryScheduleAsyncLuckPermsLoadReflective(uuid, (primary) -> {
                            world.execute(() -> {
                                try {
                                    if (!ref.isValid()) return;
                                    if (hm.getCustomHud() instanceof ScoreboardHud) {
                                        ScoreboardHud sb = (ScoreboardHud) hm.getCustomHud();
                                        sb.setRank("Rank: " + primary);
                                        sb.refresh();
                                        LOGGER.atInfo().log("[AUTOSCORE] LP async reflective loaded for uuid=%s primary=%s", uuid, primary);
                                    }
                                } catch (Throwable t) {
                                    LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Failed to apply LP loaded user reflectively for uuid=%s", uuid);
                                }
                            });
                        });
                        if (!scheduled) {
                            // fallback only if we couldn't schedule async load
                            hud.setRank("Rank: Member");
                            hud.refresh();
                            LOGGER.atInfo().log("[AUTOSCORE] Using fallback rank for uuid=%s (LP not present)", uuid);
                        } else {
                            // leave fallback out for now; async will update soon
                            hud.setRank("Rank: Member");
                            hud.refresh();
                        }
                    }

                    // schedule periodic refresh (coords/playtime + repeated LP fast checks)
                    ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
                        world.execute(() -> {
                            try {
                                if (!ref.isValid()) return;

                                TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
                                if (transform != null) {
                                    Vector3d pos = transform.getPosition();
                                    hud.setCoords(String.format("Coords: %d, %d, %d",
                                            (int) Math.floor(pos.getX()),
                                            (int) Math.floor(pos.getY()),
                                            (int) Math.floor(pos.getZ())));
                                }

                                UUID u = refToUuid.get(ref);
                                long stored = (u != null) ? playtimeStore.getTotalMillis(u) : 0L;
                                long joinedNow = joinTimestamps.getOrDefault(ref, System.currentTimeMillis());
                                hud.setPlaytime(formatPlaytime(stored + (System.currentTimeMillis() - joinedNow)));

                                // attempt quick reflective fast-path each tick (only if LP exists)
                                String pg = tryResolveLuckPermsPrimaryGroupReflective(u);
                                if (pg != null) hud.setRank("Rank: " + pg);

                                hud.refresh();
                            } catch (Throwable t) {
                                LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Periodic refresh failed");
                            }
                        });
                    }, REFRESH_PERIOD_SECONDS, REFRESH_PERIOD_SECONDS, TimeUnit.SECONDS);

                    updaters.put(ref, future);

                } catch (Throwable t) {
                    LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Attach exception");
                }
            });
        }, 350L, TimeUnit.MILLISECONDS);
    }

    /**
     * Try to resolve LuckPerms primary group using reflection.
     * Returns primary group string or null if not available/cached/not present.
     */
    private String tryResolveLuckPermsPrimaryGroupReflective(UUID uuid) {
        if (uuid == null) return null;
        try {
            // Check provider class exists
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            Object lpInstance = getMethod.invoke(null); // static get()
            if (lpInstance == null) return null;

            Method getUserManagerMethod = lpInstance.getClass().getMethod("getUserManager");
            Object userManager = getUserManagerMethod.invoke(lpInstance);
            if (userManager == null) return null;

            Method getUserMethod = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUserMethod.invoke(userManager, uuid);
            if (user == null) return null;

            Method getPrimary = user.getClass().getMethod("getPrimaryGroup");
            Object primary = getPrimary.invoke(user);
            if (primary instanceof String) return (String) primary;
            return null;
        } catch (ClassNotFoundException cnf) {
            // LuckPerms not present; expected in some setups
            return null;
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Reflection LP fast-path error for uuid=%s", uuid);
            return null;
        }
    }

    /**
     * Try to schedule loadUser(uuid) reflectively. If scheduling succeeded returns true.
     * The consumer will be invoked with primary group string when loaded.
     */
    private boolean tryScheduleAsyncLuckPermsLoadReflective(UUID uuid, java.util.function.Consumer<String> onLoaded) {
        if (uuid == null) return false;
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            Object lpInstance = getMethod.invoke(null);
            if (lpInstance == null) return false;

            Method getUserManagerMethod = lpInstance.getClass().getMethod("getUserManager");
            Object userManager = getUserManagerMethod.invoke(lpInstance);
            if (userManager == null) return false;

            Method loadUserMethod = userManager.getClass().getMethod("loadUser", UUID.class);
            Object futureObj = loadUserMethod.invoke(userManager, uuid);
            if (!(futureObj instanceof CompletableFuture)) {
                // Might be some other future type; attempt to handle common case only
                return false;
            }
            @SuppressWarnings("unchecked")
            CompletableFuture<Object> future = (CompletableFuture<Object>) futureObj;
            future.thenAccept(loadedUser -> {
                try {
                    if (loadedUser == null) return;
                    Method getPrimary = loadedUser.getClass().getMethod("getPrimaryGroup");
                    Object primary = getPrimary.invoke(loadedUser);
                    if (primary instanceof String) {
                        onLoaded.accept((String) primary);
                    }
                } catch (Throwable t) {
                    LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Reflection: failed to read primary from loaded user for uuid=%s", uuid);
                }
            }).exceptionally(exc -> {
                LOGGER.atWarning().withCause(exc).log("[AUTOSCORE] Reflection: loadUser failed for uuid=%s", uuid);
                return null;
            });
            return true;
        } catch (ClassNotFoundException cnf) {
            return false;
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Reflection: schedule loadUser error for uuid=%s", uuid);
            return false;
        }
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
            UUID uuid = refToUuid.remove(ref);
            Long joined = joinTimestamps.remove(ref);
            if (uuid != null && joined != null) {
                long sessionElapsed = Math.max(0L, System.currentTimeMillis() - joined);
                playtimeStore.addMillis(uuid, sessionElapsed);
                playtimeStore.save();
            }
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] persist error");
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
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] cleanup error");
        }
    }

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