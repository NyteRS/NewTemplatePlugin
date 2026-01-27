package com.example.exampleplugin.spawner;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProximitySpawnSystem (updated)
 *
 * Added optional targetWorldName — if provided, the system only triggers when the player is in
 * the named world (World.getName()).
 */
public class ProximitySpawnSystem extends EntityTickingSystem<EntityStore> {

    private final Query<EntityStore> query;
    private final double tx, ty, tz;
    private final double radius;
    private final long cooldownMillis;
    private final SpawnStrategy spawnStrategy;
    private final String targetWorldName; // optional: spawn only in this world (instance)

    // tracking whether we have an active spawn and when it happened
    private volatile long lastSpawnAt = 0L;

    // optional: per-player last-trigger time to avoid re-triggering from same player repeatedly
    private final Map<PlayerRef, Long> perPlayerLastTrigger = new ConcurrentHashMap<>();

    /**
     * Constructor without world restriction.
     */
    public ProximitySpawnSystem(double tx, double ty, double tz, double radius, SpawnStrategy spawnStrategy, long cooldownMillis) {
        this(tx, ty, tz, radius, spawnStrategy, cooldownMillis, null);
    }

    /**
     * Constructor with optional world name.
     *
     * @param targetWorldName if non-null, only trigger when player's world.getName().equals(targetWorldName)
     */
    public ProximitySpawnSystem(double tx, double ty, double tz, double radius, SpawnStrategy spawnStrategy, long cooldownMillis, String targetWorldName) {
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.radius = Math.max(0.0, radius);
        this.spawnStrategy = spawnStrategy;
        this.cooldownMillis = Math.max(0L, cooldownMillis);
        this.targetWorldName = (targetWorldName == null || targetWorldName.isBlank()) ? null : targetWorldName;

        ComponentType<EntityStore, PlayerRef> prType = PlayerRef.getComponentType();
        ComponentType<EntityStore, Player> playerType = Player.getComponentType();
        if (prType == null || playerType == null) {
            this.query = Query.any();
        } else {
            @SuppressWarnings("unchecked")
            Query<EntityStore> q = (Query<EntityStore>) Query.and(new Query[]{(Query) prType, (Query) playerType});
            this.query = q;
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    @Override
    public void tick(float deltaTime, int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        try {
            Holder<EntityStore> holder = EntityUtils.toHolder(entityIndex, chunk);
            Player player = (Player) holder.getComponent(Player.getComponentType());
            PlayerRef playerRef = (PlayerRef) holder.getComponent(PlayerRef.getComponentType());
            if (player == null || playerRef == null) return;

            Ref<EntityStore> ref = chunk.getReferenceTo(entityIndex);
            if (ref == null || !ref.isValid()) return;

            TransformComponent transform = (TransformComponent) holder.getComponent(TransformComponent.getComponentType());
            if (transform == null) return;

            // World check (if target world specified)
            World currentWorld = null;
            try {
                Object external = store.getExternalData();
                if (external instanceof EntityStore) {
                    currentWorld = ((EntityStore) external).getWorld();
                }
            } catch (Throwable ignored) {}

            if (this.targetWorldName != null) {
                if (currentWorld == null) return;
                try {
                    String worldName = currentWorld.getName();
                    if (worldName == null || !worldName.equals(this.targetWorldName)) {
                        return;
                    }
                } catch (Throwable ignored) {
                    // if world name unavailable, be conservative and skip
                    return;
                }
            }

            Vector3d pos = transform.getPosition();
            double dx = pos.getX() - tx;
            double dy = pos.getY() - ty;
            double dz = pos.getZ() - tz;
            double distSq = dx * dx + dy * dy + dz * dz;
            double radiusSq = radius * radius;

            long now = System.currentTimeMillis();

            if (distSq <= radiusSq) {
                // Within radius — check global cooldown and per-player cooldown
                if (now - lastSpawnAt >= cooldownMillis) {
                    Long playerLast = perPlayerLastTrigger.get(playerRef);
                    if (playerLast == null || now - playerLast >= 250L) { // small per-player debounce (250ms)
                        // schedule spawn via world thread to be safe for world/entity ops
                        try {
                            World world = currentWorld;
                            final PlayerRef triggerRef = playerRef;
                            final Player triggerPlayer = player;
                            if (world != null) {
                                world.execute(() -> {
                                    try {
                                        spawnStrategy.spawn(world, tx, ty, tz, triggerRef, triggerPlayer);
                                    } catch (Throwable t) {
                                        t.printStackTrace();
                                    }
                                });
                            } else {
                                // fallback: call directly but guarded
                                try {
                                    spawnStrategy.spawn(world, tx, ty, tz, triggerRef, triggerPlayer);
                                } catch (Throwable ignored) {}
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }

                        lastSpawnAt = now;
                        perPlayerLastTrigger.put(playerRef, now);
                    }
                }
            } else {
                // leave behavior: optionally clear per-player timestamp to allow re-triggering after leaving
                // perPlayerLastTrigger.remove(playerRef);
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // SpawnStrategy, CommandSpawnStrategy, ReflectionSpawnStrategy are identical to previous version
    public interface SpawnStrategy {
        void spawn(World world, double x, double y, double z, PlayerRef trigger, Player triggerPlayer);
    }

    public static final class CommandSpawnStrategy implements SpawnStrategy {
        private final String commandTemplate;
        private final boolean runAsConsole;

        public CommandSpawnStrategy(String commandTemplate, boolean runAsConsole) {
            this.commandTemplate = commandTemplate;
            this.runAsConsole = runAsConsole;
        }

        @Override
        public void spawn(World world, double x, double y, double z, PlayerRef trigger, Player triggerPlayer) {
            try {
                String playerName = "";
                try {
                    if (triggerPlayer != null) {
                        playerName = triggerPlayer.getDisplayName();
                    } else if (trigger != null) {
                        playerName = trigger.getUsername();
                    }
                } catch (Throwable ignored) {
                    playerName = (trigger != null) ? trigger.getUsername() : "";
                }

                String cmd = commandTemplate
                        .replace("%x", String.valueOf((int) Math.floor(x)))
                        .replace("%y", String.valueOf((int) Math.floor(y)))
                        .replace("%z", String.valueOf((int) Math.floor(z)))
                        .replace("%player", playerName);

                if (runAsConsole) {
                    try {
                        CommandManager.get().handleCommand(ConsoleSender.INSTANCE, cmd);
                    } catch (Throwable t) {
                        if (triggerPlayer != null) {
                            CommandManager.get().handleCommand(triggerPlayer, cmd);
                        } else if (trigger != null) {
                            try { CommandManager.get().handleCommand(trigger, cmd); } catch (Throwable ignored) {}
                        }
                    }
                } else {
                    if (triggerPlayer != null) {
                        CommandManager.get().handleCommand(triggerPlayer, cmd);
                    } else if (trigger != null) {
                        try { CommandManager.get().handleCommand(trigger, cmd); } catch (Throwable ignored) {
                            CommandManager.get().handleCommand(ConsoleSender.INSTANCE, cmd);
                        }
                    } else {
                        CommandManager.get().handleCommand(ConsoleSender.INSTANCE, cmd);
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public static final class ReflectionSpawnStrategy implements SpawnStrategy {
        private final String entityTypeId;

        public ReflectionSpawnStrategy(String entityTypeId) { this.entityTypeId = entityTypeId; }

        @Override
        public void spawn(World world, double x, double y, double z, PlayerRef trigger, Player triggerPlayer) {
            try {
                if (world == null) return;
                try {
                    Object entityStore = world.getEntityStore();
                    if (entityStore != null) {
                        Class<?> esClass = entityStore.getClass();
                        for (String mName : new String[]{"createEntity", "create", "spawnEntity", "createInstance"}) {
                            try {
                                java.lang.reflect.Method m = esClass.getMethod(mName, String.class);
                                Object entity = m.invoke(entityStore, entityTypeId);
                                if (entity != null) {
                                    try {
                                        java.lang.reflect.Method setPos = entity.getClass().getMethod("setPosition", double.class, double.class, double.class);
                                        setPos.invoke(entity, x, y, z);
                                    } catch (Throwable ignored) {}
                                    try {
                                        java.lang.reflect.Method spawnM = world.getClass().getMethod("spawnEntity", entity.getClass());
                                        spawnM.invoke(world, entity);
                                        return;
                                    } catch (Throwable ignored2) {}
                                    try {
                                        java.lang.reflect.Method addM = esClass.getMethod("addEntity", entity.getClass());
                                        addM.invoke(entityStore, entity);
                                        return;
                                    } catch (Throwable ignored3) {}
                                }
                            } catch (NoSuchMethodException ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
                System.out.println("[ProximitySpawn] Reflection spawn heuristics failed; adapt ReflectionSpawnStrategy for your server build.");
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}