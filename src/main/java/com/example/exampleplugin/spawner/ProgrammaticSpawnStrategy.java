package com.example.exampleplugin.spawner;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.spawning.SpawningContext;
import com.hypixel.hytale.server.spawning.ISpawnableWithModel;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.util.TargetUtil;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ProgrammaticSpawnStrategy — uses NPCPlugin spawn API to create entities similar to HytaleSpawners.
 *
 * Notes:
 * - This class mirrors the spawn flow from RedW0lfStoneYT/HytaleSpawners (getValidSpawnPoint, nearby checks, NPCPlugin.spawnEntity).
 * - It assumes NPCPlugin and spawning APIs are available at runtime.
 * - If NPCPlugin is missing, use your CommandSpawnStrategy fallback instead.
 */
public class ProgrammaticSpawnStrategy implements ProximitySpawnSystem.SpawnStrategy {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final String spawnTypeId;
    private final int spawnCount;        // number of mobs to attempt per activation
    private final int spawnRadius;       // radius (blocks) to randomize spawn positions around center
    private final int maxNearby;         // maximum nearby NPCs allowed
    private final int maxAttempts;       // maximum spawn attempts per activation before giving up
    private final boolean debug;

    /**
     * @param spawnTypeId mob type id (passed to NPCPlugin.get().getIndex)
     * @param spawnCount number of mobs to try spawn each activation
     * @param spawnRadius radius for random offsets (x/z), also used for nearby cap bounding box
     * @param cooldownMillis not used directly here (ProximitySpawnSystem tracks cooldown) but kept for parity
     * @param maxNearby maximum number of similar NPCs allowed nearby (skip spawn if exceeded)
     * @param maxAttempts maximum attempts to find valid spawn positions
     * @param debug whether to emit detailed logs for each attempt
     */
    public ProgrammaticSpawnStrategy(String spawnTypeId, int spawnCount, int spawnRadius, long cooldownMillis, int maxNearby, int maxAttempts, boolean debug) {
        this.spawnTypeId = spawnTypeId;
        this.spawnCount = Math.max(1, spawnCount);
        this.spawnRadius = Math.max(0, spawnRadius);
        this.maxNearby = Math.max(0, maxNearby);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.debug = debug;
    }

