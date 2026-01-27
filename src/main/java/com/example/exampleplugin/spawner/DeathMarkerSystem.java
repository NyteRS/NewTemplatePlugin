package com.example.exampleplugin.spawner;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records killer + death position for a short time so item entities can be picked up automatically.
 */
public class DeathMarkerSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final long TTL_MS = 10_000L;

    private final Map<Integer, Marker> markers = new ConcurrentHashMap<>();

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        Query<EntityStore> q = DeathComponent.getComponentType();
        return (q == null) ? Query.any() : q;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
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

            TransformComponent tx = (TransformComponent) commandBuffer.getComponent(deadRef, TransformComponent.getComponentType());
            Vector3d pos = (tx != null && tx.getPosition() != null) ? tx.getPosition().clone() : new Vector3d();

            markers.put(deadRef.getIndex(), new Marker(attackerRef, pos, System.currentTimeMillis() + TTL_MS));
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[DeathMarker] error");
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        markers.remove(ref.getIndex());
    }

    public Map<Integer, Marker> getMarkers() {
        return markers;
    }

    public void cleanupExpiredMarkers() {
        long now = System.currentTimeMillis();
        markers.entrySet().removeIf(e -> e.getValue() == null || e.getValue().expiresAt < now || e.getValue().attackerRef == null || !e.getValue().attackerRef.isValid());
    }

    public static final class Marker {
        public final Ref<EntityStore> attackerRef;
        public final Vector3d deadPosition;
        public final long expiresAt;

        Marker(Ref<EntityStore> attackerRef, Vector3d deadPosition, long expiresAt) {
            this.attackerRef = attackerRef;
            this.deadPosition = deadPosition;
            this.expiresAt = expiresAt;
        }
    }
}