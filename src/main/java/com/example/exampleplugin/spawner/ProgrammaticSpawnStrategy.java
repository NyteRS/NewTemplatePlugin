package com.example.exampleplugin.spawner;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.spawning.SpawningContext;
import com.hypixel.hytale.server.spawning.ISpawnableWithModel;
import it.unimi.dsi.fastutil.Pair;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ProgrammaticSpawnStrategy — uses NPCPlugin spawn API to create entities.
 *
 * Added support for spawnOnExact:
 *  - if spawnOnExact==true, attempt to spawn exactly at the configured coordinate (subject to spawningContext tests)
 *  - if spawnOnExact==false, choose randomized offsets inside spawnRadius as before
 */
public class ProgrammaticSpawnStrategy implements ProximitySpawnSystem.SpawnStrategy {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final String spawnTypeId;
    private final int spawnCount;
    private final int spawnRadius;
    private final int maxNearby;
    private final int maxAttempts;
    private final boolean debug;
    private final boolean spawnOnExact;

    public ProgrammaticSpawnStrategy(String spawnTypeId, int spawnCount, int spawnRadius, int maxNearby, int maxAttempts, boolean debug, boolean spawnOnExact) {
        this.spawnTypeId = spawnTypeId;
        this.spawnCount = Math.max(1, spawnCount);
        this.spawnRadius = Math.max(0, spawnRadius);
        this.maxNearby = Math.max(0, maxNearby);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.debug = debug;
        this.spawnOnExact = spawnOnExact;
    }

