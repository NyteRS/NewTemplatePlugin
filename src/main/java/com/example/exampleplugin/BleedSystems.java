package com.example.exampleplugin;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.modules.entity.AllLegacyLivingEntityTypesQuery;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Bleed system with:
 *  - stacks scale off last dagger hit damage
 *  - bleed-applied damage grants lifesteal to attacker based on the attacker's equipped item
 *  - bleed damage is marked as BleedEntitySource so bleed events don't re-add stacks
 *
 * Tunables are at the top of the class.
 */
public final class BleedSystems {
    private BleedSystems() {}

    // Tunables (adjust)
    private static final int MAX_STACKS = 5;
    private static final long STACK_DURATION_MS = 3000L;         // 3 seconds per stack
    private static final float TICK_INTERVAL_SECONDS = 1.0f;     // apply bleed every 1s

    // Fixed fallback values if we don't have last-hit damage
    private static final float PER_STACK_DAMAGE_PER_TICK = 2.0f; // fallback per-stack per-tick damage
    private static final float BURST_DAMAGE_FALLBACK = 10.0f;    // fallback burst damage

    // Scaling parameters (relative to the dagger's original damage)
    private static final float BLEED_PERCENT_PER_STACK = 0.20f;  // 5% of the original dagger damage per stack per tick
    private static final float BURST_MULTIPLIER = 1.50f;         // burst equals 50% of the original dagger damage

    // Default lifesteal if dagger detected (same default as your lifesteal system)
    private static final double DEFAULT_DAGGER_LIFESTEAL = 0.12;

    // Map keyed by stable EntityKey to BleedData
    private static final ConcurrentMap<EntityKey, BleedData> BLEED_MAP = new ConcurrentHashMap<>();

    // Marker Source used for bleed damage (prevents bleed damage from retriggering itself)
    private static final class BleedEntitySource extends Damage.EntitySource {
        BleedEntitySource(@Nonnull Ref<EntityStore> shooter) {
            super(shooter);
        }
    }

    // --- Public helpers ---

    /**
     * Add a stack to a victim; pass the original damage amount that applied the stack (lastHitDamage).
     * If lastHitDamage <= 0 we simply record no damage and the fallback fixed values will be used.
     */
    public static void addStack(@Nonnull Ref<EntityStore> victimRef, @Nullable Ref<EntityStore> attackerRef, float lastHitDamage) {
        if (victimRef == null || !victimRef.isValid()) return;
        EntityKey key = new EntityKey(victimRef);
        BleedData data = BLEED_MAP.computeIfAbsent(key, k -> new BleedData());
        data.addStack(attackerRef, lastHitDamage);
        try {
            com.hypixel.hytale.server.npc.NPCPlugin.get().getLogger()
                    .atInfo().log("Bleed: added stack to %s (stacks=%d lastHitDamage=%.2f)", key, data.getCount(), data.getLastHitDamage());
        } catch (Throwable ignored) {}
    }

    public static void clearFor(@Nonnull Ref<EntityStore> victimRef) {
        if (victimRef == null) return;
        BLEED_MAP.remove(new EntityKey(victimRef));
    }

    // --- Damage event system: add stack on dagger hits (ignores bleed-sourced damage) ---

    public static class BleedOnDamage extends DamageEventSystem {
        public BleedOnDamage() { super(); }

