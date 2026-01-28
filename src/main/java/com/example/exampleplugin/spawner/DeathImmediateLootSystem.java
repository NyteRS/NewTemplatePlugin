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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * Run-before-drop system: give death drops directly to the killer (player) at death time.
 *
 * This runs BEFORE the engine drop systems so it can consume drops before item entities are spawned.
 */
public final class DeathImmediateLootSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        ComponentType<EntityStore, DeathComponent> deathType = DeathComponent.getComponentType();
        return (deathType == null) ? Query.any() : Archetype.of(new ComponentType[]{ deathType });
    }

    @Nonnull
    @Override
    public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
        // Ensure we run BEFORE engine drop systems so we can hand items to the player first.
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

            // Only handle Deaths caused by an entity (i.e. last attacker is an entity ref)
            if (!(deathInfo.getSource() instanceof Damage.EntitySource)) return;
            Ref<EntityStore> attackerRef = ((Damage.EntitySource) deathInfo.getSource()).getRef();
            if (attackerRef == null || !attackerRef.isValid()) return;

            // We only auto-give to players (skip non-player attackers)
            Player attackerPlayer = (Player) commandBuffer.getComponent(attackerRef, Player.getComponentType());
            if (attackerPlayer == null) return;

            // death position (optional but used for ItemUtils)
            TransformComponent tx = (TransformComponent) commandBuffer.getComponent(deadRef, TransformComponent.getComponentType());
            Vector3d deathPos = (tx != null) ? tx.getPosition() : null;

            // 1) If death component already lists itemsLostOnDeath -> hand those to attacker
            ItemStack[] itemsLost = death.getItemsLostOnDeath();
            if (itemsLost != null && itemsLost.length > 0) {
                LOG.atInfo().log("[DeathImmediateLoot] handing DeathComponent items to attacker (ref=%d) for dead entity %d", attackerRef.getIndex(), deadRef.getIndex());
                giveStacksToPlayer(attackerRef, itemsLost, deathPos, commandBuffer);
                // clear so engine's DropPlayerDeathItems doesn't duplicate them
                death.setItemsLostOnDeath(Collections.emptyList());
                return;
            }

            // 2) Otherwise, if this is an NPC, try to determine its dropList id from the role and generate drops
            NPCEntity npc = (NPCEntity) commandBuffer.getComponent(deadRef, NPCEntity.getComponentType());
            if (npc != null) {
                String dropListId = extractDropListIdFromRole(npc.getRole());
                if (dropListId != null) {
                    List<ItemStack> drops = ItemModule.get().getRandomItemDrops(dropListId);
                    if (drops != null && !drops.isEmpty()) {
                        LOG.atInfo().log("[DeathImmediateLoot] Generated %d drops for dropList=%s; giving to attacker %d", drops.size(), dropListId, attackerRef.getIndex());
                        for (ItemStack stack : drops) {
                            if (stack == null || stack.isEmpty()) continue;
                            ItemUtils.interactivelyPickupItem(attackerRef, stack, deathPos, (ComponentAccessor<EntityStore>) commandBuffer);
                        }
                        // Try to prevent engine duplicate spawning; clear DeathComponent items (best-effort).
                        death.setItemsLostOnDeath(Collections.emptyList());
                        return;
                    } else {
                        LOG.atInfo().log("[DeathImmediateLoot] dropList '%s' produced no stacks", dropListId);
                    }
                } else {
                    LOG.atInfo().log("[DeathImmediateLoot] could not extract dropListId from npc.role for dead entity %d", deadRef.getIndex());
                }
            }

            // nothing we could do deterministically here; fallback to leaving drops to engine
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[DeathImmediateLoot] error while handling death entity %d", deadRef.getIndex());
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // nothing to do here for this system
    }

    // Utility: hand an array of ItemStack to player via ItemUtils (uses engine pickup logic)
    private static void giveStacksToPlayer(Ref<EntityStore> playerRef, ItemStack[] stacks, Vector3d origin, ComponentAccessor<EntityStore> accessor) {
        for (ItemStack s : stacks) {
            if (s == null || s.isEmpty()) continue;
            ItemUtils.interactivelyPickupItem(playerRef, s, origin, accessor);
        }
    }

    /**
     * Try common ways to extract a drop-list id from the Role object.
     * Replace or extend these attempts with the exact method from your Role API if you know it.
     */
    @SuppressWarnings("unchecked")
    private static String extractDropListIdFromRole(Role role) {
        if (role == null) return null;

        try {
            // First try common getter names that might exist on Role/role definition
            // (Replace these with the real method if you know it; these are fallbacks.)
            try {
                java.lang.reflect.Method m = role.getClass().getMethod("getDropListId");
                Object v = m.invoke(role);
                if (v instanceof String && !((String) v).isEmpty()) return (String) v;
            } catch (NoSuchMethodException ignore) {}

            try {
                java.lang.reflect.Method m = role.getClass().getMethod("getDroplistId");
                Object v = m.invoke(role);
                if (v instanceof String && !((String) v).isEmpty()) return (String) v;
            } catch (NoSuchMethodException ignore) {}

            // Some Role implementations expose a "getRoleAsset()" or "getAsset()" that contains metadata
            try {
                java.lang.reflect.Method m = role.getClass().getMethod("getRoleAsset");
                Object asset = m.invoke(role);
                if (asset != null) {
                    try {
                        java.lang.reflect.Method md = asset.getClass().getMethod("getDropListId");
                        Object v = md.invoke(asset);
                        if (v instanceof String && !((String) v).isEmpty()) return (String) v;
                    } catch (NoSuchMethodException ignore2) {}
                }
            } catch (NoSuchMethodException ignore) {}

            // If the Role class has a generic "getData" or "getDefinition" you can inspect that too
            // Add additional reflection attempts here as needed for your server version.

        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[DeathImmediateLoot] Reflection failure when extracting dropListId from Role");
        }

        return null;
    }
}