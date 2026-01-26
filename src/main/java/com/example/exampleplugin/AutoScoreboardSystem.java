package com.example.exampleplugin;

import com.example.exampleplugin.darkvalehud.hud.DarkvaleHudRegistrar;
import com.example.exampleplugin.darkvalehud.hud.DarkvaleHudHelper;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AutoScoreboardSystem reimplemented in the ticking style used by your working DarkvaleHudSystem.
 *
 * - Maintains a per-PlayerRef ScoreboardHud and updates it from live components each tick.
 * - Recreates and re-attaches HUDs when PlayerRef changes (so HUDs are always bound to the current instance/world).
 * - Persists playtime when a player is removed.
 */
public final class AutoScoreboardSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<PlayerRef, ScoreboardHud> huds = new ConcurrentHashMap<>();
    private final Map<PlayerRef, Long> joinTimestamps = new ConcurrentHashMap<>();
    private final Map<PlayerRef, UUID> refToUuid = new ConcurrentHashMap<>();
    private final Map<PlayerRef, String> lastKnownRank = new ConcurrentHashMap<>();

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

    private static final DecimalFormat BALANCE_FMT = new DecimalFormat("#,##0.##");

    private final Query<EntityStore> query;

    public AutoScoreboardSystem() {
        try {
            playtimeStore.load();
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("Failed to load playtime store");
        }

        // safe query construction like DarkvaleHudSystem — avoid null Query
        ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
        ComponentType<EntityStore, Player> playerType = Player.getComponentType();
        if (playerRefType == null || playerType == null) {
            this.query = Query.any();
        } else {
            @SuppressWarnings("unchecked")
            Query<EntityStore> q = (Query<EntityStore>) Query.and(new Query[] { (Query) playerRefType, (Query) playerType });
            this.query = q;
        }

        tryRegisterLuckPermsEventListener();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    @Override
    public void tick(float deltaTime, int entityIndex, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            Holder<EntityStore> holder = EntityUtils.toHolder(entityIndex, chunk);
            Player player = (Player) holder.getComponent(Player.getComponentType());
            PlayerRef playerRef = (PlayerRef) holder.getComponent(PlayerRef.getComponentType());
            if (player == null || playerRef == null) return;

            Ref<EntityStore> ref = chunk.getReferenceTo(entityIndex);

            // If player removed, cleanup
            try {
                if (player.wasRemoved() || ref == null || !ref.isValid()) {
                    cleanupForRef(playerRef, ref, store);
                    return;
                }
            } catch (Throwable ignored) {}

            // Ensure we have a tracked hud for this PlayerRef
            ScoreboardHud hud = huds.get(playerRef);
            if (hud == null) {
                hud = new ScoreboardHud(playerRef);
                hud.setServerName("Darkvale");
                hud.setGold("Gold: 0");
                hud.setRank("Rank: Member");
                hud.setPlaytime("Playtime: 0m");
                hud.setCoords("Coords: 0, 0, 0");
                hud.setFooter("www.darkvale.com");
                huds.put(playerRef, hud);

                // persist uuid if available and set join timestamp
                try {
                    UUIDComponent uuidComp = (UUIDComponent) store.getComponent(ref, UUIDComponent.getComponentType());
                    if (uuidComp != null) refToUuid.put(playerRef, uuidComp.getUuid());
                } catch (Throwable ignored) {}
                joinTimestamps.put(playerRef, System.currentTimeMillis());

                // Attach hud via DarkvaleHudRegistrar (MultipleHUD)
                try {
                    DarkvaleHudRegistrar.showHud(player, playerRef, hud);
                } catch (Throwable t) {
                    LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Failed to register hud with MHUD");
                }

                // schedule safe show via world
                try {
                    World world = ((EntityStore) store.getExternalData()).getWorld();
                    final ScoreboardHud hudFinal = hud; // <-- add this final copy (or DarkvaleHud final copy where appropriate)
                    if (world != null) {
                        world.execute(() -> {
                            try { hudFinal.show(); } catch (Throwable ignoredShow) {}
                        });
                    } else {
                        hud.show();
                    }
                } catch (Throwable ignored) {}

                // Try to resolve and set rank initially
                UUID uuid = refToUuid.get(playerRef);
                String rankText = tryLuckPermsCachedRank(uuid);
                if (rankText == null) rankText = tryPermissionRank(player);
                if (rankText == null) rankText = "Rank: Member";
                lastKnownRank.put(playerRef, rankText);
                hud.setRank(rankText);
                // safe refresh
                try { if (playerRef.getPacketHandler() != null) hud.refresh(); } catch (Throwable ignored) {}
            }

            // Update coords if transform present
            TransformComponent transform = (TransformComponent) holder.getComponent(TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d pos = transform.getPosition();
                hud.setCoords(String.format("Coords: %d, %d, %d",
                        (int) Math.floor(pos.getX()),
                        (int) Math.floor(pos.getY()),
                        (int) Math.floor(pos.getZ())));
            }

            // Update playtime
            UUID u = refToUuid.get(playerRef);
            long stored = (u != null) ? playtimeStore.getTotalMillis(u) : 0L;
            long joined = joinTimestamps.getOrDefault(playerRef, System.currentTimeMillis());
            hud.setPlaytime(formatPlaytime(stored + (System.currentTimeMillis() - joined)));

            // Rank check (cheap cached LP fast-path, else permission fallback)
            String newRank = null;
            try {
                newRank = tryLuckPermsCachedRank(u);
            } catch (Throwable ignored) {}
            if (newRank == null) {
                try {
                    newRank = tryPermissionRank(player);
                } catch (Throwable ignored) {}
            }
            if (newRank == null) newRank = "Rank: Member";

            String last = lastKnownRank.get(playerRef);
            if (last == null || !last.equals(newRank)) {
                lastKnownRank.put(playerRef, newRank);
                hud.setRank(newRank);
            }

            // Refresh HUD (only if packet handler is available)
            try {
                if (playerRef.getPacketHandler() != null) {
                    hud.refresh();
                }
            } catch (Throwable ignored) {
                // skip refresh if packet handler not available or something goes wrong
            }

        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Tick error");
        }
    }

    private void cleanupForRef(PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            // persist playtime
            UUID u = refToUuid.remove(playerRef);
            Long joined = joinTimestamps.remove(playerRef);
            if (u != null && joined != null) {
                long sessionElapsed = Math.max(0L, System.currentTimeMillis() - joined);
                playtimeStore.addMillis(u, sessionElapsed);
                playtimeStore.save();
            }
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Failed to persist playtime on cleanup");
        }

        try {
            // detach HUD if present (use registrar for MultipleHUD compatibility)
            ScoreboardHud s = huds.remove(playerRef);
            if (s != null && ref != null && ref.isValid() && store != null) {
                try {
                    Player p = (Player) store.getComponent(ref, Player.getComponentType());
                    if (p != null) {
                        DarkvaleHudRegistrar.hideHud(p, playerRef);
                    }
                } catch (Throwable ignored) {}
            }
            lastKnownRank.remove(playerRef);
        } catch (Throwable ignored) {}
    }

    /**
     * Try to obtain rank text from LuckPerms cached user (no loadUser calls).
     * Returns "Rank: X" or null if unavailable.
     */
    private String tryLuckPermsCachedRank(UUID uuid) {
        if (uuid == null) return null;
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            if (lp == null) {
                LOGGER.atInfo().log("[AUTOSCORE] LuckPermsProvider.get() returned null");
                return null;
            }
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(uuid);
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
                    boolean has = false;
                    try {
                        Method alt = p.getClass().getMethod("hasPermissions", String.class);
                        Object r = alt.invoke(p, node);
                        if (r instanceof Boolean) has = (Boolean) r;
                    } catch (Throwable ignoredAlt) {}
                    if (has) return "Rank: " + rankName;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String buildRankText(@Nonnull net.luckperms.api.model.user.User user) {
        try {
            String primary = user.getPrimaryGroup();
            String prefix = null;
            try {
                if (user.getCachedData() != null && user.getCachedData().getMetaData() != null) {
                    prefix = user.getCachedData().getMetaData().getPrefix();
                }
            } catch (Throwable ignored) {}
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

    // Reflection-based registration for LuckPerms UserDataRecalculateEvent (keeps earlier behavior)
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

            java.util.function.Consumer<Object> consumer = new java.util.function.Consumer<>() {
                @Override
                public void accept(Object event) {
                    try {
                        handleUserDataRecalculateEventReflective(event);
                    } catch (Throwable t) {
                        LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Error in LP event consumer");
                    }
                }
            };

            Method subscribe = eventBus.getClass().getMethod("subscribe", Class.class, java.util.function.Consumer.class);
            subscribe.invoke(eventBus, eventClass, consumer);

            LOGGER.atInfo().log("[AUTOSCORE] Registered LuckPerms UserDataRecalculateEvent listener (reflective)");
        } catch (ClassNotFoundException cnf) {
            LOGGER.atInfo().log("[AUTOSCORE] LuckPerms event classes not present; skipping event registration");
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Failed to register LuckPerms event listener reflectively");
        }
    }

    // Reflective handler (updates lastKnownRank + huds when relevant)
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

            // Apply to matching huds
            for (Map.Entry<PlayerRef, UUID> e : refToUuid.entrySet()) {
                final PlayerRef pref = e.getKey();
                final UUID mapped = e.getValue();
                if (!uuid.equals(mapped)) continue;

                ScoreboardHud hud = huds.get(pref);
                if (hud == null) continue;
                String last = lastKnownRank.get(pref);
                if (last == null || !last.equals(rankText)) {
                    lastKnownRank.put(pref, rankText);
                    hud.setRank(rankText);
                    try {
                        if (pref.getPacketHandler() != null) hud.refresh();
                    } catch (Throwable ignored) {}
                    LOGGER.atInfo().log("[AUTOSCORE] Applied LP event update for uuid=%s newRank=%s", uuid, rankText);
                }
            }
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[AUTOSCORE] Error handling UserDataRecalculateEvent reflectively");
        }
    }
}