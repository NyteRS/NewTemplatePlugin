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
import java.util.List;
import java.util.Collections;

/**
 * Give death drops immediately to the killer (player) and record the death for cleanup.
 */
public final class DeathGiveSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final ItemSpawnCleaner cleaner; // call recordDeath on this

    public DeathGiveSystem(ItemSpawnCleaner cleaner) {
        this.cleaner = cleaner;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        var dt = DeathComponent.getComponentType();
        return (dt == null) ? Query.any() : Archetype.of(new ComponentType[]{ dt });
    }

    @Nonnull
    @Override
    public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
        return java.util.Set.of(
                new SystemDependency(Order.BEFORE, com.hypixel.hytale.server.npc.systems.NPCDamageSystems.DropDeathItems.class),
                new SystemDependency(Order.BEFORE, com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems.DropPlayerDeathItems.class)
        );
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> deadRef, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            DeathComponent death = (DeathComponent) commandBuffer.getComponent(deadRef, DeathComponent.getComponentType());
            if (death == null) return;

            Damage deathInfo = death.getDeathInfo();
            if (deathInfo == null) return;
            if (!(deathInfo.getSource() instanceof Damage.EntitySource)) return;

            Ref<EntityStore> attackerRef = ((Damage.EntitySource) deathInfo.getSource()).getRef();
            if (attackerRef == null || !attackerRef.isValid()) return;

            // Only give to players
            Player attacker = (Player) commandBuffer.getComponent(attackerRef, Player.getComponentType());
            if (attacker == null) return;

            TransformComponent tx = (TransformComponent) commandBuffer.getComponent(deadRef, TransformComponent.getComponentType());
            Vector3d deathPos = (tx != null) ? tx.getPosition().clone() : null;

            // 1) If DeathComponent already has itemsLostOnDeath, use those
            ItemStack[] itemsLost = death.getItemsLostOnDeath();
            if (itemsLost != null && itemsLost.length > 0) {
                LOG.atInfo().log("[DeathGive] Giving %d death-component stacks to attacker=%d", itemsLost.length, attackerRef.getIndex());
                for (ItemStack s : itemsLost) {
                    if (s == null || s.isEmpty()) continue;
                    ItemUtils.interactivelyPickupItem(attackerRef, s, deathPos, (ComponentAccessor<EntityStore>) commandBuffer);
                }
                // clear so engine's player-drop system doesn't duplicate
                death.setItemsLostOnDeath(Collections.emptyList());
                // record for cleanup (in case NPC system still spawns)
                cleaner.recordDeath(deadRef.getIndex(), deathPos, attackerRef);
                return;
            }

            // 2) If NPC, try role drop list (ItemModule)
            NPCEntity npc = (NPCEntity) commandBuffer.getComponent(deadRef, NPCEntity.getComponentType());
            if (npc != null) {
                // Role -> drop list: the role assets usually define a dropList under role; many servers store it on role.getRoleAsset() or on role.getDropListId()
                String dropListId = tryExtractDropListIdFromRole(npc.getRole());
                if (dropListId != null) {
                    List<ItemStack> drops = ItemModule.get().getRandomItemDrops(dropListId);
                    if (drops != null && !drops.isEmpty()) {
                        LOG.atInfo().log("[DeathGive] Generated %d drops for dropList=%s for attacker=%d", drops.size(), dropListId, attackerRef.getIndex());
                        for (ItemStack stack : drops) {
                            if (stack == null || stack.isEmpty()) continue;
                            ItemUtils.interactivelyPickupItem(attackerRef, stack, deathPos, (ComponentAccessor<EntityStore>) commandBuffer);
                        }
                        // best-effort clear
                        death.setItemsLostOnDeath(Collections.emptyList());
                        cleaner.recordDeath(deadRef.getIndex(), deathPos, attackerRef);
                        return;
                    }
                }
            }

            // nothing to do deterministically
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[DeathGive] error");
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // intentionally empty (do not clear cleaner records here)
    }

    // Minimal safe extractor: try common getters. If your Role API has a concrete method, replace with it.
    private static String tryExtractDropListIdFromRole(Object role) {
        if (role == null) return null;
        try {
            try { var m = role.getClass().getMethod("getDropListId"); Object v = m.invoke(role); if (v instanceof String) return (String)v; } catch (NoSuchMethodException ignored) {}
            try { var m = role.getClass().getMethod("getDroplistId"); Object v = m.invoke(role); if (v instanceof String) return (String)v; } catch (NoSuchMethodException ignored) {}
            try { var m = role.getClass().getMethod("getRoleAsset"); Object asset = m.invoke(role); if (asset != null) {
                try { var md = asset.getClass().getMethod("getDropListId"); Object v = md.invoke(asset); if (v instanceof String) return (String)v; } catch (NoSuchMethodException ignored2) {}
            } } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}
        return null;
    }
}