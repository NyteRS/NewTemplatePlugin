package com.example.exampleplugin.spawner;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records recent deaths (attacker ref + corpse position + timestamp).
 * ItemAutoPickupSystem consults this for new item entities.
 */
public class DeathMarkerSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    public static final class Marker {
        public final Ref<EntityStore> attackerRef;
        public final Vector3d deadPosition;
        public final long timestamp;

        public Marker(Ref<EntityStore> attackerRef, Vector3d deadPosition, long timestamp) {
            this.attackerRef = attackerRef;
            this.deadPosition = deadPosition;
            this.timestamp = timestamp;
        }
    }

    private final Map<Integer, Marker> markers = new ConcurrentHashMap<>();
    private static final long MARKER_LIFETIME_MS = 5_000L;

    public DeathMarkerSystem() {
        LOG.atWarning().log("[DeathMarker] constructed");
    }

    public Map<Integer, Marker> getMarkers() {
        return this.markers;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // Safe while DeathComponent type initialises; RefSystem registration tolerates Query.any()
        Query<EntityStore> q = DeathComponent.getComponentType();
        if (q == null) return Query.any();
        // DeathComponent.getComponentType() is itself a Query-compatible ComponentType; the system registry accepts it.
        return (Query) DeathComponent.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        // No explicit system dependency here — we only record the death marker when DeathComponent is added.
        return Set.of();
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            LOG.atWarning().log("[DeathMarker] onEntityAdded ref=%d reason=%s", ref.getIndex(), reason);
            DeathComponent death = (DeathComponent) commandBuffer.getComponent(ref, DeathComponent.getComponentType());
            if (death == null) {
                LOG.atWarning().log("[DeathMarker] death component null for ref=%d", ref.getIndex());
                return;
            }

            Damage deathInfo = death.getDeathInfo();
            if (deathInfo == null) {
                LOG.atWarning().log("[DeathMarker] deathInfo null for ref=%d", ref.getIndex());
                return;
            }

            if (!(deathInfo.getSource() instanceof Damage.EntitySource)) {
                LOG.atWarning().log("[DeathMarker] death source not EntitySource for ref=%d source=%s", ref.getIndex(), deathInfo.getSource() == null ? "null" : deathInfo.getSource().getClass().getSimpleName());
                return;
            }

            Ref<EntityStore> attackerRef = ((Damage.EntitySource) deathInfo.getSource()).getRef();
            if (attackerRef == null || !attackerRef.isValid()) {
                LOG.atWarning().log("[DeathMarker] attackerRef null/invalid for deadRef=%d", ref.getIndex());
                return;
            }

            // Ensure attacker is a player
            Player attacker = (Player) commandBuffer.getComponent(attackerRef, Player.getComponentType());
            if (attacker == null) {
                LOG.atWarning().log("[DeathMarker] attackerRef=%d is not a Player (deadRef=%d)", attackerRef.getIndex(), ref.getIndex());
                return;
            }

            TransformComponent transform = (TransformComponent) commandBuffer.getComponent(ref, TransformComponent.getComponentType());
            Vector3d pos = (transform != null) ? transform.getPosition().clone() : new Vector3d(0, 0, 0);
            long now = System.currentTimeMillis();
            markers.put(ref.getIndex(), new Marker(attackerRef, pos, now));
            LOG.atWarning().log("[DeathMarker] marker created deadIndex=%d attackerIndex=%d pos=%s", ref.getIndex(), attackerRef.getIndex(), pos);
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[DeathMarker] exception in onEntityAdded");
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        markers.remove(ref.getIndex());
        LOG.atWarning().log("[DeathMarker] marker removed deadIndex=%d reason=%s", ref.getIndex(), reason);
    }

    // Small helper for consumers
    public void cleanupExpiredMarkers() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, Marker> e : markers.entrySet()) {
            if (now - e.getValue().timestamp > MARKER_LIFETIME_MS) {
                markers.remove(e.getKey());
                LOG.atWarning().log("[DeathMarker] expired removed deadIndex=%d", e.getKey());
            }
        }
    }
}