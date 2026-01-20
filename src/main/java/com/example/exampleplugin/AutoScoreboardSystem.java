package com.example.exampleplugin;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
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

/**
 * AutoScoreboardSystem — safe attach + refresh using ScoreboardHud and tailored for your UI.
 *
 * Guarantees:
 * - Attach happens after client-ready or with a short delay.
 * - Periodic refresh runs only when world/player/client are ready.
 * - Uses refreshNow on the world thread to push values to client.
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
    private static final long PLAYER_READY_DELAY_MS = 400L;
    private static final long CLIENT_READY_CHUNKS_WAIT_MS = 10_000L;
    private static final DecimalFormat BALANCE_FMT = new DecimalFormat("#,##0.##");

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

        long joinMs = System.currentTimeMillis();
        joinTimestamps.put(ref, joinMs);
        if (playerUuid != null) refToUuid.put(ref, playerUuid);

        EntityStore external = (EntityStore) store.getExternalData();
        World world = external.getWorld();

        // schedule attach / waiting on the world thread
        world.execute(() -> {
            try {
                if (!ref.isValid()) return;
                if (!world.isAlive()) return;

                Player p = (Player) store.getComponent(ref, Player.getComponentType());
                PlayerRef pref = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
                if (p == null || pref == null) return;

                PacketHandler ph = pref.getPacketHandler();
                CompletableFuture<Void> chunksFuture = null;
                try { chunksFuture = (ph != null) ? ph.getClientReadyForChunksFuture() : null; } catch (Throwable ignored) {}

                if (chunksFuture != null && !chunksFuture.isDone()) {
                    final ScheduledFuture<?> timeout = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                        world.execute(() -> {
                            if (!ref.isValid() || !world.isAlive()) return;
                            attachHudNow(ref, store, joinMs, world);
                        });
                    }, CLIENT_READY_CHUNKS_WAIT_MS, TimeUnit.MILLISECONDS);

                    chunksFuture.whenComplete((v, ex) -> {
                        try { timeout.cancel(false); } catch (Throwable ignore) {}
                        world.execute(() -> {
                            if (!ref.isValid() || !world.isAlive()) return;
                            attachHudNow(ref, store, joinMs, world);
                        });
                    });
                    return;
                }

                if (p.isWaitingForClientReady()) {
                    final long start = System.currentTimeMillis();
                    final ScheduledFuture<?> poll = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
                        world.execute(() -> {
                            if (!ref.isValid() || !world.isAlive()) { cancelUpdater(ref); return; }
                            Player current = (Player) store.getComponent(ref, Player.getComponentType());
                            if (current == null) { cancelUpdater(ref); return; }
                            if (!current.isWaitingForClientReady()) {
                                attachHudNow(ref, store, joinMs, world);
                                cancelUpdater(ref);
                                return;
                            }
                            if (System.currentTimeMillis() - start > CLIENT_READY_CHUNKS_WAIT_MS) {
                                attachHudNow(ref, store, joinMs, world);
                                cancelUpdater(ref);
                            }
                        });
                    }, 250L, 250L, TimeUnit.MILLISECONDS);
                    updaters.put(ref, poll);
                    return;
                }

                HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                    world.execute(() -> {
                        if (!ref.isValid() || !world.isAlive()) return;
                        attachHudNow(ref, store, joinMs, world);
                    });
                }, PLAYER_READY_DELAY_MS, TimeUnit.MILLISECONDS);
            } catch (Throwable t) {
                LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Error scheduling HUD attach");
            }
        });
    }

    private void cancelUpdater(@Nonnull Ref<EntityStore> ref) {
        ScheduledFuture<?> f = updaters.remove(ref);
        if (f != null) f.cancel(false);
    }

    /**
     * Must be run on the world thread (we call world.execute before invoking this).
     */
    private void attachHudNow(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, long joinTimestampMs, @Nonnull World world) {
        try {
            if (!ref.isValid() || !world.isAlive()) return;

            Player p = (Player) store.getComponent(ref, Player.getComponentType());
            PlayerRef pref = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
            if (p == null || pref == null) return;

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

            hud.openFor(p);

            String rankText = null;
            if (uuid != null) {
                try {
                    LuckPerms lp = LuckPermsProvider.get();
                    if (lp != null) {
                        User user = lp.getUserManager().getUser(uuid);
                        if (user != null) {
                            rankText = buildRankText(user);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (rankText == null) rankText = tryPermissionRank(p);
            if (rankText == null) rankText = "Rank: Member";

            lastKnownRank.put(ref, rankText);
            hud.setRank(rankText);

            cancelUpdater(ref);
            ScheduledFuture<?> periodic = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
                world.execute(() -> {
                    try {
                        if (!ref.isValid() || !world.isAlive()) { cancelUpdater(ref); return; }

                        Player current = (Player) store.getComponent(ref, Player.getComponentType());
                        if (current == null) { cancelUpdater(ref); return; }

                        if (current.isWaitingForClientReady()) return;

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

                        // Debug: log server-side computed values just before sending
                        try {
                            LOGGER.atInfo().log("[AUTOSCORE] Updating HUD for %s coords=%s playtime=%s",
                                    current.getDisplayName(),
                                    (transform != null ? hudToCoordsString(transform.getPosition()) : "n/a"),
                                    formatPlaytime(stored + (System.currentTimeMillis() - joinedNow)));
                        } catch (Throwable ignored) {}

                        // synchronous refresh on world thread
                        hud.refreshNow(current);

                    } catch (Throwable t) {
                        LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Periodic refresh failed");
                    }
                });
            }, REFRESH_PERIOD_SECONDS, REFRESH_PERIOD_SECONDS, TimeUnit.SECONDS);

            updaters.put(ref, periodic);
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] attachHudNow error");
        }
    }

    private static String hudToCoordsString(Vector3d pos) {
        return String.format("%d,%d,%d", (int) Math.floor(pos.getX()), (int) Math.floor(pos.getY()), (int) Math.floor(pos.getZ()));
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        cancelUpdater(ref);
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
                    if (p.getWorld() != null) {
                        p.getWorld().execute(() -> {
                            try {
                                ScoreboardHud sb = (ScoreboardHud) hm.getCustomHud();
                                if (sb != null) sb.hideFor(p);
                            } catch (Throwable ignored) {}
                        });
                    }
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

    // Keep your existing LuckPerms registration & handler methods (not repeated here)
    private void tryRegisterLuckPermsEventListener() {
        // existing implementation...
    }
    private void handleUserDataRecalculateEventReflective(Object event) {
        // existing implementation...
    }
}