    @Override
    public void spawn(World world, double x, double y, double z, PlayerRef trigger, Player triggerPlayer) {
        if (world == null) {
            LOG.atWarning().log("[ProgrammaticSpawn] world is null; aborting spawn for %s", spawnTypeId);
            return;
        }

        try {
            if (debug) LOG.atInfo().log("[ProgrammaticSpawn] triggered spawn for type=%s at (%.1f,%.1f,%.1f) exact=%s by=%s",
                    spawnTypeId, x, y, z, spawnOnExact, safePlayerString(trigger));

            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            if (entityStore == null) {
                LOG.atWarning().log("[ProgrammaticSpawn] entity store missing; aborting spawn");
                return;
            }

            // Attempt to discover NPC component type reflectively
            Object npcComponentType = null;
            try {
                Class<?> npcClass = Class.forName("com.hypixel.hytale.server.core.entity.entities.npc.NPCEntity");
                Method getCompType = npcClass.getMethod("getComponentType");
                npcComponentType = getCompType.invoke(null);
            } catch (Throwable ignored) {
                npcComponentType = null;
            }

            // Broad nearby check
            double boxW = spawnRadius + 1.0;
            double boxH = spawnRadius + 1.0;
            List<Ref<EntityStore>> nearbyRefs = new ArrayList<>();
            TargetUtil.getAllEntitiesInBox(
                    new Vector3d(x - boxW, y - boxH, z - boxW),
                    new Vector3d(x + boxW, y + boxH, z + boxW),
                    entityStore
            ).forEach(r -> nearbyRefs.add(r));

            AtomicInteger nearbyNPCCount = new AtomicInteger(0);
            try {
                for (Ref<EntityStore> r : nearbyRefs) {
                    if (r == null || !r.isValid()) continue;
                    boolean counted = false;
                    if (npcComponentType != null) {
                        try {
                            Object comp = r.getStore().getComponent(r, (com.hypixel.hytale.component.ComponentType) npcComponentType);
                            if (comp != null) {
                                nearbyNPCCount.getAndIncrement();
                                counted = true;
                            }
                        } catch (Throwable ignored) {}
                    }
                    if (!counted) {
                        try {
                            Object playerComp = r.getStore().getComponent(r, Player.getComponentType());
                            if (playerComp == null) nearbyNPCCount.getAndIncrement();
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}

            if (nearbyNPCCount.get() > maxNearby) {
                if (debug) LOG.atInfo().log("[ProgrammaticSpawn] nearby count %d exceeds maxNearby=%d -> skip spawn", nearbyNPCCount.get(), maxNearby);
                return;
            }

            // Role index & builder
            int roleIndex = NPCPlugin.get().getIndex(spawnTypeId);
            if (debug) LOG.atInfo().log("[ProgrammaticSpawn] roleIndex for '%s' -> %d", spawnTypeId, roleIndex);
            if (roleIndex < 0) {
                LOG.atWarning().log("[ProgrammaticSpawn] Invalid role name '%s' (index %d). Aborting.", spawnTypeId, roleIndex);
                return;
            }
            var roleBuilder = NPCPlugin.get().tryGetCachedValidRole(roleIndex);
            if (roleBuilder == null) {
                LOG.atWarning().log("[ProgrammaticSpawn] NPCPlugin returned null role builder for %s", spawnTypeId);
                return;
            }

            int attempts = 0;
            int spawnedTotal = 0;

            // If spawnOnExact, candidate is fixed at provided coordinates. We'll still allow multiple spawnCount but attempts iterate for spawnCount only.
            if (spawnOnExact) {
                // Attempt to spawn spawnCount entities exactly at center (but run the same checks per entity)
                for (int i = 0; i < spawnCount; i++) {
                    attempts++;
                    Vector3d candidate = new Vector3d(x, y, z);
                    Vector3d valid = getValidSpawnPoint(world, candidate, 10);
                    if (valid == null) {
                        if (debug) LOG.atInfo().log("[ProgrammaticSpawn] exact spawn: no valid spawn point at (%.2f,%.2f,%.2f)", candidate.getX(), candidate.getY(), candidate.getZ());
                        break;
                    }

                    // check nearby at spot
                    double checkW = Math.max(1.0, spawnRadius);
                    List<Ref<EntityStore>> near2 = new ArrayList<>();
                    TargetUtil.getAllEntitiesInBox(
                            new Vector3d(valid.getX() - checkW, valid.getY() - 1, valid.getZ() - checkW),
                            new Vector3d(valid.getX() + checkW, valid.getY() + 1, valid.getZ() + checkW),
                            entityStore
                    ).forEach(r -> near2.add(r));

                    AtomicInteger countAtSpot = new AtomicInteger(0);
                    try {
                        for (Ref<EntityStore> r : near2) {
                            if (r == null || !r.isValid()) continue;
                            boolean counted = false;
                            if (npcComponentType != null) {
                                try {
                                    Object comp = r.getStore().getComponent(r, (com.hypixel.hytale.component.ComponentType) npcComponentType);
                                    if (comp != null) {
                                        countAtSpot.getAndIncrement();
                                        counted = true;
                                    }
                                } catch (Throwable ignored) {}
                            }
                            if (!counted) {
                                try {
                                    Object playerComp = r.getStore().getComponent(r, Player.getComponentType());
                                    if (playerComp == null) countAtSpot.getAndIncrement();
                                } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable ignored) {}

                    if (countAtSpot.get() > maxNearby) {
                        if (debug) LOG.atInfo().log("[ProgrammaticSpawn] exact spawn: nearby cap at spot (%d) exceeded, skipping", countAtSpot.get());
                        break;
                    }

                    // spawningContext / spawnEntity flow
                    try {
                        SpawningContext spawningContext = new SpawningContext();
                        ISpawnableWithModel spawnable = (ISpawnableWithModel) roleBuilder;
                        if (!spawningContext.setSpawnable(spawnable)) {
                            LOG.atWarning().log("[ProgrammaticSpawn] setSpawnable false for %s", spawnTypeId);
                            break;
                        }
                        boolean posSet;
                        try {
                            posSet = spawningContext.set(world, valid.getX(), valid.getY(), valid.getZ());
                        } catch (Throwable t) {
                            posSet = false;
                        }
                        if (!posSet) {
                            if (debug) LOG.atInfo().log("[ProgrammaticSpawn] spawningContext.set failed for exact spawn at %s", valid);
                            break;
                        }

                        Model model = spawningContext.getModel();
                        Vector3d spawnPos = spawningContext.newPosition();

                        var spawned = NPCPlugin.get().spawnEntity(world.getEntityStore().getStore(),
                                roleIndex,
                                spawnPos,
                                new Vector3f(0, 0, 0),
                                model,
                                null);

                        if (spawned != null && spawned.first() != null && spawned.second() != null) {
                            spawnedTotal++;
                            if (debug) LOG.atInfo().log("[ProgrammaticSpawn] exact spawned %s at %s", spawnTypeId, spawnPos);
                        } else {
                            if (debug) LOG.atWarning().log("[ProgrammaticSpawn] exact spawn returned null for %s", spawnTypeId);
                        }

                    } catch (Throwable t) {
                        LOG.atWarning().withCause(t).log("[ProgrammaticSpawn] exact spawn exception for %s", spawnTypeId);
                    }
                } // end for spawnCount
            } else {
                // Non-exact behavior: try random offsets until we've spawned spawnCount or exhausted maxAttempts
                while (attempts < maxAttempts && spawnedTotal < spawnCount) {
                    attempts++;

                    int offsetX = (int) (Math.random() * (2 * spawnRadius + 1)) - spawnRadius;
                    int offsetZ = (int) (Math.random() * (2 * spawnRadius + 1)) - spawnRadius;
                    int offsetY = 0;

                    Vector3d candidate = new Vector3d(x + offsetX, y + offsetY, z + offsetZ);

                    Vector3d valid = getValidSpawnPoint(world, candidate, 10);
                    if (valid == null) {
                        if (debug) LOG.atInfo().log("[ProgrammaticSpawn] attempt %d: no valid spawn point near (%.2f,%.2f,%.2f)", attempts, candidate.getX(), candidate.getY(), candidate.getZ());
                        continue;
                    }

                    // Re-check nearby at spot
                    double checkW = Math.max(1.0, spawnRadius);
                    List<Ref<EntityStore>> near2 = new ArrayList<>();
                    TargetUtil.getAllEntitiesInBox(
                            new Vector3d(valid.getX() - checkW, valid.getY() - 1, valid.getZ() - checkW),
                            new Vector3d(valid.getX() + checkW, valid.getY() + 1, valid.getZ() + checkW),
                            entityStore
                    ).forEach(r -> near2.add(r));

                    AtomicInteger countAtSpot = new AtomicInteger(0);
                    try {
                        for (Ref<EntityStore> r : near2) {
                            if (r == null || !r.isValid()) continue;
                            boolean counted = false;
                            if (npcComponentType != null) {
                                try {
                                    Object comp = r.getStore().getComponent(r, (com.hypixel.hytale.component.ComponentType) npcComponentType);
                                    if (comp != null) {
                                        countAtSpot.getAndIncrement();
                                        counted = true;
                                    }
                                } catch (Throwable ignored) {}
                            }
                            if (!counted) {
                                try {
                                    Object playerComp = r.getStore().getComponent(r, Player.getComponentType());
                                    if (playerComp == null) countAtSpot.getAndIncrement();
                                } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable ignored) {}

                    if (countAtSpot.get() > maxNearby) {
                        if (debug) LOG.atInfo().log("[ProgrammaticSpawn] attempt %d: nearby count at spot %d exceeds maxNearby=%d -> skipping spot", attempts, countAtSpot.get(), maxNearby);
                        continue;
                    }

                    // Spawn via NPCPlugin (same as before)
                    try {
                        SpawningContext spawningContext = new SpawningContext();
                        ISpawnableWithModel spawnable = (ISpawnableWithModel) roleBuilder;
                        if (!spawningContext.setSpawnable(spawnable)) {
                            LOG.atWarning().log("[ProgrammaticSpawn] setSpawnable false for %s", spawnTypeId);
                            continue;
                        }
                        boolean posSet;
                        try {
                            posSet = spawningContext.set(world, valid.getX(), valid.getY(), valid.getZ());
                        } catch (Throwable t) {
                            posSet = false;
                        }
                        if (!posSet) {
                            if (debug) LOG.atInfo().log("[ProgrammaticSpawn] spawningContext.set failed for candidate %s", valid);
                            continue;
                        }

                        Model model = spawningContext.getModel();
                        Vector3d spawnPos = spawningContext.newPosition();

                        var spawned = NPCPlugin.get().spawnEntity(world.getEntityStore().getStore(),
                                roleIndex,
                                spawnPos,
                                new Vector3f(0, 0, 0),
                                model,
                                null);

                        if (spawned != null && spawned.first() != null && spawned.second() != null) {
                            spawnedTotal++;
                            if (debug) LOG.atInfo().log("[ProgrammaticSpawn] spawned %s at %s (attempt %d)", spawnTypeId, spawnPos, attempts);
                        } else {
                            if (debug) LOG.atWarning().log("[ProgrammaticSpawn] spawnEntity returned null for %s (attempt %d)", spawnTypeId, attempts);
                        }
                    } catch (Throwable t) {
                        LOG.atWarning().withCause(t).log("[ProgrammaticSpawn] exception while attempting programmatic spawn of %s", spawnTypeId);
                    }
                } // end while
            } // end spawnOnExact branch

            if (debug) LOG.atInfo().log("[ProgrammaticSpawn] spawn run finished for %s: attempted=%d spawned=%d", spawnTypeId, attempts, spawnedTotal);

        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[ProgrammaticSpawn] unexpected error in spawn()");
        }
    }

    private Vector3d getValidSpawnPoint(World world, Vector3d origin, int limit) {
        Vector3d pos = new Vector3d(origin.getX(), origin.getY(), origin.getZ());
        for (int i = 0; i < limit; i++) {
            try {
                BlockType type = world.getBlockType(pos.toVector3i());
                if (type == null || type.equals(BlockType.EMPTY)) return pos;
                pos = new Vector3d(pos.getX(), pos.getY() + 1.0, pos.getZ());
            } catch (Throwable ignored) {
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