package com.example.exampleplugin.spawner;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProximitySpawnSystem manager: holds many spawn entries and ticks them in a single system instance.
 *
 * Register one instance of this system at plugin setup, then call addSpawn(...) for each spawn definition.
 */
public class ProximitySpawnSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Query<EntityStore> query;
    // map id -> SpawnEntry
    private final Map<String, SpawnEntry> spawns = new ConcurrentHashMap<>();

    public ProximitySpawnSystem() {
        super();
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
            if (spawns.isEmpty()) return;

            Holder<EntityStore> holder = EntityUtils.toHolder(entityIndex, chunk);
            Player player = (Player) holder.getComponent(Player.getComponentType());
            PlayerRef playerRef = (PlayerRef) holder.getComponent(PlayerRef.getComponentType());
            if (player == null || playerRef == null) return;

            Ref<EntityStore> ref = chunk.getReferenceTo(entityIndex);
            if (ref == null || !ref.isValid()) return;

            TransformComponent transform = (TransformComponent) holder.getComponent(TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d pos = transform.getPosition();

            long now = System.currentTimeMillis();

            // iterate over all spawn entries and check whether this player triggers them
            for (SpawnEntry entry : spawns.values()) {
                // world check
                World currentWorld = null;
                try {
                    Object external = store.getExternalData();
                    if (external instanceof EntityStore) {
                        currentWorld = ((EntityStore) external).getWorld();
                    }
                } catch (Throwable ignored) {}

                if (entry.targetWorldName != null) {
                    if (currentWorld == null) continue;
                    try {
                        String worldName = currentWorld.getName();
                        if (worldName == null || !worldName.equals(entry.targetWorldName)) continue;
                    } catch (Throwable ignored) {
                        continue;
                    }
                }

                double dx = pos.getX() - entry.tx;
                double dy = pos.getY() - entry.ty;
                double dz = pos.getZ() - entry.tz;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq <= entry.radiusSq) {
                    // in range
                    long playerLast = entry.perPlayerLastTrigger.getOrDefault(playerRef, 0L);
                    long cooldownRemaining = Math.max(0L, entry.cooldownMillis - (now - entry.lastSpawnAt));
                    if (now - entry.lastSpawnAt >= entry.cooldownMillis && (playerLast == 0L || now - playerLast >= 250L)) {
                        // schedule spawn on world thread
                        try {
                            World world = currentWorld;
                            final PlayerRef triggerRef = playerRef;
                            final Player triggerPlayer = player;
                            if (world != null) {
                                world.execute(() -> {
                                    try {
                                        entry.spawnStrategy.spawn(world, entry.tx, entry.ty, entry.tz, triggerRef, triggerPlayer);
                                    } catch (Throwable t) {
                                        LOG.atWarning().withCause(t).log("[ProximitySpawn] spawn execution failed for spawn=%s", entry.id);
                                    }
                                });
                            } else {
                                try {
                                    entry.spawnStrategy.spawn(world, entry.tx, entry.ty, entry.tz, triggerRef, triggerPlayer);
                                } catch (Throwable t) {
                                    LOG.atWarning().withCause(t).log("[ProximitySpawn] spawn direct execution failed for spawn=%s", entry.id);
                                }
                            }
                        } catch (Throwable t) {
                            LOG.atWarning().withCause(t).log("[ProximitySpawn] scheduling spawn error for %s", entry.id);
                        }

                        entry.lastSpawnAt = now;
                        entry.perPlayerLastTrigger.put(playerRef, now);
                    }
                }
            }
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[ProximitySpawn] unexpected tick error");
        }
    }

    /**
     * Add a spawn entry to the manager. If id already exists, it will be ignored.
     *
     * @param def spawn definition (id MUST be non-null ideally)
     * @param strategy spawn strategy
     * @return true if added, false if skipped (duplicate or invalid)
     */
    public boolean addSpawn(SpawnDefinition def, SpawnStrategy strategy) {
        if (def == null || strategy == null) return false;
        String id = (def.id == null || def.id.isBlank()) ? makeId(def) : def.id;
        if (spawns.containsKey(id)) {
            LOG.atInfo().log("[ProximitySpawnManager] spawn %s already registered, skipping", id);
            return false;
        }
        SpawnEntry entry = new SpawnEntry(id, def, strategy);
        spawns.put(id, entry);
        LOG.atInfo().log("[ProximitySpawnManager] added spawn %s at %.1f,%.1f,%.1f radius=%.1f world=%s", id, def.x, def.y, def.z, def.radius, def.world);
        return true;
    }

    /**
     * Remove a spawn by id
     */
    public boolean removeSpawn(String id) {
        if (id == null) return false;
        return spawns.remove(id) != null;
    }

    private static String makeId(SpawnDefinition def) {
        return String.format("spawn_%d_%d_%d", Math.round(def.x), Math.round(def.y), Math.round(def.z));
    }

    /**
     * Single spawn entry record
     */
    private static class SpawnEntry {
        final String id;
        final double tx, ty, tz;
        final double radius;
        final double radiusSq;
        final long cooldownMillis;
        final SpawnStrategy spawnStrategy;
        final String targetWorldName;

        volatile long lastSpawnAt = 0L;
        final Map<PlayerRef, Long> perPlayerLastTrigger = new ConcurrentHashMap<>();

        SpawnEntry(String id, SpawnDefinition def, SpawnStrategy strategy) {
            this.id = id;
            this.tx = def.x;
            this.ty = def.y;
            this.tz = def.z;
            this.radius = Math.max(0.0, def.radius);
            this.radiusSq = this.radius * this.radius;
            this.cooldownMillis = Math.max(0L, def.cooldownMillis);
            this.spawnStrategy = strategy;
            this.targetWorldName = (def.world == null || def.world.isBlank()) ? null : def.world;
        }
    }

    /**
     * Public spawn strategy interface (delegates to actual implementations)
     */
    public interface SpawnStrategy {
        void spawn(World world, double x, double y, double z, PlayerRef trigger, Player triggerPlayer);
    }

    // CommandSpawnStrategy and Reflection/Programmatic strategies stay as separate classes/files.
}