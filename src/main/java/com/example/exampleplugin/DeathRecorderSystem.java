package com.example.exampleplugin;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.npc.systems.NPCDamageSystems;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Records deaths and (if attacker is a player) registers them with AutoLootPickupSystem.
 * Runs BEFORE server drop systems so it can capture the attacker before drops happen.
 */
public final class DeathRecorderSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final AutoLootPickupSystem autoLootSystem;

    public DeathRecorderSystem(AutoLootPickupSystem autoLootSystem) {
        this.autoLootSystem = autoLootSystem;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        var deathType = DeathComponent.getComponentType();
        if (deathType == null) return Query.any();
        return com.hypixel.hytale.component.Archetype.of(new com.hypixel.hytale.component.ComponentType[]{ deathType });
    }

    @Nonnull
    @Override
    public Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
        // Run before server drop item systems so we record attacker before server spawns drops
        return Set.of(
                new SystemDependency(Order.BEFORE, NPCDamageSystems.DropDeathItems.class),
                new SystemDependency(Order.BEFORE, DeathSystems.DropPlayerDeathItems.class)
        );
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> deadRef, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            // Skip players (we want NPC deaths here; if you want players change to handle player death too, remove this check)
            Player maybePlayer = (Player) commandBuffer.getComponent(deadRef, Player.getComponentType());
            if (maybePlayer != null) return;

            DeathComponent death = (DeathComponent) commandBuffer.getComponent(deadRef, DeathComponent.getComponentType());
            if (death == null) return;

            Damage deathInfo = death.getDeathInfo();
            if (deathInfo == null) return;

            if (deathInfo.getSource() instanceof Damage.EntitySource) {
                Ref<EntityStore> attackerRef = ((Damage.EntitySource) deathInfo.getSource()).getRef();
                if (attackerRef != null && attackerRef.isValid()) {
                    TransformComponent transform = (TransformComponent) commandBuffer.getComponent(deadRef, TransformComponent.getComponentType());
                    Vector3d pos = (transform != null) ? transform.getPosition() : null;
                    if (pos != null) {
                        autoLootSystem.recordDeath(deadRef, pos, attackerRef);
                        LOG.atInfo().log("[DeathRecorder] Recorded death of entity (index=%d) for attacker (index=%d) at %s", deadRef.getIndex(), attackerRef.getIndex(), pos);
                    }
                }
            }
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[DeathRecorder] onEntityAdded error");
        }
    }

    /**
     * Required by RefSystem: called when an entity is removed. Use it to clean up any temporary death records.
     * Note: RefSystem declares this method as onEntityRemove (no trailing 'd').
     */
    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            // remove any short-lived death record for this index to prevent stale entries
            autoLootSystem.removeDeathRecord(ref.getIndex());
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[DeathRecorder] onEntityRemove error");
        }
    }
}