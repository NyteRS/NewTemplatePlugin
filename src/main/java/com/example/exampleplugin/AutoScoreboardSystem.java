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
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.HytaleServer;

import com.hypixel.hytale.server.core.entity.UUIDComponent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * AutoScoreboardSystem — final improved HUD attach that waits for client readiness and world alive.
 *
 * Important attach conditions (all checked just before showing UI):
 *  - ref.isValid() must be true
 *  - world.isAlive() must be true
 *  - player.isWaitingForClientReady() must be false (or client-ready-for-chunks future completed)
 *
 * When polling/waiting we abort if the world becomes not alive or the player ref invalid.
 */
public final class AutoScoreboardSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<Ref<EntityStore>, ScheduledFuture<?>> updaters = new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>, Long> joinTimestamps = new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>, UUID> refToUuid = new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>, String> lastKnownRank = new ConcurrentHashMap<>();

    private final PlaytimeStore playtimeStore = new PlaytimeStore();

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
    private static final DecimalFormat BALANCE_FMT = new DecimalFormat("#,##0.##");

    // Client readiness waiting config
    private static final long CLIENT_READY_POLL_MS = 200L;
    private static final long CLIENT_READY_MAX_WAIT_MS = 15_000L;
    private static final long CLIENT_READY_CHUNKS_WAIT_MAX_MS = 15_000L;

    public AutoScoreboardSystem() {
        try {
            playtimeStore.load();
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("Failed to load playtime store");
        }
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

        world.execute(() -> {
            try {
                if (!ref.isValid()) return;

                Player p = (Player) store.getComponent(ref, Player.getComponentType());
                PlayerRef pref = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
                if (p == null || pref == null) return;

                // Make sure world is alive before we even start waiting for client-ready
                if (!world.isAlive()) {
                    LOGGER.atWarning().log("[AUTOSCORE] World %s not alive when player %s added; skipping HUD attach", world, pref.getUsername());
                    return;
                }

                // Prefer to wait for PacketHandler client-ready-for-chunks future if present
                PacketHandler ph = pref.getPacketHandler();
                CompletableFuture<Void> chunksFuture = null;
                try {
                    chunksFuture = (ph != null) ? ph.getClientReadyForChunksFuture() : null;
                } catch (Throwable ignore) { chunksFuture = null; }

                if (chunksFuture != null && !chunksFuture.isDone()) {
                    LOGGER.atInfo().log("[AUTOSCORE] Waiting for client-ready-for-chunks for player=%s", pref.getUsername());
                    final ScheduledFuture<?>[] timeoutRef = new ScheduledFuture<?>[1];

                    // schedule timeout fallback
                    timeoutRef[0] = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                        world.execute(() -> {
                            try {
                                if (!ref.isValid()) return;
                                if (!world.isAlive()) {
                                    LOGGER.atWarning().log("[AUTOSCORE] World not alive when chunks wait timed out for player=%s; aborting HUD attach", pref.getUsername());
                                    return;
                                }
                                LOGGER.atWarning().log("[AUTOSCORE] client-ready-for-chunks timed out for player=%s; proceeding with HUD attach", pref.getUsername());
                                attachHudNow(ref, store, joinedMs, world);
                            } catch (Throwable t) {
                                LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Error after chunks wait timeout for ref=%s", ref);
                            }
                        });
                    }, CLIENT_READY_CHUNKS_WAIT_MAX_MS, TimeUnit.MILLISECONDS);

                    // when client-ready-for-chunks completes, attach on world thread (and cancel timeout)
                    chunksFuture.whenComplete((v, ex) -> {
                        try {
                            if (timeoutRef[0] != null) timeoutRef[0].cancel(false);
                        } catch (Throwable ignored) {}
                        world.execute(() -> {
                            try {
                                if (!ref.isValid()) return;
                                if (!world.isAlive()) {
                                    LOGGER.atWarning().log("[AUTOSCORE] World not alive when chunks future completed for player=%s; aborting HUD attach", pref.getUsername());
                                    return;
                                }
                                if (ex != null) {
                                    LOGGER.atWarning().withCause(ex).log("[AUTOSCORE] client-ready-for-chunks completed with exception for player=%s; proceeding", pref.getUsername());
                                } else {
                                    LOGGER.atInfo().log("[AUTOSCORE] client-ready-for-chunks completed for player=%s; attaching HUD", pref.getUsername());
                                }
                                attachHudNow(ref, store, joinedMs, world);
                            } catch (Throwable t) {
                                LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Error attaching HUD after client-ready-for-chunks for ref=%s", ref);
                            }
                        });
                    });
                    return;
                }

                // If chunk-future not present, fall back to checking Player.isWaitingForClientReady()
                if (p.isWaitingForClientReady()) {
                    LOGGER.atInfo().log("[AUTOSCORE] Player %s waiting for gameplay-ready; scheduling poll", pref.getUsername());
                    final long startWait = System.currentTimeMillis();

                    ScheduledFuture<?> poll = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
                        world.execute(() -> {
                            try {
                                if (!ref.isValid()) {
                                    ScheduledFuture<?> pf = updaters.remove(ref);
                                    if (pf != null) pf.cancel(false);
                                    return;
                                }
                                if (!world.isAlive()) {
                                    LOGGER.atWarning().log("[AUTOSCORE] World not alive while polling client-ready for player=%s; aborting poll", pref.getUsername());
                                    ScheduledFuture<?> pf = updaters.remove(ref);
                                    if (pf != null) pf.cancel(false);
                                    return;
                                }
                                Player pp = (Player) store.getComponent(ref, Player.getComponentType());
                                if (pp == null) {
                                    ScheduledFuture<?> pf = updaters.remove(ref);
                                    if (pf != null) pf.cancel(false);
                                    return;
                                }
                                if (!pp.isWaitingForClientReady()) {
                                    try {
                                        attachHudNow(ref, store, joinedMs, world);
                                    } finally {
                                        ScheduledFuture<?> pf = updaters.remove(ref);
                                        if (pf != null) pf.cancel(false);
                                    }
                                    return;
                                }
                                if (System.currentTimeMillis() - startWait > CLIENT_READY_MAX_WAIT_MS) {
                                    LOGGER.atWarning().log("[AUTOSCORE] client-ready (gameplay) timed out for player=%s; attaching HUD anyway", pref.getUsername());
                                    try {
                                        attachHudNow(ref, store, joinedMs, world);
                                    } finally {
                                        ScheduledFuture<?> pf = updaters.remove(ref);
                                        if (pf != null) pf.cancel(false);
                                    }
                                }
                            } catch (Throwable t) {
                                LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Error during client-ready poll for ref=%s", ref);
                            }
                        });
                    }, CLIENT_READY_POLL_MS, CLIENT_READY_POLL_MS, TimeUnit.MILLISECONDS);

                    updaters.put(ref, poll);
                    return;
                }

                // Otherwise attach immediately (final check of world.isAlive)
                if (!world.isAlive()) {
                    LOGGER.atWarning().log("[AUTOSCORE] World not alive at immediate attach for player=%s; skipping", pref.getUsername());
                    return;
                }
                attachHudNow(ref, store, joinedMs, world);

            } catch (Throwable t) {
                LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Error in onEntityAdded world task");
            }
        });
    }

    /**
     * Perform the actual HUD attach and schedule periodic updates.
     * Must be called on world thread.
     */
    private void attachHudNow(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, long joinTimestampMs, @Nonnull World world) {
        try {
            if (!ref.isValid()) return;
            if (!world.isAlive()) {
                LOGGER.atWarning().log("[AUTOSCORE] Abort attach: world is not alive for ref=%s", ref);
                return;
            }

            Player p = (Player) store.getComponent(ref, Player.getComponentType());
            PlayerRef pref = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
            if (p == null || pref == null) return;

            // final ready-for-gameplay check
            if (p.isWaitingForClientReady()) {
                LOGGER.atInfo().log("[AUTOSCORE] Deferred attach: player still waiting for client ready for ref=%s", ref);
                return;
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
            hud.setPlaytime(formatPlaytime(storedTotal + (System.currentTimeMillis() - joinTimestampMs)));
            hud.setCoords("Coords: 0, 0, 0");
            hud.setFooter("www.darkvale.com");

            // attempt to attach
            boolean attached = false;
            try {
                hm.setCustomHud(pref, hud);
                hud.show();
                attached = true;
            } catch (CancellationException ce) {
                LOGGER.atWarning().withCause(ce).log("[AUTOSCORE] HUD attach canceled for ref=%s; aborting", ref);
                attached = false;
            } catch (IllegalStateException ise) {
                LOGGER.atWarning().withCause(ise).log("[AUTOSCORE] HUD attach illegal state for ref=%s; aborting", ref);
                attached = false;
            } catch (Throwable t) {
                LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Exception showing HUD during attach for ref=%s", ref);
                attached = false;
            }

            if (!attached) return;

            // initial rank resolution (cached LuckPerms or permissions fallback)
            String rankText = null;
            if (uuid != null) {
                try {
                    LuckPerms lp = LuckPermsProvider.get();
                    if (lp != null) {
                        User user = lp.getUserManager().getUser(uuid);
                        if (user != null) {
                            rankText = buildRankText(user);
                            LOGGER.atInfo().log("[AUTOSCORE] LP cached fast-path resolved for uuid=%s primary=%s", uuid, user.getPrimaryGroup());
                        }
                    }
                } catch (Throwable ignore) {}
            }
            if (rankText == null) {
                String permRank = tryPermissionRank(p);
                rankText = (permRank != null) ? permRank : "Rank: Member";
            }

            lastKnownRank.put(ref, rankText);
            hud.setRank(rankText);

            try {
                hud.refresh();
            } catch (CancellationException ce) {
                LOGGER.atWarning().withCause(ce).log("[AUTOSCORE] HUD refresh canceled immediately after attach for ref=%s; aborting periodic updates", ref);
                try { hm.setCustomHud(pref, null); } catch (Throwable ignored) {}
                return;
            } catch (IllegalStateException ise) {
                LOGGER.atWarning().withCause(ise).log("[AUTOSCORE] HUD refresh illegal state after attach for ref=%s; aborting periodic updates", ref);
                try { hm.setCustomHud(pref, null); } catch (Throwable ignored) {}
                return;
            } catch (Throwable t) {
                LOGGER.atWarning().withCause(t).log("[AUTOSCORE] HUD refresh error after attach for ref=%s", ref);
            }

            // cancel any existing scheduled future for this ref (poll or previous updater)
            ScheduledFuture<?> existing = updaters.remove(ref);
            if (existing != null) existing.cancel(false);

            ScheduledFuture<?> periodic = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
                world.execute(() -> {
                    try {
                        if (!ref.isValid()) return;
                        if (!world.isAlive()) {
                            ScheduledFuture<?> f = updaters.remove(ref);
                            if (f != null) f.cancel(false);
                            return;
                        }

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

                        if (u != null) {
                            try {
                                LuckPerms lp = LuckPermsProvider.get();
                                if (lp != null) {
                                    User user = lp.getUserManager().getUser(u);
                                    if (user != null) {
                                        String newRank = buildRankText(user);
                                        String last = lastKnownRank.get(ref);
                                        if (last == null || !last.equals(newRank)) {
                                            lastKnownRank.put(ref, newRank);
                                            hud.setRank(newRank);
                                        }
                                    }
                                }
                            } catch (Throwable ignore) {}
                        }

                        try {
                            hud.refresh();
                        } catch (CancellationException ce) {
                            LOGGER.atWarning().withCause(ce).log("[AUTOSCORE] HUD refresh canceled during periodic tick for ref=%s; cancelling updater", ref);
                            ScheduledFuture<?> f = updaters.remove(ref);
                            if (f != null) f.cancel(false);
                        } catch (IllegalStateException ise) {
                            LOGGER.atWarning().withCause(ise).log("[AUTOSCORE] HUD refresh illegal state during periodic tick for ref=%s; cancelling updater", ref);
                            ScheduledFuture<?> f = updaters.remove(ref);
                            if (f != null) f.cancel(false);
                        } catch (Throwable t) {
                            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Periodic HUD refresh error for ref=%s", ref);
                        }
                    } catch (Throwable t) {
                        LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Periodic refresh failed");
                    }
                });
            }, REFRESH_PERIOD_SECONDS, REFRESH_PERIOD_SECONDS, TimeUnit.SECONDS);

            updaters.put(ref, periodic);

        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] attachHud unexpected error for ref=%s", ref);
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

    private String tryPermissionRank(Player p) {
        if (p == null) return null;
        for (Map.Entry<String, String> e : PERMISSION_RANK_MAP.entrySet()) {
            final String node = e.getKey();
            final String rankName = e.getValue();
            try {
                try {
                    Method m = p.getClass().getMethod("hasPermission", String.class);
                    Object res = m.invoke(p, node);
                    if (res instanceof Boolean && (Boolean) res) {
                        return "Rank: " + rankName;
                    }
                } catch (NoSuchMethodException ns) {
                    try {
                        Method alt = p.getClass().getMethod("hasPermissions", String.class);
                        Object r = alt.invoke(p, node);
                        if (r instanceof Boolean && (Boolean) r) return "Rank: " + rankName;
                    } catch (Throwable ignoredAlt) {}
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

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

            boolean registered = false;

            try {
                Method subscribeFunc = eventBus.getClass().getMethod("subscribe", Class.class, Function.class);
                Function<Object, CompletableFuture<?>> func = new Function<>() {
                    @Override
                    public CompletableFuture<?> apply(Object event) {
                        try {
                            handleUserDataRecalculateEventReflective(event);
                        } catch (Throwable t) {
                            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Error in LP event handler (function)");
                        }
                        return CompletableFuture.completedFuture(null);
                    }
                };
                subscribeFunc.invoke(eventBus, eventClass, func);
                registered = true;
                LOGGER.atInfo().log("[AUTOSCORE] Registered LuckPerms UserDataRecalculateEvent listener (function/reflective)");
            } catch (NoSuchMethodException ns) { }

            if (!registered) {
                try {
                    Method subscribeConsumer = eventBus.getClass().getMethod("subscribe", Class.class, Consumer.class);
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
                    subscribeConsumer.invoke(eventBus, eventClass, consumer);
                    registered = true;
                    LOGGER.atInfo().log("[AUTOSCORE] Registered LuckPerms UserDataRecalculateEvent listener (consumer/reflective)");
                } catch (NoSuchMethodException ns2) { }
            }

            if (!registered) {
                LOGGER.atInfo().log("[AUTOSCORE] LuckPerms EventBus subscribe method with expected signatures not found; skipping event registration");
            }

        } catch (ClassNotFoundException cnf) {
            LOGGER.atInfo().log("[AUTOSCORE] LuckPerms event classes not present; skipping event registration");
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Failed to register LuckPerms event listener reflectively");
        }
    }

    private void handleUserDataRecalculateEventReflective(Object event) {
        if (event == null) return;
        try {
            Method getUser = event.getClass().getMethod("getUser");
            Object user = getUser.invoke(event);
            if (user == null) return;

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

            String primaryLocal = null;
            try {
                Method getPrimary = user.getClass().getMethod("getPrimaryGroup");
                Object primObj = getPrimary.invoke(user);
                if (primObj instanceof String) primaryLocal = (String) primObj;
            } catch (Throwable ignored) {}

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

            for (Map.Entry<Ref<EntityStore>, UUID> e : refToUuid.entrySet()) {
                final Ref<EntityStore> r = e.getKey();
                final UUID mappedUuid = e.getValue();
                if (!uuid.equals(mappedUuid)) continue;

                try {
                    final Store<EntityStore> st = r.getStore();
                    if (st == null) continue;
                    final EntityStore external = (EntityStore) st.getExternalData();
                    if (external == null) continue;
                    final World w = external.getWorld();
                    if (w == null) continue;

                    w.execute(() -> {
                        try {
                            if (!r.isValid()) return;
                            final Store<EntityStore> s = r.getStore();
                            if (s == null) return;
                            Player p = (Player) s.getComponent(r, Player.getComponentType());
                            PlayerRef pr = (PlayerRef) s.getComponent(r, PlayerRef.getComponentType());
                            if (p == null || pr == null) return;
                            HudManager hm = p.getHudManager();
                            if (hm == null) return;
                            if (hm.getCustomHud() instanceof ScoreboardHud) {
                                ScoreboardHud sb = (ScoreboardHud) hm.getCustomHud();
                                String last = lastKnownRank.get(r);
                                if (last == null || !last.equals(rankText)) {
                                    lastKnownRank.put(r, rankText);
                                    sb.setRank(rankText);
                                    try {
                                        sb.refresh();
                                    } catch (CancellationException ce) {
                                        LOGGER.atWarning().withCause(ce).log("[AUTOSCORE] HUD refresh canceled during LP event for ref=%s", r);
                                    } catch (IllegalStateException ise) {
                                        LOGGER.atWarning().withCause(ise).log("[AUTOSCORE] HUD refresh illegal state during LP event for ref=%s", r);
                                    } catch (Throwable t) {
                                        LOGGER.atWarning().withCause(t).log("[AUTOSCORE] HUD refresh failed during LP event for ref=%s", r);
                                    }
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