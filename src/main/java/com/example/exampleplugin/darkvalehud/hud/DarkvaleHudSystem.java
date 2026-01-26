package com.example.exampleplugin.darkvalehud.hud;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.example.exampleplugin.darkvalehud.data.DebugManager;
import com.example.exampleplugin.PlaytimeStore;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.example.exampleplugin.darkvalehud.hud.DarkvaleHudRegistrar;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.lang.reflect.Method;

/**
 * DarkvaleHudSystem (fixed for MultipleHUD compatibility and updating)
 *
 * - Registers HUD via DarkvaleHudRegistrar (which uses MultipleHUD when present)
 * - When MultipleHUD is present, triggers the wrapper HUD's show() so inner HUD content is rebuilt & sent
 * - Only calls inner hud.show() when MultipleHUD is not present (fallback path)
 * - Uses final copies for lambda captures when scheduling show()
 */
public class DarkvaleHudSystem extends EntityTickingSystem<EntityStore> {
    private final DebugManager debugManager;
    private final Query<EntityStore> query;

    // Per-PlayerRef tracked state
    private final Map<PlayerRef, DarkvaleHud> huds = new HashMap<>();
    private final Map<PlayerRef, Ref<EntityStore>> attachedRefByPlayerRef = new ConcurrentHashMap<>();
    private final Map<PlayerRef, UUID> refToUuid = new ConcurrentHashMap<>();

    // Per-Ref join timestamps (session start times)
    private final Map<Ref<EntityStore>, Long> joinTimestamps = new ConcurrentHashMap<>();

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

