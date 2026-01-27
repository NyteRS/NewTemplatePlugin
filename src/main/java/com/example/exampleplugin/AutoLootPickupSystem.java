package com.example.exampleplugin;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerItemEntityPickupSystem;
import com.hypixel.hytale.server.npc.systems.NPCDamageSystems;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans item entities and auto-picks up items that were spawned by a recent death
 * for the recorded attacker player.
 *
 * Runs AFTER NPC death/drop systems and BEFORE the built-in PlayerItemEntityPickupSystem
 * so it gets the first chance to claim newly spawned item entities.
 */
public final class AutoLootPickupSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private static final class DeathInfo {
        final Vector3d pos;
        final Ref<EntityStore> attacker;
        final long timestamp;

        DeathInfo(Vector3d pos, Ref<EntityStore> attacker, long timestamp) {
            this.pos = pos;
            this.attacker = attacker;
            this.timestamp = timestamp;
        }
    }

    private final Map<Integer, DeathInfo> deathMap = new ConcurrentHashMap<>();

    // tuning
    private final double pickupRadius = 3.0;       // how far from the death position we accept items
    private final long expiryMs = 5000L;           // how long we keep a death record (ms)

    private final Query<EntityStore> itemQuery;

    public AutoLootPickupSystem() {
        ComponentType<EntityStore, ItemComponent> itemType = ItemComponent.getComponentType();
        ComponentType<EntityStore, TransformComponent> transformType = TransformComponent.getComponentType();

        if (itemType == null || transformType == null) {
            this.itemQuery = Query.any();
        } else {
            // iterate entities that have both ItemComponent and TransformComponent
            this.itemQuery = Archetype.of(new ComponentType[]{ itemType, transformType });
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return this.itemQuery;
    }

    @Nonnull
    @Override
    public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
        // Run after the server NPC drop item system and before the player pickup system.
        return java.util.Set.of(
                new SystemDependency(Order.AFTER, NPCDamageSystems.DropDeathItems.class),
                new SystemDependency(Order.BEFORE, PlayerItemEntityPickupSystem.class)
        );
    }

    /**
     * Called by a death-recording system to register a death that may produce loot.
     * The key used here is the dead entity index for a short window.
     */
    public void recordDeath(Ref<EntityStore> deadEntityRef, Vector3d pos, Ref<EntityStore> attackerRef) {
        if (attackerRef == null) return;
        deathMap.put(deadEntityRef.getIndex(), new DeathInfo(pos.clone(), attackerRef, System.currentTimeMillis()));
    }

    /**
     * Remove a death record (used if the dead entity is removed early).
     */
    public void removeDeathRecord(int deadEntityIndex) {
        deathMap.remove(deadEntityIndex);
    }

    @Override
    public void tick(float deltaTime, int itemEntityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        try {
            Ref<EntityStore> itemRef = chunk.getReferenceTo(itemEntityIndex);
            ItemComponent itemComponent = (ItemComponent) chunk.getComponent(itemEntityIndex, ItemComponent.getComponentType());
            if (itemComponent == null) return;

            TransformComponent transform = (TransformComponent) chunk.getComponent(itemEntityIndex, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d itemPos = transform.getPosition();
            long now = System.currentTimeMillis();

            for (Map.Entry<Integer, DeathInfo> e : deathMap.entrySet()) {
                DeathInfo info = e.getValue();

                // expire old records
                if (now - info.timestamp > expiryMs) {
                    deathMap.remove(e.getKey());
                    continue;
                }

                double dist = itemPos.distanceTo(info.pos); // use distanceTo(...)
                if (dist <= pickupRadius) {
                    Ref<EntityStore> attacker = info.attacker;
                    if (attacker != null && attacker.isValid()) {
                        ItemStack itemStack = itemComponent.getItemStack();
                        if (itemStack == null || itemStack.isEmpty()) {
                            // nothing to pick up
                        } else {
                            // Use ItemUtils to attempt to give the item to the player.
                            // CommandBuffer implements ComponentAccessor; cast to satisfy API.
                            ItemUtils.interactivelyPickupItem(attacker, itemStack, itemPos, (ComponentAccessor<EntityStore>) commandBuffer);

                            // Remove the spawned item entity to avoid duplication and to prevent the normal pickup.
                            commandBuffer.removeEntity(itemRef, RemoveReason.REMOVE);

                            LOG.atInfo().log("[AutoLootPickup] Auto-picked %s for player (refIndex=%d)", itemStack, attacker.getIndex());
                        }
                    }

                    // Once one death record handled this item, stop checking others.
                    break;
                }
            }
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("AutoLootPickupSystem tick failure");
        }
    }
}