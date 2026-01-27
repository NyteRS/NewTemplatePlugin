package com.example.exampleplugin.spawner;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;

/**
 * Diagnostic version: logs every DeathComponent addition and prints component details.
 * Use this to determine whether DeathComponent additions are observed and what data they carry.
 *
 * Register in plugin.start() the same as before.
 */
public final class DeathAutoPickupSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private volatile Query<EntityStore> cachedQuery = null;

    public DeathAutoPickupSystem() {
        LOG.atWarning().log("[DeathAutoPickup] constructed (diagnostic)");
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        Query<EntityStore> q = this.cachedQuery;
        if (q != null) return q;

        try {
            var deathType = DeathComponent.getComponentType();
            if (deathType == null) {
                LOG.atWarning().log("[DeathAutoPickup] DeathComponent.getComponentType() == null -> returning Query.any()");
                return Query.any();
            }
            Query<EntityStore> archetypeQuery = Archetype.of(new ComponentType[] { deathType });
            this.cachedQuery = archetypeQuery;
            return archetypeQuery;
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[DeathAutoPickup] failed to build archetype query; falling back to Query.any()");
            return Query.any();
        }
    }

    @Nonnull
    @Override
    public Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
        // keep same dependency ordering as production system
        return Set.of(new SystemDependency(com.hypixel.hytale.component.dependency.Order.BEFORE, DeathSystems.DropPlayerDeathItems.class));
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> deadRef, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            LOG.atWarning().log("[DeathAutoPickup:DIAG] onEntityAdded ref=%d reason=%s", deadRef.getIndex(), reason);

            DeathComponent death = (DeathComponent) commandBuffer.getComponent(deadRef, DeathComponent.getComponentType());
            LOG.atWarning().log("[DeathAutoPickup:DIAG] DeathComponent present? %s", death != null);

            if (death != null) {
                Damage deathInfo = death.getDeathInfo();
                LOG.atWarning().log("[DeathAutoPickup:DIAG] death.getDeathInfo() == %s", deathInfo == null ? "null" : deathInfo.getClass().getSimpleName());

                if (deathInfo != null && deathInfo.getSource() != null) {
                    LOG.atWarning().log("[DeathAutoPickup:DIAG] deathInfo.getSource() class=%s", deathInfo.getSource().getClass().getName());
                }

                if (deathInfo instanceof Damage && deathInfo.getSource() instanceof Damage.EntitySource) {
                    Ref<EntityStore> attackerRef = ((Damage.EntitySource) deathInfo.getSource()).getRef();
                    LOG.atWarning().log("[DeathAutoPickup:DIAG] attackerRef == %s", attackerRef == null ? "null" : String.valueOf(attackerRef.getIndex()));

                    if (attackerRef != null && attackerRef.isValid()) {
                        Object maybePlayer = commandBuffer.getComponent(attackerRef, Player.getComponentType());
                        LOG.atWarning().log("[DeathAutoPickup:DIAG] attackerRef is player? %s", maybePlayer != null);
                    } else {
                        LOG.atWarning().log("[DeathAutoPickup:DIAG] attackerRef invalid or null");
                    }
                } else {
                    LOG.atWarning().log("[DeathAutoPickup:DIAG] death.source is not EntitySource or deathInfo null");
                }

                // items lost on death
                try {
                    var items = death.getItemsLostOnDeath();
                    LOG.atWarning().log("[DeathAutoPickup:DIAG] death.getItemsLostOnDeath() length = %d", (items == null) ? 0 : items.length);
                } catch (Throwable t) {
                    LOG.atWarning().withCause(t).log("[DeathAutoPickup:DIAG] reading death.getItemsLostOnDeath() failed");
                }

                // corpse transform
                TransformComponent tc = (TransformComponent) commandBuffer.getComponent(deadRef, TransformComponent.getComponentType());
                LOG.atWarning().log("[DeathAutoPickup:DIAG] corpse transform present? %s", tc != null);
                if (tc != null) LOG.atWarning().log("[DeathAutoPickup:DIAG] corpse pos=%s", tc.getPosition());
            }
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[DeathAutoPickup:DIAG] unexpected error");
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // no-op
    }
}