    public DarkvaleHudSystem(DebugManager debugManager) {
        this.debugManager = debugManager;
        ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
        ComponentType<EntityStore, Player> playerType = Player.getComponentType();
        if (playerRefType == null || playerType == null) {
            this.query = Query.any();
        } else {
            @SuppressWarnings("unchecked")
            Query<EntityStore> q = (Query<EntityStore>) Query.and(new Query[] { (Query) playerRefType, (Query) playerType });
            this.query = q;
        }

        try {
            playtimeStore.load();
        } catch (Throwable ignored) {}
    }

    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    // Helper to detect if MultipleHUD is available at runtime.
    // Uses Class.forName so it won't hard-fail when MHUD is missing.
    private boolean isMultipleHudAvailable() {
        try {
            Class.forName("com.buuz135.mhud.MultipleHUD");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void tick(float deltaTime, int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        try {
            Holder<EntityStore> holder = EntityUtils.toHolder(entityIndex, chunk);
            Player player = (Player) holder.getComponent(Player.getComponentType());
            PlayerRef playerRef = (PlayerRef) holder.getComponent(PlayerRef.getComponentType());
            if (player == null || playerRef == null) return;

            Ref<EntityStore> ref = chunk.getReferenceTo(entityIndex);

            // Removal/invalid-ref cleanup: persist playtime for mapped UUID and remove hud
            try {
                if (player.wasRemoved() || ref == null || !ref.isValid()) {
                    DarkvaleHud existingHud = huds.remove(playerRef);
                    if (existingHud != null) {
                        try {
                            // persist playtime for mapped UUID if present
                            UUID mappedUuid = refToUuid.remove(playerRef);
                            Long joined = (ref != null) ? joinTimestamps.remove(ref) : null;
                            if (mappedUuid != null && joined != null) {
                                long elapsed = Math.max(0L, System.currentTimeMillis() - joined);
                                playtimeStore.addMillis(mappedUuid, elapsed);
                                playtimeStore.save();
                            }
                            // detach hud if still attached to player (best-effort) via registrar
                            try {
                                Player p = (Player) store.getComponent(ref, Player.getComponentType());
                                if (p != null) {
                                    DarkvaleHudRegistrar.hideHud(p, playerRef);
                                }
                            } catch (Throwable ignored) {}
                        } catch (Throwable ignored) {}
                    }
                    if (ref != null) joinTimestamps.remove(ref);
                    attachedRefByPlayerRef.remove(playerRef);
                    return;
                }
            } catch (Throwable ignored) {}

            // Detect transfer (PlayerRef attached to a different entity Ref)
            Ref<EntityStore> prevRef = attachedRefByPlayerRef.get(playerRef);
            if (prevRef == null || !prevRef.equals(ref)) {
                // Persist previous session (if any)
                try {
                    if (prevRef != null) {
                        UUID prevUuid = refToUuid.get(playerRef);
                        Long prevJoined = joinTimestamps.remove(prevRef);
                        if (prevUuid != null && prevJoined != null) {
                            long elapsed = Math.max(0L, System.currentTimeMillis() - prevJoined);
                            playtimeStore.addMillis(prevUuid, elapsed);
                            playtimeStore.save();
                        }
                    }
                } catch (Throwable ignored) {}

                // Copy debug-enabled flag from prev ref to new ref so toggle persists across transfer
                try {
                    if (prevRef != null) {
                        boolean prevEnabled = debugManager.isDebugEnabled(prevRef);
                        boolean newEnabled = (ref != null) && debugManager.isDebugEnabled(ref);
                        if (prevEnabled && !newEnabled) {
                            debugManager.setDebugEnabled(ref, true);
                        }
                    }
                } catch (Throwable ignored) {}

                // Update mappings for new ref
                attachedRefByPlayerRef.put(playerRef, ref);
                if (ref != null) {
                    joinTimestamps.put(ref, System.currentTimeMillis());
                    // record uuid for the new ref if available
                    try {
                        UUIDComponent uuidComp = (UUIDComponent) store.getComponent(ref, UUIDComponent.getComponentType());
                        if (uuidComp != null) {
                            refToUuid.put(playerRef, uuidComp.getUuid());
                        } else {
                            refToUuid.remove(playerRef);
                        }
                    } catch (Throwable ignored) {
                        refToUuid.remove(playerRef);
                    }
                }
            }

            // Ensure HUD exists and is attached to the current PlayerRef
            DarkvaleHud hud = huds.get(playerRef);
            if (hud == null) {
                hud = new DarkvaleHud(playerRef, this.debugManager, ref);
                huds.put(playerRef, hud);
                try {
                    // register via registrar that calls MultipleHUD (compile-time)
                    DarkvaleHudRegistrar.showHud(player, playerRef, hud);
                } catch (Throwable ignored) {}
                if (ref != null) joinTimestamps.put(ref, joinTimestamps.getOrDefault(ref, System.currentTimeMillis()));

                // Safely show HUD if MultipleHUD is NOT present (fallback behavior).
                // If MultipleHUD is present, it will handle building & sending the UI, so do not call inner hud.show().
                try {
                    if (!isMultipleHudAvailable()) {
                        World world = ((EntityStore) store.getExternalData()).getWorld();
                        final DarkvaleHud hudFinal = hud; // final copy for lambda capture
                        if (world != null) {
                            world.execute(() -> {
                                try { hudFinal.show(); } catch (Throwable ignoredShow) {}
                            });
                        } else {
                            try { hudFinal.show(); } catch (Throwable ignored) {}
                        }
                    } else {
                        // If MultipleHUD is present, ensure the wrapper sends a first build:
                        try {
                            CustomUIHud wrapper = player.getHudManager().getCustomHud();
                            if (wrapper != null) {
                                // call wrapper.show() to make it rebuild combined UI
                                wrapper.show();
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }

            // Make sure the toggle state has taken effect before updating content
            if (!this.debugManager.isDebugEnabled(ref)) {
                // For fallback (no MHUD) ensure the HUD is shown/updated once (build will hide).
                if (!isMultipleHudAvailable()) {
                    try { hud.show(); } catch (Throwable ignored) {}
                } else {
                    try {
                        CustomUIHud wrapper = player.getHudManager().getCustomHud();
                        if (wrapper != null) wrapper.show();
                    } catch (Throwable ignored) {}
                }
                return;
            }

            // --- COORDS: original working pattern — always set integer coords and show()
            TransformComponent transform = (TransformComponent) holder.getComponent(TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d pos = transform.getPosition();
                int x = MathUtil.floor(pos.getX());
                int y = MathUtil.floor(pos.getY());
                int z = MathUtil.floor(pos.getZ());
                hud.setPosition(x, y, z);
            }

            // --- PLAYTIME: use mapped UUID for this PlayerRef
            UUID uuid = refToUuid.get(playerRef);
            if (uuid == null) {
                // fallback: try to obtain from current ref and store mapping
                try {
                    UUIDComponent uuidComp = (UUIDComponent) store.getComponent(ref, UUIDComponent.getComponentType());
                    if (uuidComp != null) {
                        uuid = uuidComp.getUuid();
                        refToUuid.put(playerRef, uuid);
                    }
                } catch (Throwable ignored) {}
            }

            long storedMillis = (uuid != null) ? playtimeStore.getTotalMillis(uuid) : 0L;
            long joined = (ref != null) ? joinTimestamps.getOrDefault(ref, System.currentTimeMillis()) : System.currentTimeMillis();
            hud.setPlaytime(formatPlaytime(storedMillis + (System.currentTimeMillis() - joined)));

            // --- RANK: LuckPerms cached fast-path then permission fallback
            String rankText = tryLuckPermsCachedRank(uuid);
            if (rankText == null) rankText = tryPermissionRank(player);
            if (rankText == null) rankText = "Rank: Member";
            hud.setRank(rankText);

            // --- BIOME
            try {
                WorldMapTracker tracker = player.getWorldMapTracker();
                if (tracker != null) {
                    String currentBiome = tracker.getCurrentBiomeName();
                    if (currentBiome != null && !currentBiome.isEmpty()) {
                        hud.setBiomeName(formatBiomeName(currentBiome));
                    }
                }
            } catch (Throwable ignored) {}

            // --- Server name & footer defaults
            hud.setServerName("Darkvale");
            hud.setFooter("www.darkvale.com");

            // --- SHOW: original behavior — write every tick (this keeps client updated right after transfer)
            // When MultipleHUD is present, call wrapper.show() to rebuild combined UI. Otherwise call inner hud.show().
            try {
                if (!isMultipleHudAvailable()) {
                    World world = ((EntityStore) store.getExternalData()).getWorld();
                    final DarkvaleHud hudFinal2 = hud; // final copy for lambda capture
                    if (world != null) {
                        world.execute(() -> {
                            try { hudFinal2.show(); } catch (Throwable ignoredShow) {}
                        });
                    } else {
                        hudFinal2.show();
                    }
                } else {
                    try {
                        CustomUIHud wrapper = player.getHudManager().getCustomHud();
                        if (wrapper != null) wrapper.show();
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

        } catch (Throwable ignored) {
            // keep tick robust
        }
    }

    private static String formatPlaytime(long totalMillis) {
        long totalSeconds = totalMillis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        if (hours > 0) return String.format("Playtime: %dh %dm", hours, minutes);
        return String.format("Playtime: %dm", Math.max(0L, minutes));
    }

    private String tryLuckPermsCachedRank(UUID uuid) {
        if (uuid == null) return null;
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            if (lp == null) return null;
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(uuid);
            if (user == null) return null;
            String prefix = null;
            try {
                if (user.getCachedData() != null && user.getCachedData().getMetaData() != null) {
                    prefix = user.getCachedData().getMetaData().getPrefix();
                }
            } catch (Throwable ignored) {}
            String primary = user.getPrimaryGroup();
            if (primary == null) return null;
            if (prefix != null && !prefix.isBlank()) return "Rank: " + prefix + " " + primary;
            return "Rank: " + primary;
        } catch (Throwable ignored) { return null; }
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
                    if (res instanceof Boolean && (Boolean) res) return "Rank: " + rankName;
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

    private String formatBiomeName(String internalName) {
        if (internalName == null) return "Unknown";
        String name = internalName.replace("zone_1_", "").replace("zone_2_", "").replace("zone_3_", "").replace("zone_4_", "")
                .replace("Zone1_", "").replace("Zone2_", "").replace("Zone3_", "").replace("Zone4_", "");
        StringBuilder result = new StringBuilder();
        String[] words = name.split("_");
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) result.append(word.substring(1).toLowerCase());
                result.append(" ");
            }
        }
        return result.toString().trim();
    }
}