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
 * ProximitySpawnSystem
 *
 * - ticks over player entities and checks distance to a target location
 * - triggers a SpawnStrategy when a player enters radius
 * - supports cooldown between spawns
 *
 * Usage:
 *   register system:
 *     this.getEntityStoreRegistry().registerSystem(
 *         new ProximitySpawnSystem(targetX, targetY, targetZ, radius,
 *             new CommandSpawnStrategy("spawnmob zombie %x %y %z", true), cooldownMs)
 *     );
 */
public class ProximitySpawnSystem extends EntityTickingSystem<EntityStore> {

    private final Query<EntityStore> query;
    private final double tx, ty, tz;
    private final double radius;
    private final long cooldownMillis;
    private final SpawnStrategy spawnStrategy;

    // tracking whether we have an active spawn and when it happened
    private volatile long lastSpawnAt = 0L;

    // optional: per-player last-trigger time to avoid re-triggering from same player repeatedly
    private final Map<PlayerRef, Long> perPlayerLastTrigger = new ConcurrentHashMap<>();

    /**
     * Create a proximity spawner.
     *
     * @param tx target X
     * @param ty target Y
     * @param tz target Z
     * @param radius trigger radius (in same world units as TransformComponent)
     * @param spawnStrategy strategy used to actually create the mob/perform spawn
     * @param cooldownMillis minimum time between spawns in ms
     */
    public ProximitySpawnSystem(double tx, double ty, double tz, double radius, SpawnStrategy spawnStrategy, long cooldownMillis) {
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.radius = Math.max(0.0, radius);
        this.spawnStrategy = spawnStrategy;
        this.cooldownMillis = Math.max(0L, cooldownMillis);

        // Build the query for Player + PlayerRef (same pattern used across repo)
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
                            World world = ((EntityStore) store.getExternalData()).getWorld();
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
                                    spawnStrategy.spawn(((EntityStore) store.getExternalData()).getWorld(), tx, ty, tz, triggerRef, triggerPlayer);
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
                // optionally you could reset perPlayerLastTrigger here to allow re-triggering when player leaves and re-enters
                // perPlayerLastTrigger.remove(playerRef);
            }

        } catch (Throwable t) {
            // protect tick loop from exceptions
            t.printStackTrace();
        }
    }

    /**
     * Strategy that performs the actual spawn action.
     */
    public interface SpawnStrategy {
        /**
         * Called when a spawn is requested.
         *
         * @param world the world instance (may be null in some fallback cases)
         * @param x     spawn x
         * @param y     spawn y
         * @param z     spawn z
         * @param trigger PlayerRef that triggered the spawn
         * @param triggerPlayer optional Player instance (may be null)
         */
        void spawn(World world, double x, double y, double z, PlayerRef trigger, Player triggerPlayer);
    }

    /**
     * Simple spawn strategy that executes a server command.
     * Template supports placeholders: %x %y %z %player
     */
    public static final class CommandSpawnStrategy implements SpawnStrategy {
        private final String commandTemplate;
        private final boolean runAsConsole;

        /**
         * @param commandTemplate command string template, e.g. "spawnmob zombie %x %y %z"
         * @param runAsConsole if true, runs command as console (ConsoleSender.INSTANCE), otherwise runs as triggering player
         */
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
                        // Player may expose getUsername() in this server API
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
                        // fallback: try running as trigger player if console not allowed
                        if (triggerPlayer != null) {
                            CommandManager.get().handleCommand(triggerPlayer, cmd);
                        } else if (trigger != null) {
                            // try to send as the PlayerRef via CommandManager if supported
                            try {
                                CommandManager.get().handleCommand(trigger, cmd);
                            } catch (Throwable ignored) {}
                        }
                    }
                } else {
                    if (triggerPlayer != null) {
                        CommandManager.get().handleCommand(triggerPlayer, cmd);
                    } else if (trigger != null) {
                        try {
                            CommandManager.get().handleCommand(trigger, cmd);
                        } catch (Throwable ignored) {
                            CommandManager.get().handleCommand(ConsoleSender.INSTANCE, cmd);
                        }
                    } else {
                        // fallback to console
                        CommandManager.get().handleCommand(ConsoleSender.INSTANCE, cmd);
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    /**
     * Reflection-based strategy: best-effort attempt to find a spawn/creation API in server runtime.
     * This is a helper you can adapt to your server's specific spawn APIs — it demonstrates the pattern.
     */
    public static final class ReflectionSpawnStrategy implements SpawnStrategy {

        private final String entityTypeId; // e.g. "zombie" or registry id your server uses

        public ReflectionSpawnStrategy(String entityTypeId) {
            this.entityTypeId = entityTypeId;
        }

        @Override
        public void spawn(World world, double x, double y, double z, PlayerRef trigger, Player triggerPlayer) {
            try {
                if (world == null) return;

                // Best-effort reflection attempts (adapt to your exact server API)
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
                            } catch (NoSuchMethodException ignored) {
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                System.out.println("[ProximitySpawn] Reflection spawn heuristics failed; please adapt ReflectionSpawnStrategy for your server build.");
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}