        @Nonnull
        public Query<EntityStore> getQuery() {
            return (Query<EntityStore>) AllLegacyLivingEntityTypesQuery.INSTANCE;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer,
                           @Nonnull Damage damageEvent) {
            try {
                if (damageEvent == null || damageEvent.isCancelled()) return;

                // Ignore damage that originated from bleed system itself
                Damage.Source src = damageEvent.getSource();
                if (src instanceof BleedEntitySource) return;

                float amount = damageEvent.getAmount();
                if (amount <= 0f) return;

                if (!(src instanceof Damage.EntitySource)) return;

                Ref<EntityStore> attackerRef = ((Damage.EntitySource) src).getRef();
                if (attackerRef == null || !attackerRef.isValid()) return;

                @Nullable Entity ent = EntityUtils.getEntity(attackerRef, store);
                if (!(ent instanceof LivingEntity)) return;
                LivingEntity attacker = (LivingEntity) ent;

                ItemStack held = getHeldItemReflective(attacker);
                if (held == null || ItemStack.isEmpty(held)) return;

                // Dagger heuristic
                boolean isDagger = false;
                Item item = held.getItem();
                if (item != null) {
                    try {
                        String[] cats = item.getCategories();
                        if (cats != null) {
                            for (String c : cats) if (c != null && c.equalsIgnoreCase("Dagger")) { isDagger = true; break; }
                        }
                    } catch (Throwable ignored) {}
                    String id = held.getItemId();
                    if (!isDagger && id != null && id.toLowerCase().contains("dagger")) isDagger = true;
                }

                if (!isDagger) return;

                // Victim ref from archetype chunk
                Ref<EntityStore> victimRef = archetypeChunk.getReferenceTo(index);
                if (victimRef == null || !victimRef.isValid()) return;

                // Pass amount so future bleed can scale off it
                addStack(victimRef, attackerRef, amount);

            } catch (Throwable t) {
                try {
                    com.hypixel.hytale.server.npc.NPCPlugin.get().getLogger()
                            .at(java.util.logging.Level.WARNING).withCause(t).log("BleedOnDamage handler error");
                } catch (Throwable ignored) {}
            }
        }

        // Reflection helper to obtain held item
        @Nullable
        private ItemStack getHeldItemReflective(@Nonnull LivingEntity attacker) {
            try {
                Object inventory = tryInvoke(attacker, "getInventory");
                if (inventory == null) return null;
                String[] tries = { "getActiveHotbarItem", "getActiveHotbarItemStack", "getItemInHand", "getActiveItem", "getActiveSlotItem", "getItem" };
                for (String name : tries) {
                    try {
                        Method m = inventory.getClass().getMethod(name);
                        Object out = m.invoke(inventory);
                        if (out instanceof ItemStack) return (ItemStack) out;
                    } catch (NoSuchMethodException ignore) {}
                }
                for (String name : tries) {
                    try {
                        Method m = attacker.getClass().getMethod(name);
                        Object out = m.invoke(attacker);
                        if (out instanceof ItemStack) return (ItemStack) out;
                    } catch (NoSuchMethodException ignore) {}
                }
            } catch (Throwable ignored) {}
            return null;
        }

        private Object tryInvoke(Object obj, String methodName) {
            try {
                Method m = obj.getClass().getMethod(methodName);
                return m.invoke(obj);
            } catch (Throwable ignored) { return null; }
        }
    }

    // --- Ticking system: apply periodic damage and burst ---

    public static class BleedTicking extends EntityTickingSystem<EntityStore> {
        public BleedTicking() {}

        @Nonnull
        public Query<EntityStore> getQuery() {
            return (Query<EntityStore>) AllLegacyLivingEntityTypesQuery.INSTANCE;
        }

        @Override
        public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref == null || !ref.isValid()) return;
            EntityKey key = new EntityKey(ref);

            BleedData data = BLEED_MAP.get(key);
            if (data == null) return;

            BleedResult result;
            try {
                result = data.tick(dt);
            } catch (Throwable t) {
                try {
                    com.hypixel.hytale.server.npc.NPCPlugin.get().getLogger()
                            .at(java.util.logging.Level.WARNING).withCause(t).log("Bleed tick error for %s", key);
                } catch (Throwable ignored) {}
                return;
            }

            if (result == null) {
                if (data.isEmpty()) BLEED_MAP.remove(key);
                return;
            }

