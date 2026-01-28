package com.example.exampleplugin.spawner;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.entity.ItemUtils;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans newly spawned item entities; if they are near a recently-recorded death, immediately
 * hand the item stack to the recorded attacker and remove the entity.
 *
 * Dedupe is per-death-aware: we skip only when an itemRef was already handled at/after the death timestamp.
 */
public final class ItemSpawnCleaner extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private static final class DeathRecord {
        final Vector3d pos;
        final Ref<EntityStore> attacker;
        final long time;

        DeathRecord(Vector3d pos, Ref<EntityStore> attacker, long time) { this.pos = pos; this.attacker = attacker; this.time = time; }
    }

    // deathIndex -> DeathRecord
    private final Map<Integer, DeathRecord> deathRecords = new ConcurrentHashMap<>();

    // itemRefIndex -> last handled timestamp (ms)
    private final Map<Integer, Long> processedItemTimestamps = new ConcurrentHashMap<>();

    // tuning
    private double matchRadiusMeters = 5.0;
    private long ttlMs = 10_000L;

    // after we mark an item as processed, keep that record for this many ms to avoid reprocessing
    private long processedItemRetentionMs = 30_000L;

    public ItemSpawnCleaner() {
        LOG.atInfo().log("[ItemSpawnCleaner] constructed (matchRadius=%.1f ttlMs=%d processedRetentionMs=%d)", matchRadiusMeters, ttlMs, processedItemRetentionMs);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        ComponentType<EntityStore, ItemComponent> it = ItemComponent.getComponentType();
        ComponentType<EntityStore, TransformComponent> tt = TransformComponent.getComponentType();
        return (it == null || tt == null) ? Query.any() : Archetype.of(new ComponentType[]{ it, tt });
    }

    @Nonnull
    @Override
    public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
        return java.util.Set.of(
                new SystemDependency(Order.AFTER, com.hypixel.hytale.server.npc.systems.NPCDamageSystems.DropDeathItems.class),
                new SystemDependency(Order.AFTER, com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems.DropPlayerDeathItems.class),
                new SystemDependency(Order.BEFORE, com.hypixel.hytale.server.core.modules.entity.player.PlayerItemEntityPickupSystem.class)
        );
    }

    /**
     * Called by the death watcher when a death is observed.
     */
    public void recordDeath(int deadEntityIndex, Vector3d pos, Ref<EntityStore> attackerRef) {
        if (pos == null || attackerRef == null) {
            LOG.atInfo().log("[ItemSpawnCleaner] recordDeath called with null pos/attacker (deadIndex=%d)", deadEntityIndex);
            return;
        }
        DeathRecord dr = new DeathRecord(pos.clone(), attackerRef, System.currentTimeMillis());
        deathRecords.put(deadEntityIndex, dr);
        LOG.atInfo().log("[ItemSpawnCleaner] recorded death index=%d attacker=%d pos=%s (records=%d)", deadEntityIndex, attackerRef.getIndex(), pos, deathRecords.size());
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            // prune old processed item entries occasionally
            pruneProcessedItems();

            if (deathRecords.isEmpty()) return; // nothing to do

            Ref<EntityStore> itemRef = chunk.getReferenceTo(index);
            ItemComponent itemComp = (ItemComponent) chunk.getComponent(index, ItemComponent.getComponentType());
            TransformComponent tx = (TransformComponent) chunk.getComponent(index, TransformComponent.getComponentType());
            if (itemComp == null || tx == null) return;

            int itemIndex = itemRef.getIndex();
            Vector3d itemPos = tx.getPosition();
            long now = System.currentTimeMillis();

            // For each death record: decide if item is within radius, and if we should handle it.
            for (Map.Entry<Integer, DeathRecord> e : deathRecords.entrySet()) {
                int deadIndex = e.getKey();
                DeathRecord dr = e.getValue();

                if (now - dr.time > ttlMs) {
                    deathRecords.remove(deadIndex);
                    LOG.atInfo().log("[ItemSpawnCleaner] expired death record index=%d", deadIndex);
                    continue;
                }

                double dist = itemPos.distanceTo(dr.pos);
                if (dist > matchRadiusMeters) continue;

                ItemStack stack = itemComp.getItemStack();
                LOG.atInfo().log("[ItemSpawnCleaner] candidate spawned item ref=%d near death=%d dist=%.2f stack=%s", itemIndex, deadIndex, dist, stack);

                // Per-death-aware dedupe:
                Long lastHandled = processedItemTimestamps.get(itemIndex);
                if (lastHandled != null && lastHandled >= dr.time) {
                    // This itemRef was already handled at or after the death timestamp -> skip for this death.
                    LOG.atInfo().log("[ItemSpawnCleaner] skipping already-processed itemRef=%d (last=%d ms ago relative to now) for death=%d", itemIndex, now - lastHandled, deadIndex);
                    // don't break; maybe other deathRecords exist where lastHandled < that death time (unlikely) — continue checking.
                    continue;
                }

                Ref<EntityStore> attackerRef = dr.attacker;
                if (attackerRef == null || !attackerRef.isValid()) {
                    LOG.atInfo().log("[ItemSpawnCleaner] attackerRef invalid for death %d; removing record", deadIndex);
                    deathRecords.remove(deadIndex);
                    continue;
                }

                Object maybePlayer = commandBuffer.getComponent(attackerRef, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
                if (maybePlayer == null) {
                    LOG.atInfo().log("[ItemSpawnCleaner] attackerRef %d is not a player; removing record", attackerRef.getIndex());
                    deathRecords.remove(deadIndex);
                    continue;
                }

                // Mark as processed for this item BEFORE attempting pickup to avoid re-entrancy loops.
                processedItemTimestamps.put(itemIndex, now);

                try {
                    LOG.atInfo().log("[ItemSpawnCleaner] attempting pickup: itemRef=%d deadIndex=%d attacker=%d", itemIndex, deadIndex, attackerRef.getIndex());
                    ItemUtils.interactivelyPickupItem(attackerRef, stack, itemPos, (ComponentAccessor<EntityStore>) commandBuffer);

                    // Remove the spawned entity so nothing remains on the ground.
                    commandBuffer.removeEntity(itemRef, RemoveReason.REMOVE);

                    LOG.atInfo().log("[ItemSpawnCleaner] claimed itemRef=%d for attacker=%d and removed entity", itemIndex, attackerRef.getIndex());
                } catch (Throwable ex) {
                    LOG.atWarning().withCause(ex).log("[ItemSpawnCleaner] failed to interactively pickup itemRef=%d for attacker=%d", itemIndex, attackerRef.getIndex());
                }

                // We've handled this item for one death; stop checking other death records for it.
                break;
            }
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[ItemSpawnCleaner] tick error");
        }
    }

    private void pruneProcessedItems() {
        long now = System.currentTimeMillis();
        processedItemTimestamps.entrySet().removeIf(en -> (now - en.getValue()) > processedItemRetentionMs);
    }
}