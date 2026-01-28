package com.example.exampleplugin.spawner;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans entities for new DeathComponent instances and records deaths for ItemSpawnCleaner.
 * Record-only variant; processed-death dedupe is time-based to allow engine entity-index reuse.
 */
public final class DeathWatcherSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    // entityIndex -> last processed death timestamp (ms). We allow reprocessing after retention window.
    private final Map<Integer, Long> processedDeathTimestamps = new ConcurrentHashMap<>();

    // retention for processed-death entries (ms). Adjust as needed; 5000ms lets reused indices be reprocessed after 5s.
    private final long processedDeathRetentionMs = 5000L;

    private final ItemSpawnCleaner cleaner;

    public DeathWatcherSystem(ItemSpawnCleaner cleaner) {
        this.cleaner = cleaner;
        LOG.atInfo().log("[DeathWatcherSystem] constructed (record-only, retentionMs=%d)", processedDeathRetentionMs);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        ComponentType<EntityStore, DeathComponent> deathType = DeathComponent.getComponentType();
        return (deathType == null) ? Query.any() : Archetype.of(new ComponentType[]{ deathType });
    }

    @Nonnull
    @Override
    public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
        // Run BEFORE engine item-drop systems so we can record before they spawn items.
        return java.util.Set.of(
                new SystemDependency(Order.BEFORE, com.hypixel.hytale.server.npc.systems.NPCDamageSystems.DropDeathItems.class),
                new SystemDependency(Order.BEFORE, com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems.DropPlayerDeathItems.class)
        );
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            // prune old processed death timestamps occasionally
            pruneProcessedDeaths();

            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            DeathComponent death = (DeathComponent) chunk.getComponent(index, DeathComponent.getComponentType());
            if (death == null) return;

            int entityIndex = ref.getIndex();
            long now = System.currentTimeMillis();

            Long lastProcessed = processedDeathTimestamps.get(entityIndex);
            if (lastProcessed != null && (now - lastProcessed) < processedDeathRetentionMs) {
                // recently processed this entity index -> skip (to avoid duplicate handling during same death)
                return;
            }

            // record this observation as processed (timestamp)
            processedDeathTimestamps.put(entityIndex, now);

            LOG.atInfo().log("[DeathWatcher] detected death for entityRef=%d", entityIndex);

            // death position if available (for cleaner)
            TransformComponent tx = (TransformComponent) chunk.getComponent(index, TransformComponent.getComponentType());
            Vector3d deathPos = (tx != null) ? tx.getPosition().clone() : null;

            Damage deathInfo = death.getDeathInfo();
            if (deathInfo == null) {
                LOG.atInfo().log("[DeathWatcher] no deathInfo for entityRef=%d", entityIndex);
                return;
            }

            if (!(deathInfo.getSource() instanceof Damage.EntitySource)) {
                LOG.atInfo().log("[DeathWatcher] death source not entity for entityRef=%d", entityIndex);
                return;
            }

            Ref<EntityStore> attackerRef = ((Damage.EntitySource) deathInfo.getSource()).getRef();
            if (attackerRef == null || !attackerRef.isValid()) {
                LOG.atInfo().log("[DeathWatcher] attackerRef null/invalid for entityRef=%d", entityIndex);
                return;
            }

            // IMPORTANT: record the death for the cleaner and DO NOT transfer items here.
            cleaner.recordDeath(entityIndex, deathPos, attackerRef);
            LOG.atInfo().log("[DeathWatcher] recorded death for cleaner only: entityRef=%d attacker=%d pos=%s", entityIndex, attackerRef.getIndex(), deathPos);

            // Done.
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[DeathWatcher] error during tick");
        }
    }

    private void pruneProcessedDeaths() {
        long now = System.currentTimeMillis();
        processedDeathTimestamps.entrySet().removeIf(en -> (now - en.getValue()) > processedDeathRetentionMs);
    }
}