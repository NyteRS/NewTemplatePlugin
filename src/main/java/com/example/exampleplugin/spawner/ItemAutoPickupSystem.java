package com.example.exampleplugin.spawner;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PickupItemComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;

/**
 * When an Item entity is added, first check if it has a PickupItemComponent (engine indicated
 * it should be picked up by a particular target). If so call ItemUtils.interactivelyPickupItem
 * to hand the ItemStack to the target immediately. Otherwise fall back to death-marker matching.
 */
public class ItemAutoPickupSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private final DeathMarkerSystem markerSystem;

    // Squared match radii used when matching by position (if pickup component not present)
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
        if (q == null) return Query.any();
        return q;
    }

    @Nonnull
    @Override
    public Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
        return Set.of();
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> itemRef, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            LOG.atWarning().log("[ItemAutoPickup] onEntityAdded r=%s itemRef=%d", reason, itemRef.getIndex());

            if (reason != AddReason.SPAWN) return;

            ItemComponent itemComp = (ItemComponent) commandBuffer.getComponent(itemRef, ItemComponent.getComponentType());
            if (itemComp == null) {
                LOG.atWarning().log("[ItemAutoPickup] itemComp null for itemRef=%d", itemRef.getIndex());
                return;
            }

            ItemStack stack = itemComp.getItemStack();
            if (stack == null || stack.isEmpty()) {
                LOG.atWarning().log("[ItemAutoPickup] item stack empty for itemRef=%d", itemRef.getIndex());
                return;
            }

            // 1) If engine created this as a pickup for a particular entity, PickupItemComponent will exist.
            PickupItemComponent pickupComp = (PickupItemComponent) commandBuffer.getComponent(itemRef, PickupItemComponent.getComponentType());
            if (pickupComp != null) {
                Ref<EntityStore> targetRef = pickupComp.getTargetRef();
                Vector3d origin = pickupComp.getStartPosition() != null ? pickupComp.getStartPosition().clone() : null;

                LOG.atWarning().log("[ItemAutoPickup] itemRef=%d has PickupItemComponent target=%s origin=%s - trying interactive pickup",
                        itemRef.getIndex(),
                        targetRef == null ? "null" : String.valueOf(targetRef.getIndex()),
                        origin);

                if (targetRef != null && targetRef.isValid()) {
                    try {
                        // ItemUtils.interactivelyPickupItem will attempt to give the stack to the target (and handle remainder)
                        ItemUtils.interactivelyPickupItem(targetRef, stack, origin, (com.hypixel.hytale.component.ComponentAccessor<EntityStore>) commandBuffer);
                        LOG.atWarning().log("[ItemAutoPickup] interactive pickup attempted for itemRef=%d target=%d", itemRef.getIndex(), targetRef.getIndex());
                        return; // done - the helper removes/handles the item entity as needed
                    } catch (Throwable t) {
                        LOG.atWarning().withCause(t).log("[ItemAutoPickup] interactive pickup threw for itemRef=%d", itemRef.getIndex());
                        // fall through to marker matching as fallback
                    }
                } else {
                    LOG.atWarning().log("[ItemAutoPickup] pickup target invalid for itemRef=%d", itemRef.getIndex());
                }
            }

            // 2) No PickupItemComponent or interactive pickup failed — fallback to marker matching (as before)
            TransformComponent itemTransform = (TransformComponent) commandBuffer.getComponent(itemRef, TransformComponent.getComponentType());
            if (itemTransform == null) {
                LOG.atWarning().log("[ItemAutoPickup] item transform null for itemRef=%d", itemRef.getIndex());
                return;
            }
            Vector3d itemPos = itemTransform.getPosition();

            // Clean expired markers and iterate
            markerSystem.cleanupExpiredMarkers();
            Map<Integer, DeathMarkerSystem.Marker> markers = markerSystem.getMarkers();
            if (markers.isEmpty()) {
                LOG.atWarning().log("[ItemAutoPickup] no markers present when itemRef=%d spawned", itemRef.getIndex());
            }

            boolean matchedAny = false;
            for (Map.Entry<Integer, DeathMarkerSystem.Marker> entry : markers.entrySet()) {
                int deadIndex = entry.getKey();
                DeathMarkerSystem.Marker m = entry.getValue();
                if (m == null || m.attackerRef == null) continue;
                if (!m.attackerRef.isValid()) continue;

                Vector3d deadPos = m.deadPosition;
                double dxDead = itemPos.x - deadPos.x;
                double dyDead = itemPos.y - deadPos.y;
                double dzDead = itemPos.z - deadPos.z;
                double distSqDead = dxDead*dxDead + dyDead*dyDead + dzDead*dzDead;

                TransformComponent attackerTransform = (TransformComponent) commandBuffer.getComponent(m.attackerRef, TransformComponent.getComponentType());
                Vector3d attackerPos = (attackerTransform != null) ? attackerTransform.getPosition().clone() : null;
                double distSqAttacker = Double.POSITIVE_INFINITY;
                if (attackerPos != null) {
                    double dxA = itemPos.x - attackerPos.x;
                    double dyA = itemPos.y - attackerPos.y;
                    double dzA = itemPos.z - attackerPos.z;
                    distSqAttacker = dxA*dxA + dyA*dyA + dzA*dzA;
                }

                LOG.atWarning().log("[ItemAutoPickup] itemRef=%d -> deadIndex=%d deadPos=%s attackerIndex=%s attackerPos=%s itemPos=%s distSqDead=%.3f distSqAttacker=%s",
                        itemRef.getIndex(),
                        deadIndex,
                        deadPos,
                        (m.attackerRef == null ? "null" : String.valueOf(m.attackerRef.getIndex())),
                        (attackerPos == null ? "null" : attackerPos),
                        itemPos,
                        distSqDead,
                        (Double.isInfinite(distSqAttacker) ? "null" : String.format("%.3f", distSqAttacker)));

                boolean closeToDead = distSqDead <= MATCH_RADIUS_SQ_DEADPOS;
                boolean closeToAttacker = (!Double.isInfinite(distSqAttacker) && distSqAttacker <= MATCH_RADIUS_SQ_ATTACKERPOS);

                if (!closeToDead && !closeToAttacker) {
                    // Not close enough to either
                    continue;
                }

                matchedAny = true;

                Player attackerPlayer = (Player) commandBuffer.getComponent(m.attackerRef, Player.getComponentType());
                if (attackerPlayer == null) {
                    LOG.atWarning().log("[ItemAutoPickup] attackerRef=%d is not a Player", m.attackerRef.getIndex());
                    continue;
                }

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
                    markers.remove(deadIndex);
                    LOG.atWarning().log("[ItemAutoPickup] fully accepted itemRef=%d by attackerIndex=%d qty=%d", itemRef.getIndex(), m.attackerRef.getIndex(), originalQty);
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

                // nothing accepted; continue searching
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