            try {
                // --- BURST ---
                if (result.burstDamage > 0f) {
                    int appliedBurst;
                    if (result.lastHitDamage > 0f) {
                        appliedBurst = Math.max(1, Math.round(result.lastHitDamage * BURST_MULTIPLIER));
                    } else {
                        appliedBurst = Math.max(1, Math.round(BURST_DAMAGE_FALLBACK));
                    }

                    Ref<EntityStore> atkRef = result.lastAttackerRef;
                    if (atkRef != null && atkRef.isValid()) {
                        try {
                            Damage.Source src = new BleedEntitySource(atkRef); // marker source
                            Damage burstWithSource = new Damage(src, DamageCause.PHYSICAL, (float) appliedBurst);
                            attachDefaultCameraEffect(burstWithSource);
                            DamageSystems.executeDamage(ref, commandBuffer, burstWithSource);

                            // apply lifesteal to attacker
                            applyLifestealToAttacker(atkRef, commandBuffer, appliedBurst);

                        } catch (Throwable t) {
                            Damage burst = new Damage(Damage.NULL_SOURCE, DamageCause.PHYSICAL, (float) appliedBurst);
                            attachDefaultCameraEffect(burst);
                            DamageSystems.executeDamage(ref, commandBuffer, burst);
                        }
                    } else {
                        Damage burst = new Damage(Damage.NULL_SOURCE, DamageCause.PHYSICAL, (float) appliedBurst);
                        attachDefaultCameraEffect(burst);
                        DamageSystems.executeDamage(ref, commandBuffer, burst);
                    }
                    try {
                        com.hypixel.hytale.server.npc.NPCPlugin.get().getLogger()
                                .atInfo().log("Bleed: burst %s -> appliedDamage=%d", key, appliedBurst);
                    } catch (Throwable ignored) {}
                }

                // --- PERIODIC ---
                if (result.periodicDamage > 0f) {
                    int appliedPeriodic = Math.max(1, Math.round(result.periodicDamage));
                    Ref<EntityStore> atkRef = result.lastAttackerRef;
                    if (atkRef != null && atkRef.isValid()) {
                        try {
                            Damage.Source src = new BleedEntitySource(atkRef);
                            Damage periodicWithSource = new Damage(src, DamageCause.PHYSICAL, (float) appliedPeriodic);
                            attachDefaultCameraEffect(periodicWithSource);
                            DamageSystems.executeDamage(ref, commandBuffer, periodicWithSource);

                            // apply lifesteal
                            applyLifestealToAttacker(atkRef, commandBuffer, appliedPeriodic);

                        } catch (Throwable t) {
                            Damage periodic = new Damage(Damage.NULL_SOURCE, DamageCause.PHYSICAL, (float) appliedPeriodic);
                            attachDefaultCameraEffect(periodic);
                            DamageSystems.executeDamage(ref, commandBuffer, periodic);
                        }
                    } else {
                        Damage periodic = new Damage(Damage.NULL_SOURCE, DamageCause.PHYSICAL, (float) appliedPeriodic);
                        attachDefaultCameraEffect(periodic);
                        DamageSystems.executeDamage(ref, commandBuffer, periodic);
                    }
                    try {
                        com.hypixel.hytale.server.npc.NPCPlugin.get().getLogger()
                                .atInfo().log("Bleed: periodic %s -> appliedDamage=%d (stacks=%d)", key, appliedPeriodic, data.getCount());
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                try {
                    com.hypixel.hytale.server.npc.NPCPlugin.get().getLogger()
                            .at(java.util.logging.Level.WARNING).withCause(t).log("Bleed damage application error for %s", key);
                } catch (Throwable ignored) {}
            }

            if (data.isEmpty()) BLEED_MAP.remove(key);
        }
    }

