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

// LuckPerms API (cached fast-path usage; guarded)
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * AutoScoreboardSystem — cached-only LuckPerms integration + permission-node fallback and event-driven updates.
 *
 * - Uses cached getUser(...) only; does not call loadUser(...)
 * - Periodic cheap checks update HUD only when rank text changes
 * - If LuckPerms API or cached data is unavailable, uses permission-node checks on the Player as a fallback
 *   (configurable list of nodes -> rank display).
 * - Registers a LuckPerms UserDataRecalculateEvent listener reflectively (so plugin won't NoClassDefFoundError
 *   if LP API isn't visible to plugin classloader). The reflectively-handled event will update HUD immediately
 *   for affected players.
 */
public final class AutoScoreboardSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<Ref<EntityStore>, ScheduledFuture<?>> updaters = new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>, Long> joinTimestamps = new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>, UUID> refToUuid = new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>, String> lastKnownRank = new ConcurrentHashMap<>();

    private final PlaytimeStore playtimeStore = new PlaytimeStore();

    // Permission node -> friendly rank name (order matters; first match wins)
    private static final LinkedHashMap<String, String> PERMISSION_RANK_MAP = new LinkedHashMap<>();
    static {
        PERMISSION_RANK_MAP.put("group.owner", "Owner");
        PERMISSION_RANK_MAP.put("group.admin", "Admin");
        PERMISSION_RANK_MAP.put("group.moderator", "Moderator");
        PERMISSION_RANK_MAP.put("group.mod", "Moderator");
        PERMISSION_RANK_MAP.put("group.vip", "VIP");
        PERMISSION_RANK_MAP.put("group.member", "Member");
    }

    private static final long REFRESH_PERIOD_SECONDS = 1L;

    public AutoScoreboardSystem() {
        try {
            playtimeStore.load();
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("Failed to load playtime store");
        }

        // Attempt to register LP event listener reflectively (safe if LP classes are missing)
        tryRegisterLuckPermsEventListener();
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

        // Fast-path: try to obtain UUID from commandBuffer
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

                    // Re-check components on world thread and ensure UUID present
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

                    // 1) Try LuckPerms cached fast-path
                    String rankText = tryLuckPermsCachedRank(uuid);
                    if (rankText != null) {
                        lastKnownRank.put(ref, rankText);
                        hud.setRank(rankText);
                        hud.refresh();
                        LOGGER.atInfo().log("[AUTOSCORE] LP cached fast-path resolved for uuid=%s", uuid);
                    } else {
                        // 2) Try permission-node based fallback using Player.hasPermission(...)
                        String permRank = tryPermissionRank(p);
                        if (permRank != null) {
                            rankText = permRank;
                            lastKnownRank.put(ref, rankText);
                            hud.setRank(rankText);
                            hud.refresh();
                            LOGGER.atInfo().log("[AUTOSCORE] Permission fallback applied for ref=%s rank=%s", ref, rankText);
                        } else {
                            // final fallback
                            rankText = "Rank: Member";
                            lastKnownRank.put(ref, rankText);
                            hud.setRank(rankText);
                            hud.refresh();
                            LOGGER.atInfo().log("[AUTOSCORE] Using fallback rank for uuid=%s", uuid);
                        }
                    }

                    // schedule periodic refresh task (coords/playtime + cheap cached LP checks + permission fallback)
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

                                // Try LuckPerms cached fast-path first
                                String newRank = null;
                                try {
                                    newRank = tryLuckPermsCachedRank(u);
                                } catch (Throwable ignored) {}

                                // If LP cached not available, use permission fallback via Player (re-query Player from store)
                                if (newRank == null) {
                                    try {
                                        Player pNow = (Player) store.getComponent(ref, Player.getComponentType());
                                        if (pNow != null) {
                                            newRank = tryPermissionRank(pNow);
                                        }
                                    } catch (Throwable ignored) {}
                                }

                                if (newRank == null) newRank = "Rank: Member";

                                String last = lastKnownRank.get(ref);
                                if (last == null || !last.equals(newRank)) {
                                    lastKnownRank.put(ref, newRank);
                                    hud.setRank(newRank);
                                }

                                hud.refresh();
                            } catch (Throwable t) {
                                LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Periodic refresh failed");
                            }
                        });
                    }, REFRESH_PERIOD_SECONDS, REFRESH_PERIOD_SECONDS, TimeUnit.SECONDS);

                    updaters.put(ref, future);

                } catch (Throwable t) {
                    LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Attach sequence exception");
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

        lastKnownRank.remove(ref);

        try {
            UUID uuid = refToUuid.remove(ref);
            Long joined = joinTimestamps.remove(ref);
            if (uuid != null && joined != null) {
                long sessionElapsed = Math.max(0L, System.currentTimeMillis() - joined);
                playtimeStore.addMillis(uuid, sessionElapsed);
                playtimeStore.save();
            }
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Failed to persist playtime on remove");
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
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Cleanup exception");
        }
    }

    /**
     * Try to obtain rank text from LuckPerms cached user (no loadUser calls).
     * Returns "Rank: X" or null if unavailable.
     */
    private String tryLuckPermsCachedRank(UUID uuid) {
        if (uuid == null) return null;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            if (lp == null) {
                LOGGER.atInfo().log("[AUTOSCORE] LuckPermsProvider.get() returned null");
                return null;
            }
            User user = lp.getUserManager().getUser(uuid);
            if (user == null) return null;
            return buildRankText(user);
        } catch (NoClassDefFoundError ncdf) {
            LOGGER.atWarning().log("[AUTOSCORE] LuckPerms API classes not available to plugin at runtime: %s", ncdf.getClass().getSimpleName());
            return null;
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Error while checking LuckPerms cached user");
            return null;
        }
    }

    /**
     * Permission-node fallback: check Player.hasPermission(node) for known nodes in PERMISSION_RANK_MAP.
     * Returns "Rank: <Name>" or null if none matched or method not available.
     */
    private String tryPermissionRank(Player p) {
        if (p == null) return null;
        for (Map.Entry<String, String> e : PERMISSION_RANK_MAP.entrySet()) {
            final String node = e.getKey();
            final String rankName = e.getValue();
            try {
                // Try direct API first
                try {
                    Method m = p.getClass().getMethod("hasPermission", String.class);
                    Object res = m.invoke(p, node);
                    if (res instanceof Boolean && (Boolean) res) {
                        return "Rank: " + rankName;
                    }
                } catch (NoSuchMethodException ns) {
                    // Some server APIs might expose a different method name; try a couple of alternatives reflectively
                    boolean has = false;
                    try {
                        Method alt = p.getClass().getMethod("hasPermissions", String.class);
                        Object r = alt.invoke(p, node);
                        if (r instanceof Boolean) has = (Boolean) r;
                    } catch (Throwable ignoredAlt) {
                        // final fallback: try a boolean field or skip
                    }
                    if (has) return "Rank: " + rankName;
                }
            } catch (Throwable t) {
                // If any reflection/invoke fails for one node, continue to next node silently
            }
        }
        return null;
    }

    /**
     * Build the rank text to display on the HUD from a LuckPerms User.
     * Prefer cached prefix (if present) then append the primary group for clarity.
     */
    private static String buildRankText(@Nonnull User user) {
        try {
            String primary = user.getPrimaryGroup();
            String prefix = null;
            try {
                if (user.getCachedData() != null && user.getCachedData().getMetaData() != null) {
                    prefix = user.getCachedData().getMetaData().getPrefix();
                }
            } catch (Throwable ignored) { }

            if (prefix != null && !prefix.isBlank()) {
                return "Rank: " + prefix + " " + primary;
            } else {
                return "Rank: " + primary;
            }
        } catch (Throwable t) {
            return "Rank: Member";
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

    // ---------------------------------------------------------------------
    // Reflection-based registration for LuckPerms UserDataRecalculateEvent
    // ---------------------------------------------------------------------
    private void tryRegisterLuckPermsEventListener() {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            Object lpInstance = getMethod.invoke(null);
            if (lpInstance == null) {
                LOGGER.atInfo().log("[AUTOSCORE] LuckPerms provider.get() returned null when registering event listener");
                return;
            }

            Method getEventBus = lpInstance.getClass().getMethod("getEventBus");
            Object eventBus = getEventBus.invoke(lpInstance);
            if (eventBus == null) {
                LOGGER.atInfo().log("[AUTOSCORE] LuckPerms EventBus not available");
                return;
            }

            Class<?> eventClass = Class.forName("net.luckperms.api.event.user.UserDataRecalculateEvent");

            Consumer<Object> consumer = new Consumer<>() {
                @Override
                public void accept(Object event) {
                    try {
                        handleUserDataRecalculateEventReflective(event);
                    } catch (Throwable t) {
                        LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Error in LP event consumer");
                    }
                }
            };

            Method subscribe = eventBus.getClass().getMethod("subscribe", Class.class, Consumer.class);
            subscribe.invoke(eventBus, eventClass, consumer);

            LOGGER.atInfo().log("[AUTOSCORE] Registered LuckPerms UserDataRecalculateEvent listener (reflective)");
        } catch (ClassNotFoundException cnf) {
            LOGGER.atInfo().log("[AUTOSCORE] LuckPerms event classes not present; skipping event registration");
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Failed to register LuckPerms event listener reflectively");
        }
    }

    /**
     * Reflectively inspect the UserDataRecalculateEvent and update HUDs for the affected UUID.
     */
    private void handleUserDataRecalculateEventReflective(Object event) {
        if (event == null) return;
        try {
            Method getUser = event.getClass().getMethod("getUser");
            Object user = getUser.invoke(event);
            if (user == null) return;

            // Resolve UUID (try multiple method names)
            UUID uuidLocal = null;
            try {
                Method getUniqueId = user.getClass().getMethod("getUniqueId");
                Object idObj = getUniqueId.invoke(user);
                if (idObj instanceof UUID) uuidLocal = (UUID) idObj;
            } catch (NoSuchMethodException ns) {
                try {
                    Method alt = user.getClass().getMethod("getUuid");
                    Object idObj = alt.invoke(user);
                    if (idObj instanceof UUID) uuidLocal = (UUID) idObj;
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            // getPrimaryGroup()
            String primaryLocal = null;
            try {
                Method getPrimary = user.getClass().getMethod("getPrimaryGroup");
                Object primObj = getPrimary.invoke(user);
                if (primObj instanceof String) primaryLocal = (String) primObj;
            } catch (Throwable ignored) {}

            // get prefix via cachedData -> getMetaData -> getPrefix
            String prefixLocal = null;
            try {
                Method getCached = user.getClass().getMethod("getCachedData");
                Object cached = getCached.invoke(user);
                if (cached != null) {
                    Method getMeta = cached.getClass().getMethod("getMetaData");
                    Object meta = getMeta.invoke(cached);
                    if (meta != null) {
                        Method getPrefix = meta.getClass().getMethod("getPrefix");
                        Object prefObj = getPrefix.invoke(meta);
                        if (prefObj instanceof String) prefixLocal = (String) prefObj;
                    }
                }
            } catch (Throwable ignored) {}

            if (uuidLocal == null) return;

            final UUID uuid = uuidLocal;
            final String primary = primaryLocal;
            final String prefix = prefixLocal;

            final String rankText;
            if (primary != null) {
                if (prefix != null && !prefix.isBlank()) {
                    rankText = "Rank: " + prefix + " " + primary;
                } else {
                    rankText = "Rank: " + primary;
                }
            } else {
                rankText = "Rank: Member";
            }

            // Find matching attached refs and update their HUDs
            for (Map.Entry<Ref<EntityStore>, UUID> e : refToUuid.entrySet()) {
                final Ref<EntityStore> ref = e.getKey();
                final UUID mappedUuid = e.getValue();
                if (!uuid.equals(mappedUuid)) continue;

                try {
                    final Store<EntityStore> store = ref.getStore();
                    if (store == null) continue;
                    final EntityStore external = (EntityStore) store.getExternalData();
                    if (external == null) continue;
                    final World world = external.getWorld();
                    if (world == null) continue;

                    world.execute(() -> {
                        try {
                            if (!ref.isValid()) return;
                            final Store<EntityStore> s = ref.getStore();
                            if (s == null) return;
                            Player p = (Player) s.getComponent(ref, Player.getComponentType());
                            PlayerRef pref = (PlayerRef) s.getComponent(ref, PlayerRef.getComponentType());
                            if (p == null || pref == null) return;
                            HudManager hm = p.getHudManager();
                            if (hm == null) return;
                            if (hm.getCustomHud() instanceof ScoreboardHud) {
                                ScoreboardHud sb = (ScoreboardHud) hm.getCustomHud();
                                String last = lastKnownRank.get(ref);
                                if (last == null || !last.equals(rankText)) {
                                    lastKnownRank.put(ref, rankText);
                                    sb.setRank(rankText);
                                    sb.refresh();
                                    LOGGER.atInfo().log("[AUTOSCORE] Applied LP event update for uuid=%s newRank=%s", uuid, rankText);
                                }
                            }
                        } catch (Throwable t) {
                            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Failed to apply LP event update for uuid=%s", uuid);
                        }
                    });
                } catch (Throwable ignore) { /* swallow */ }
            }
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Error handling UserDataRecalculateEvent reflectively");
        }
    }
}