package com.example.exampleplugin.spawner;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PickupItemComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerItemEntityPickupSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;

/**
 * When an Item entity is added, attempt immediate pickup by the recorded killer.
 */
public class ItemAutoPickupSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private final DeathMarkerSystem markerSystem;

    private static final double MATCH_RADIUS_SQ_DEADPOS = 16.0;     // 4 blocks radius
    private static final double MATCH_RADIUS_SQ_ATTACKERPOS = 36.0; // 6 blocks radius

    public ItemAutoPickupSystem(DeathMarkerSystem markerSystem) {
        this.markerSystem = markerSystem;
        LOG.atWarning().log("[ItemAutoPickup] constructed");
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        Query<EntityStore> q = ItemComponent.getComponentType();
        return (q == null) ? Query.any() : q;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        // After drops spawn and marker exists; before vanilla player pickup so we can consume/remove
        return Set.of(
            new SystemDependency(Order.AFTER, DeathSystems.DropPlayerDeathItems.class),
            new SystemDependency(Order.AFTER, DeathMarkerSystem.class),
            new SystemDependency(Order.BEFORE, PlayerItemEntityPickupSystem.class)
        );
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> itemRef, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            LOG.atWarning().log("[ItemAutoPickup] onEntityAdded r=%s itemRef=%d", reason, itemRef.getIndex());

            ItemComponent itemComp = (ItemComponent) commandBuffer.getComponent(itemRef, ItemComponent.getComponentType());
            if (itemComp == null) return;

            ItemStack stack = itemComp.getItemStack();
            if (stack == null || stack.isEmpty()) return;

            // Direct-target pickup path (engine may set this)
            PickupItemComponent pickupComp = (PickupItemComponent) commandBuffer.getComponent(itemRef, PickupItemComponent.getComponentType());
            if (pickupComp != null) {
                Ref<EntityStore> targetRef = pickupComp.getTargetRef();
                Vector3d origin = pickupComp.getStartPosition() != null ? pickupComp.getStartPosition().clone() : null;
                if (targetRef != null && targetRef.isValid()) {
                    try {
                        ItemUtils.interactivelyPickupItem(targetRef, stack, origin, (ComponentAccessor<EntityStore>) commandBuffer);
                        commandBuffer.removeEntity(itemRef, RemoveReason.REMOVE);
                        LOG.atWarning().log("[ItemAutoPickup] interactive pickup succeeded for itemRef=%d target=%d", itemRef.getIndex(), targetRef.getIndex());
                        return;
                    } catch (Throwable t) {
                        LOG.atWarning().withCause(t).log("[ItemAutoPickup] interactive pickup threw for itemRef=%d", itemRef.getIndex());
                    }
                }
            }

            TransformComponent itemTransform = (TransformComponent) commandBuffer.getComponent(itemRef, TransformComponent.getComponentType());
            if (itemTransform == null) return;
            Vector3d itemPos = itemTransform.getPosition();

            markerSystem.cleanupExpiredMarkers();
            Map<Integer, DeathMarkerSystem.Marker> markers = markerSystem.getMarkers();

            boolean matchedAny = false;
            for (Map.Entry<Integer, DeathMarkerSystem.Marker> entry : markers.entrySet()) {
                DeathMarkerSystem.Marker m = entry.getValue();
                if (m == null || m.attackerRef == null || !m.attackerRef.isValid()) continue;

                Vector3d deadPos = m.deadPosition;
                double dxDead = itemPos.x - deadPos.x;
                double dyDead = itemPos.y - deadPos.y;
                double dzDead = itemPos.z - deadPos.z;
                double distSqDead = dxDead * dxDead + dyDead * dyDead + dzDead * dzDead;

                TransformComponent attackerTransform = (TransformComponent) commandBuffer.getComponent(m.attackerRef, TransformComponent.getComponentType());
                Vector3d attackerPos = (attackerTransform != null) ? attackerTransform.getPosition().clone() : null;
                double distSqAttacker = Double.POSITIVE_INFINITY;
                if (attackerPos != null) {
                    double dxA = itemPos.x - attackerPos.x;
                    double dyA = itemPos.y - attackerPos.y;
                    double dzA = itemPos.z - attackerPos.z;
                    distSqAttacker = dxA * dxA + dyA * dyA + dzA * dzA;
                }

                boolean closeToDead = distSqDead <= MATCH_RADIUS_SQ_DEADPOS;
                boolean closeToAttacker = distSqAttacker <= MATCH_RADIUS_SQ_ATTACKERPOS;
                if (!closeToDead && !closeToAttacker) continue;

                matchedAny = true;

                Player attackerPlayer = (Player) commandBuffer.getComponent(m.attackerRef, Player.getComponentType());
                if (attackerPlayer == null) continue;

                PlayerSettings pSettings = (PlayerSettings) commandBuffer.getComponent(m.attackerRef, PlayerSettings.getComponentType());
                if (pSettings == null) pSettings = PlayerSettings.defaults();

                ItemContainer container = attackerPlayer.getInventory().getContainerForItemPickup(stack.getItem(), pSettings);
                ItemStackTransaction tx = container.addItemStack(stack);
                ItemStack remainder = tx.getRemainder();

                int originalQty = stack.getQuantity();
                int remainderQty = (remainder == null) ? 0 : remainder.getQuantity();
                int accepted = originalQty - remainderQty;

                if (remainder == null || remainder.isEmpty()) {
                    try {
                        attackerPlayer.notifyPickupItem(m.attackerRef, stack, itemPos, commandBuffer);
                    } catch (Throwable ignored) {}
                    commandBuffer.removeEntity(itemRef, RemoveReason.REMOVE);
                    LOG.atWarning().log("[ItemAutoPickup] fully accepted itemRef=%d attackerIndex=%d qty=%d", itemRef.getIndex(), m.attackerRef.getIndex(), originalQty);
                    return;
                }

                if (!remainder.equals(stack)) {
                    if (accepted > 0) {
                        try {
                            attackerPlayer.notifyPickupItem(m.attackerRef, stack.withQuantity(accepted), itemPos, commandBuffer);
                        } catch (Throwable ignored) {}
                    }
                    itemComp.setItemStack(remainder);
                    LOG.atWarning().log("[ItemAutoPickup] partial accepted itemRef=%d attackerIndex=%d accepted=%d remainder=%d", itemRef.getIndex(), m.attackerRef.getIndex(), accepted, remainderQty);
                    return;
                }

                // Nothing accepted (inventory full); leave item for vanilla pickup and keep marker alive
            }

            if (!matchedAny) {
                LOG.atWarning().log("[ItemAutoPickup] no marker matched itemRef=%d (markers=%d)", itemRef.getIndex(), markers.size());
            }

        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[ItemAutoPickup] unexpected error in onEntityAdded");
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // no-op
    }
}