    // Attach a CameraEffect meta object to Damage so clients show damage numbers/effects.
    private static void attachDefaultCameraEffect(Damage damage) {
        if (damage == null) return;
        try {
            // Try protocol CameraEffect first
            try {
                Class<?> cameraEffectProtocol = Class.forName("com.hypixel.hytale.protocol.CameraEffect");
                Method getAssetMap = cameraEffectProtocol.getMethod("getAssetMap");
                Object assetMap = getAssetMap.invoke(null);
                if (assetMap != null) {
                    Method getIndex = assetMap.getClass().getMethod("getIndex", String.class);
                    Object idxObj = getIndex.invoke(assetMap, "Default");
                    int idx = (idxObj instanceof Integer) ? (Integer) idxObj : ((Number) idxObj).intValue();
                    Damage.CameraEffect dmgCam = new Damage.CameraEffect(idx);
                    damage.getMetaStore().putMetaObject(Damage.CAMERA_EFFECT, dmgCam);
                    return;
                }
            } catch (ClassNotFoundException cnf) {
                // ignore and try fallback
            }

            // Fallback attempts: try other likely classes/packages (best-effort)
            try {
                Class<?> ceClass = Class.forName("com.hypixel.hytale.server.core.asset.type.camera.CameraEffect");
                Method getAssetMap2 = ceClass.getMethod("getAssetMap");
                Object assetMap2 = getAssetMap2.invoke(null);
                if (assetMap2 != null) {
                    Method getIndex2 = assetMap2.getClass().getMethod("getIndex", String.class);
                    Object idxObj2 = getIndex2.invoke(assetMap2, "Default");
                    int idx2 = (idxObj2 instanceof Integer) ? (Integer) idxObj2 : ((Number) idxObj2).intValue();
                    Damage.CameraEffect dmgCam2 = new Damage.CameraEffect(idx2);
                    damage.getMetaStore().putMetaObject(Damage.CAMERA_EFFECT, dmgCam2);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
            // best-effort; do not break bleed if effect can't be attached
        }
    }

    // Apply lifesteal to attackerRef for a given appliedDamage amount.
    // Heals attacker by appliedDamage * lifestealPercent (if attacker has a lifesteal item).
    private static void applyLifestealToAttacker(@Nonnull Ref<EntityStore> attackerRef, @Nonnull CommandBuffer<EntityStore> commandBuffer, int appliedDamage) {
        if (attackerRef == null || !attackerRef.isValid()) return;
        try {
            // Resolve attacker entity and held item
            Entity maybeEnt = EntityUtils.getEntity(attackerRef, commandBuffer);
            if (!(maybeEnt instanceof LivingEntity)) return;
            LivingEntity attackerEntity = (LivingEntity) maybeEnt;

            ItemStack held = tryGetHeldItemFromEntity(attackerEntity, commandBuffer);
            double lifestealPercent = getLifestealFromItem(held);
            if (lifestealPercent <= 0.0) return;

            float heal = (float)(appliedDamage * lifestealPercent);
            if (heal <= 0f) return;

            EntityStatMap statMap = commandBuffer.getComponent(attackerRef, EntityStatMap.getComponentType());
            if (statMap != null) {
                statMap.addStatValue(DefaultEntityStatTypes.getHealth(), heal);
            }
        } catch (Throwable ignored) {}
    }

    // Try to obtain held item from a LivingEntity using reflection; commandBuffer is unused here but kept for parity if needed.
    @Nullable
    private static ItemStack tryGetHeldItemFromEntity(@Nonnull LivingEntity attacker, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            Object inventory = null;
            try {
                Method mInv = attacker.getClass().getMethod("getInventory");
                inventory = mInv.invoke(attacker);
            } catch (NoSuchMethodException ignore) {}
            if (inventory != null) {
                String[] tries = { "getActiveHotbarItem", "getActiveHotbarItemStack", "getItemInHand", "getActiveItem", "getActiveSlotItem", "getItem" };
                for (String nm : tries) {
                    try {
                        Method m = inventory.getClass().getMethod(nm);
                        Object out = m.invoke(inventory);
                        if (out instanceof ItemStack) return (ItemStack) out;
                    } catch (NoSuchMethodException ignore) {}
                }
            }
            // fallback to direct on entity
            String[] tries2 = { "getActiveHotbarItem", "getActiveHotbarItemStack", "getItemInHand", "getActiveItem", "getActiveSlotItem", "getItem" };
            for (String nm : tries2) {
                try {
                    Method m = attacker.getClass().getMethod(nm);
                    Object out = m.invoke(attacker);
                    if (out instanceof ItemStack) return (ItemStack) out;
                } catch (NoSuchMethodException ignore) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // Determine lifesteal percent from item; uses dagger heuristic and default percentage
    private static double getLifestealFromItem(@Nullable ItemStack itemStack) {
        if (itemStack == null) return 0.0;
        try {
            String id = itemStack.getItemId();
            Item it = itemStack.getItem();
            if (it != null) {
                try {
                    String[] cats = it.getCategories();
                    if (cats != null) {
                        for (String c : cats) {
                            if (c != null && c.equalsIgnoreCase("Dagger")) return DEFAULT_DAGGER_LIFESTEAL;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (id != null && id.toLowerCase().contains("dagger")) return DEFAULT_DAGGER_LIFESTEAL;
        } catch (Throwable ignored) {}
        return 0.0;
    }

    // --- Per-entity key & data ---

    private static final class EntityKey {
        private final Store<EntityStore> store;
        private final int index;

        EntityKey(Ref<EntityStore> ref) {
            this.store = ref.getStore();
            this.index = ref.getIndex();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EntityKey)) return false;
            EntityKey other = (EntityKey) o;
            return this.index == other.index && this.store == other.store;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(store) * 31 + index;
        }

        @Override
        public String toString() {
            return "EntityKey(store=" + System.identityHashCode(store) + ",index=" + index + ")";
        }
    }

    private static final class BleedData {
        private final List<Long> stacks = new ArrayList<>();
        private float tickAccumulator = 0f;
        private Ref<EntityStore> lastAttackerRef = null;
        private float lastHitDamage = 0f; // store original dagger damage (if available)

        synchronized void addStack(@Nullable Ref<EntityStore> attackerRef, float lastHitDamage) {
            long now = System.currentTimeMillis();
            removeExpiredInternal(now);
            stacks.add(now);
            if (attackerRef != null && attackerRef.isValid()) lastAttackerRef = attackerRef;
            if (lastHitDamage > 0f) this.lastHitDamage = lastHitDamage; // update lastHitDamage to newest hit's damage
            while (stacks.size() > MAX_STACKS) stacks.remove(0);
        }

        synchronized BleedResult tick(float dtSeconds) {
            long now = System.currentTimeMillis();
            removeExpiredInternal(now);
            if (stacks.isEmpty()) return null;

            tickAccumulator += dtSeconds;
            float periodicDamage = 0f;
            float burst = 0f;
            Ref<EntityStore> attacker = lastAttackerRef;

            if (stacks.size() >= MAX_STACKS) {
                // burst scaled off lastHitDamage if available
                if (lastHitDamage > 0f) {
                    burst = lastHitDamage * BURST_MULTIPLIER;
                } else {
                    burst = BURST_DAMAGE_FALLBACK;
                }
                stacks.clear();
                tickAccumulator = 0f;
                lastAttackerRef = attacker;
                return new BleedResult(0f, burst, attacker, lastHitDamage);
            }

            if (tickAccumulator >= TICK_INTERVAL_SECONDS) {
                int intervals = (int) Math.floor(tickAccumulator / TICK_INTERVAL_SECONDS);
                if (intervals > 0) {
                    int s = stacks.size();
                    if (lastHitDamage > 0f) {
                        periodicDamage = s * lastHitDamage * BLEED_PERCENT_PER_STACK * intervals;
                    } else {
                        periodicDamage = s * PER_STACK_DAMAGE_PER_TICK * intervals;
                    }
                    tickAccumulator -= intervals * TICK_INTERVAL_SECONDS;
                }
            }

            if (periodicDamage <= 0f) return null;
            return new BleedResult(periodicDamage, 0f, attacker, lastHitDamage);
        }

        synchronized boolean isEmpty() {
            removeExpiredInternal(System.currentTimeMillis());
            return stacks.isEmpty();
        }

        synchronized int getCount() {
            removeExpiredInternal(System.currentTimeMillis());
            return stacks.size();
        }

        synchronized float getLastHitDamage() { return lastHitDamage; }

        private void removeExpiredInternal(long nowMillis) {
            long threshold = nowMillis - STACK_DURATION_MS;
            Iterator<Long> it = stacks.iterator();
            while (it.hasNext()) {
                Long t = it.next();
                if (t < threshold) it.remove();
            }
            if (stacks.isEmpty()) lastAttackerRef = null;
        }
    }

    private static final class BleedResult {
        final float periodicDamage;
        final float burstDamage;
        final Ref<EntityStore> lastAttackerRef;
        final float lastHitDamage;

        BleedResult(float periodicDamage, float burstDamage, Ref<EntityStore> lastAttackerRef, float lastHitDamage) {
            this.periodicDamage = periodicDamage;
            this.burstDamage = burstDamage;
            this.lastAttackerRef = lastAttackerRef;
            this.lastHitDamage = lastHitDamage;
        }
    }
}