package com.example.exampleplugin.spawner;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Immediately hands death drops to the killer; clears drops so nothing spawns on the ground.
 */
public class DeathImmediateLootSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        Query<EntityStore> q = DeathComponent.getComponentType();
        return (q == null) ? Query.any() : (Query<EntityStore>) q;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        // Run before the engine drop step so we can consume drops first
        return Set.of(new SystemDependency(Order.BEFORE, DeathSystems.DropPlayerDeathItems.class));
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> deadRef, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            DeathComponent death = (DeathComponent) commandBuffer.getComponent(deadRef, DeathComponent.getComponentType());
            if (death == null) return;

            Damage deathInfo = death.getDeathInfo();
            if (deathInfo == null || !(deathInfo.getSource() instanceof Damage.EntitySource)) return;

            Ref<EntityStore> attackerRef = ((Damage.EntitySource) deathInfo.getSource()).getRef();
            if (attackerRef == null || !attackerRef.isValid()) return;

            Player attacker = (Player) commandBuffer.getComponent(attackerRef, Player.getComponentType());
            if (attacker == null) return;

            PlayerSettings pSettings = (PlayerSettings) commandBuffer.getComponent(attackerRef, PlayerSettings.getComponentType());
            if (pSettings == null) pSettings = PlayerSettings.defaults();

            TransformComponent deadTx = (TransformComponent) commandBuffer.getComponent(deadRef, TransformComponent.getComponentType());
            Vector3d dropPos = (deadTx != null) ? deadTx.getPosition() : null;

            ItemStack[] drops = death.getItemsLostOnDeath();
            if (drops == null || drops.length == 0) return;

            List<ItemStack> leftovers = new ArrayList<>();
            for (ItemStack stack : drops) {
                if (stack == null || stack.isEmpty()) continue;

                ItemContainer container = attacker.getInventory().getContainerForItemPickup(stack.getItem(), pSettings);
                ItemStackTransaction tx = container.addItemStack(stack);
                ItemStack remainder = tx.getRemainder();

                int originalQty = stack.getQuantity();
                int remainderQty = (remainder == null) ? 0 : remainder.getQuantity();
                int accepted = originalQty - remainderQty;

                if (accepted > 0) {
                    try {
                        attacker.notifyPickupItem(attackerRef, stack.withQuantity(accepted), dropPos, commandBuffer);
                    } catch (Throwable ignored) {}
                }

                if (remainder != null && !remainder.isEmpty()) {
                    leftovers.add(remainder);
                }
            }

            // Prevent engine from spawning ground drops
            if (leftovers.isEmpty()) {
                death.setItemsLostOnDeath(List.of());
                death.setItemsLossMode(DeathConfig.ItemsLossMode.NONE);
            } else {
                death.setItemsLostOnDeath(leftovers);
            }

            LOG.atWarning().log("[DeathImmediateLoot] consumed drops for dead=%d attacker=%d leftovers=%d",
                    deadRef.getIndex(), attackerRef.getIndex(), leftovers.size());
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[DeathImmediateLoot] error");
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // no-op
    }
}