    @Override
    public void spawn(World world, double x, double y, double z, PlayerRef trigger, Player triggerPlayer) {
        if (world == null) {
            LOG.atWarning().log("[ProgSpawn] world is null; aborting spawn of %s", spawnTypeId);
            return;
        }

        try {
            if (debug) LOG.atInfo().log("[ProgSpawn] triggered spawn for type=%s at (%.1f,%.1f,%.1f) by=%s", spawnTypeId, x, y, z, safePlayerString(trigger));

            final Store<EntityStore> entityStore = world.getEntityStore().getStore();
            if (entityStore == null) {
                LOG.atWarning().log("[ProgSpawn] entity store missing for world; aborting spawn");
                return;
            }

            // Nearby cap check first (cheap)
            double width = spawnRadius + 1;
            double height = spawnRadius + 1;
            ObjectList<Ref<EntityStore>> nearby = new ObjectArrayList<>();
            TargetUtil.getAllEntitiesInBox(
                    new Vector3d(x - width, y - height, z - width),
                    new Vector3d(x + width, y + height, z + width),
                    entityStore
            ).forEach(nearby::add);

            AtomicInteger nearbyNPCCount = new AtomicInteger(0);
            ComponentType<EntityStore, NPCEntity> npcComponentType = NPCEntity.getComponentType();
            nearby.forEach(entityRef -> {
                if (entityRef == null || !entityRef.isValid()) return;
                try {
                    if (entityRef.getStore().getComponent(entityRef, npcComponentType) != null) nearbyNPCCount.getAndIncrement();
                } catch (Throwable ignored) {}
            });

            if (nearbyNPCCount.get() > this.maxNearby) {
                if (debug) LOG.atInfo().log("[ProgSpawn] nearby NPC count %d exceeds maxNearby=%d -> skip spawn", nearbyNPCCount.get(), this.maxNearby);
                return;
            }

            // Do spawn attempts (up to maxAttempts); for each attempt try to spawn spawnCount creatures
            int attempts = 0;
            int spawnedTotal = 0;

            int roleIndex = NPCPlugin.get().getIndex(spawnTypeId);
            if (roleIndex < 0) {
                LOG.atWarning().log("[ProgSpawn] NPCPlugin has no index for spawn type '%s' (index=%d). Aborting programmatic spawn.", spawnTypeId, roleIndex);
                return;
            }
            // try get role builder / cached role
            var roleBuilder = NPCPlugin.get().tryGetCachedValidRole(roleIndex);
            if (roleBuilder == null) {
                LOG.atWarning().log("[ProgSpawn] NPCPlugin returned null role builder for type '%s' (index=%d). Aborting programmatic spawn.", spawnTypeId, roleIndex);
                return;
            }

            while (attempts < this.maxAttempts && spawnedTotal < this.spawnCount) {
                attempts++;

                // choose a randomized spawn position within spawnRadius
                double dx = (Math.random() * (2 * spawnRadius + 1)) - spawnRadius;
                double dz = (Math.random() * (2 * spawnRadius + 1)) - spawnRadius;
                double dy = 0; // small vertical variance could be added

                Vector3d candidate = new Vector3d(x + dx, y + dy, z + dz);

                Vector3d valid = getValidSpawnPoint(world, candidate, 10);
                if (valid == null) {
                    if (debug) LOG.atInfo().log("[ProgSpawn] attempt %d: no valid spawn point near (%.2f,%.2f,%.2f)", attempts, candidate.getX(), candidate.getY(), candidate.getZ());
                    continue;
                }

                // re-check nearby cap at the exact spot (smaller box)
                double checkWidth = Math.max(1.0, spawnRadius);
                ObjectList<Ref<EntityStore>> near2 = new ObjectArrayList<>();
                TargetUtil.getAllEntitiesInBox(
                        new Vector3d(valid.getX() - checkWidth, valid.getY() - 1, valid.getZ() - checkWidth),
                        new Vector3d(valid.getX() + checkWidth, valid.getY() + 1, valid.getZ() + checkWidth),
                        entityStore
                ).forEach(near2::add);

                AtomicInteger countAtSpot = new AtomicInteger(0);
                near2.forEach(entityRef -> {
                    if (entityRef == null || !entityRef.isValid()) return;
                    try {
                        if (entityRef.getStore().getComponent(entityRef, npcComponentType) != null) countAtSpot.getAndIncrement();
                    } catch (Throwable ignored) {}
                });

                if (countAtSpot.get() > this.maxNearby) {
                    if (debug) LOG.atInfo().log("[ProgSpawn] attempt %d: nearby count at spot %d exceeds maxNearby=%d -> skipping spot", attempts, countAtSpot.get(), this.maxNearby);
                    continue;
                }

                // Attempt spawn via NPCPlugin
                try {
                    SpawningContext spawningContext = new SpawningContext();
                    ISpawnableWithModel spawnable = (ISpawnableWithModel) roleBuilder;
                    if (!spawningContext.setSpawnable(spawnable)) {
                        LOG.atWarning().log("[ProgSpawn] SpawningContext.setSpawnable returned false for type %s", spawnTypeId);
                        continue;
                    }
                    Model model = spawningContext.getModel();

                    // spawnEntity expects (store, roleIndex, position, rotation, model, whatever)
                    Pair<Ref<EntityStore>, com.hypixel.hytale.server.npc.entities.NPCEntity> spawned = NPCPlugin.get().spawnEntity(
                            world.getEntityStore().getStore(),
                            roleIndex,
                            valid.add(0.5, 0, 0.5),
                            new Vector3f(0, 0, 0),
                            model,
                            null
                    );

                    if (spawned != null) {
                        spawnedTotal++;
                        if (debug) LOG.atInfo().log("[ProgSpawn] spawned entity for type=%s at (%.2f,%.2f,%.2f) (attempt %d)", spawnTypeId, valid.getX(), valid.getY(), valid.getZ(), attempts);
                    } else {
                        if (debug) LOG.atWarning().log("[ProgSpawn] NPCPlugin.spawnEntity returned null for type=%s at (%.2f,%.2f,%.2f)", spawnTypeId, valid.getX(), valid.getY(), valid.getZ());
                    }
                } catch (Throwable t) {
                    LOG.atWarning().withCause(t).log("[ProgSpawn] exception while attempting programmatic spawn of %s", spawnTypeId);
                }
            } // end attempts loop

            if (debug) LOG.atInfo().log("[ProgSpawn] spawn run finished for %s: attempted=%d spawned=%d", spawnTypeId, attempts, spawnedTotal);

        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[ProgSpawn] unexpected error in ProgrammaticSpawnStrategy.spawn");
        }
    }

    /**
     * Try to find a valid spawn location by climbing up from origin until an empty block is found.
     * Returns the valid position or null if none found within limit steps.
     */
    private Vector3d getValidSpawnPoint(World world, Vector3d origin, int limit) {
        Vector3d pos = new Vector3d(origin.getX(), origin.getY(), origin.getZ());
        for (int i = 0; i < limit; i++) {
            try {
                BlockType type = world.getBlockType(pos.toVector3i());
                if (type == null || type.equals(BlockType.EMPTY)) return pos;
                pos.add(0, 1, 0);
            } catch (Throwable ignored) {
                // if query fails, bail
                return null;
            }
        }
        return null;
    }

    private static String safePlayerString(PlayerRef ref) {
        try {
            if (ref == null) return "<null-ref>";
            String name = ref.getUsername();
            return (name == null || name.isBlank()) ? ("playerRef@" + ref.hashCode()) : name;
        } catch (Throwable t) {
            return "playerRef@" + (ref == null ? "null" : ref.hashCode());
        }
    }
}