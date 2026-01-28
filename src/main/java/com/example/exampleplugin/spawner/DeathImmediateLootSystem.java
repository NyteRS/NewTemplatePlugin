package com.example.exampleplugin.spawner;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * Robust death-time system: uses Query.any() and inspects each added entity to see if it carries
 * a DeathComponent. Logs everything and records deaths for the cleaner.
 */
public final class DeathImmediateLootSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final ItemSpawnCleaner cleaner;

    public DeathImmediateLootSystem(ItemSpawnCleaner cleaner) {
        this.cleaner = cleaner;
        LOG.atInfo().log("[DeathImmediateLootSystem] constructed");
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // Use Query.any() so we cannot miss the add event due to archetype mismatch.
        return Query.any();
    }

    @Nonnull
    @Override
    public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
        // Try to run before the engine NPC/player drop systems and before NPCSystems.OnDeathSystem (if present).
        return java.util.Set.of(
                new SystemDependency(Order.BEFORE, com.hypixel.hytale.server.npc.systems.NPCDamageSystems.DropDeathItems.class),
                new SystemDependency(Order.BEFORE, com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems.DropPlayerDeathItems.class),
                // also try to run before NPCSystems.OnDeathSystem where that exists
                new SystemDependency(Order.BEFORE, com.hypixel.hytale.server.npc.systems.NPCSystems.OnDeathSystem.class)
        );
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            // Inspect the entity for DeathComponent at runtime
            DeathComponent death = (DeathComponent) commandBuffer.getComponent(ref, DeathComponent.getComponentType());
            if (death == null) return;

            LOG.atInfo().log("[DeathImmediateLoot] onEntityAdded ref=%d reason=%s deathInfo=%s", ref.getIndex(), reason, death.getDeathInfo());

            Damage deathInfo = death.getDeathInfo();
            if (deathInfo == null) {
                LOG.atInfo().log("[DeathImmediateLoot] no deathInfo for ref=%d", ref.getIndex());
                return;
            }

            if (!(deathInfo.getSource() instanceof Damage.EntitySource)) {
                LOG.atInfo().log("[DeathImmediateLoot] death.source not entity (ref=%d)", ref.getIndex());
                return;
            }

            Ref<EntityStore> attackerRef = ((Damage.EntitySource) deathInfo.getSource()).getRef();
            if (attackerRef == null || !attackerRef.isValid()) {
                LOG.atInfo().log("[DeathImmediateLoot] attackerRef null/invalid for ref=%d", ref.getIndex());
                return;
            }

            // record the death for the cleaner always (so cleaner can claim spawned items)
            TransformComponent tx = (TransformComponent) commandBuffer.getComponent(ref, TransformComponent.getComponentType());
            Vector3d deathPos = (tx != null) ? tx.getPosition().clone() : null;
            cleaner.recordDeath(ref.getIndex(), deathPos, attackerRef);
            LOG.atInfo().log("[DeathImmediateLoot] recorded death for cleaner: deadRef=%d attacker=%d pos=%s", ref.getIndex(), attackerRef.getIndex(), deathPos);

            // Only give to players
            Player attackerPlayer = (Player) commandBuffer.getComponent(attackerRef, Player.getComponentType());
            if (attackerPlayer == null) {
                LOG.atInfo().log("[DeathImmediateLoot] attacker is not a player for ref=%d attackerRef=%d", ref.getIndex(), attackerRef.getIndex());
                return;
            }

            // 1) Try DeathComponent.getItemsLostOnDeath()
            ItemStack[] itemsLost = death.getItemsLostOnDeath();
            if (itemsLost != null && itemsLost.length > 0) {
                LOG.atInfo().log("[DeathImmediateLoot] giving %d stacks from DeathComponent to attacker=%d", itemsLost.length, attackerRef.getIndex());
                for (ItemStack s : itemsLost) {
                    if (s == null) continue;
                    LOG.atInfo().log("[DeathImmediateLoot] calling ItemUtils.interactivelyPickupItem for stack=%s", s);
                    ItemUtils.interactivelyPickupItem(attackerRef, s, deathPos, (ComponentAccessor<EntityStore>) commandBuffer);
                }
                death.setItemsLostOnDeath(Collections.emptyList());
                LOG.atInfo().log("[DeathImmediateLoot] cleared itemsLostOnDeath for deadRef=%d", ref.getIndex());
                return;
            }

            // 2) If NPC, try role drop list
            NPCEntity npc = (NPCEntity) commandBuffer.getComponent(ref, NPCEntity.getComponentType());
            if (npc != null) {
                String dropListId = tryExtractDropListIdFromRole(npc.getRole());
                LOG.atInfo().log("[DeathImmediateLoot] extracted dropListId=%s for deadRef=%d", dropListId, ref.getIndex());
                if (dropListId != null) {
                    List<ItemStack> drops = ItemModule.get().getRandomItemDrops(dropListId);
                    LOG.atInfo().log("[DeathImmediateLoot] ItemModule.getRandomItemDrops -> %d stacks", drops == null ? 0 : drops.size());
                    if (drops != null && !drops.isEmpty()) {
                        for (ItemStack stack : drops) {
                            if (stack == null) continue;
                            LOG.atInfo().log("[DeathImmediateLoot] giving generated stack=%s to attacker=%d", stack, attackerRef.getIndex());
                            ItemUtils.interactivelyPickupItem(attackerRef, stack, deathPos, (ComponentAccessor<EntityStore>) commandBuffer);
                        }
                        death.setItemsLostOnDeath(Collections.emptyList());
                        LOG.atInfo().log("[DeathImmediateLoot] handed droplist items and cleared DeathComponent for deadRef=%d", ref.getIndex());
                        return;
                    }
                }
            }

            LOG.atInfo().log("[DeathImmediateLoot] nothing handed for deadRef=%d", ref.getIndex());
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[DeathImmediateLoot] error handling death ref=%d", ref.getIndex());
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // keep deathRecords until TTL expires in cleaner
    }

    // same reflection helper as before; keeps safe attempts to find dropListId
    private static String tryExtractDropListIdFromRole(Object role) {
        if (role == null) return null;
        try {
            try { var m = role.getClass().getMethod("getDropListId"); Object v = m.invoke(role); if (v instanceof String) return (String)v; } catch (NoSuchMethodException ignored) {}
            try { var m = role.getClass().getMethod("getDroplistId"); Object v = m.invoke(role); if (v instanceof String) return (String)v; } catch (NoSuchMethodException ignored) {}
            try { var m = role.getClass().getMethod("getRoleAsset"); Object asset = m.invoke(role); if (asset != null) {
                try { var md = asset.getClass().getMethod("getDropListId"); Object v = md.invoke(asset); if (v instanceof String) return (String)v; } catch (NoSuchMethodException ignored2) {}
            } } catch (NoSuchMethodException ignored) {}
        } catch (Throwable t) {}
        return null;
    }
}