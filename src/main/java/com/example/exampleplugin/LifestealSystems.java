package com.example.exampleplugin;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.modules.entity.AllLegacyLivingEntityTypesQuery;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;

/**
 * Robust lifesteal system:
 * - prints System.out debug lines so you can see activity
 * - uses reflection to try multiple inventory / item getters
 * - supports explicit ITEM_LIFESTEAL_MAP
 */
public final class LifestealSystems {

    private LifestealSystems() { /* utility */ }

    public static class LifestealOnDamage extends DamageEventSystem {

        private static final double DEFAULT_DAGGER_LIFESTEAL = 0.12;

        // Add your item IDs here (exact match to itemStack.getItemId()).
        private static final Map<String, Double> ITEM_LIFESTEAL_MAP;
        static {
            ITEM_LIFESTEAL_MAP = new HashMap<>();
            ITEM_LIFESTEAL_MAP.put("items/dagger_basic", 0.12);
            // ITEM_LIFESTEAL_MAP.put("items/your_dagger_id", 0.15);
        }

        public LifestealOnDamage() {
            super();
            // Visible registration debug
            System.out.println("[Lifesteal] LifestealOnDamage system constructed");
        }

        @Nonnull
        public Query<EntityStore> getQuery() {
            return (Query<EntityStore>) AllLegacyLivingEntityTypesQuery.INSTANCE;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer,
                           @Nonnull Damage damageEvent) {

            // quick visible marker each event call (comment out later if spammy)
            System.out.println("[Lifesteal] handle() called for Damage event");

            try {
                if (damageEvent == null) return;
                if (damageEvent.isCancelled()) {
                    System.out.println("[Lifesteal] damageEvent cancelled");
                    return;
                }

                float damageAmount = damageEvent.getAmount();
                if (damageAmount <= 0f) {
                    System.out.println("[Lifesteal] damageAmount <= 0");
                    return;
                }

                Damage.Source source = damageEvent.getSource();
                if (!(source instanceof Damage.EntitySource)) {
                    // not entity-sourced (could be environmental/projectile without entity source wrapper)
                    System.out.println("[Lifesteal] source is not EntitySource");
                    return;
                }

                Ref<EntityStore> attackerRef = ((Damage.EntitySource) source).getRef();
                if (attackerRef == null || !attackerRef.isValid()) {
                    System.out.println("[Lifesteal] attackerRef invalid");
                    return;
                }

                @Nullable Entity ent = EntityUtils.getEntity(attackerRef, store);
                if (!(ent instanceof LivingEntity)) {
                    System.out.println("[Lifesteal] attacker is not LivingEntity");
                    return;
                }

                LivingEntity attacker = (LivingEntity) ent;

                ItemStack held = getHeldItemReflective(attacker);
                if (held == null || ItemStack.isEmpty(held)) {
                    System.out.println("[Lifesteal] no held item found");
                    return;
                }

                double lifesteal = getLifestealFromItem(held);
                if (lifesteal <= 0.0) {
                    System.out.println("[Lifesteal] lifesteal == 0 for item " + safeItemId(held));
                    return;
                }

                double heal = damageAmount * lifesteal;
                if (heal <= 0.0) return;

                EntityStatMap statMap = (EntityStatMap) store.getComponent(attackerRef, EntityStatMap.getComponentType());
                if (statMap == null) {
                    System.out.println("[Lifesteal] no EntityStatMap on attacker");
                    return;
                }

                float healFloat = (float) heal;
                statMap.addStatValue(DefaultEntityStatTypes.getHealth(), healFloat);

                // Visible debug
                System.out.println(String.format("[Lifesteal] applied: attackerIndex=%d damage=%.2f lifesteal=%.3f heal=%.2f item=%s",
                        attackerRef.getIndex(), (double) damageAmount, lifesteal, heal, safeItemId(held)));

            } catch (Throwable t) {
                System.out.println("[Lifesteal] handler exception:");
                t.printStackTrace();
            }
        }

        // Attempt many getter names by reflection so we work despite API differences.
        @Nullable
        private ItemStack getHeldItemReflective(@Nonnull LivingEntity attacker) {
            try {
                Object inventory = tryInvoke(attacker, "getInventory");
                if (inventory == null) return null;

                // candidate method names on inventory
                String[] invGetters = { "getActiveHotbarItem", "getActiveHotbarItemStack", "getActiveHotbarItemStack", "getActiveHotbarItem", "getActiveHotbarItem", "getActiveHotbarItem" , "getActiveHotbarItem", "getActiveHotbarItem" };
                // candidate names (some jars used getActiveHotbarItem, getActiveHotbarItemStack, getActiveHotbarItem)
                String[] tries = { "getActiveHotbarItem", "getActiveHotbarItemStack", "getActiveHotbarItem", "getActiveHotbarItem", "getItemInHand", "getActiveItem", "getActiveSlotItem", "getItem" };

                for (String name : tries) {
                    try {
                        Method m = inventory.getClass().getMethod(name);
                        Object out = m.invoke(inventory);
                        if (out instanceof ItemStack) return (ItemStack) out;
                    } catch (NoSuchMethodException ignore) {
                    }
                }

                // fallback: some LivingEntity implementations expose getActiveHotbarItem directly
                for (String name : tries) {
                    try {
                        Method m = attacker.getClass().getMethod(name);
                        Object out = m.invoke(attacker);
                        if (out instanceof ItemStack) return (ItemStack) out;
                    } catch (NoSuchMethodException ignore) {
                    }
                }
            } catch (Throwable t) {
                System.out.println("[Lifesteal] reflection error while getting held item:");
                t.printStackTrace();
            }
            return null;
        }

        // try to call zero-arg method name on obj, return null if not found or invocation failed
        private Object tryInvoke(Object obj, String methodName) {
            try {
                Method m = obj.getClass().getMethod(methodName);
                return m.invoke(obj);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private String safeItemId(ItemStack stack) {
            if (stack == null) return "null";
            try { return String.valueOf(stack.getItemId()); } catch (Throwable t) { return "unknown"; }
        }

        private double getLifestealFromItem(@Nonnull ItemStack itemStack) {
            if (itemStack == null) return 0.0;

            // explicit mapping
            try {
                String id = itemStack.getItemId();
                if (id != null) {
                    Double v = ITEM_LIFESTEAL_MAP.get(id);
                    if (v != null) return v;
                }
            } catch (Throwable ignored) {}

            // categories fallback
            try {
                Item it = itemStack.getItem();
                if (it != null) {
                    String[] cats = it.getCategories();
                    if (cats != null) {
                        for (String c : cats) {
                            if (c != null && c.equalsIgnoreCase("Dagger")) return DEFAULT_DAGGER_LIFESTEAL;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            try {
                String idlower = itemStack.getItemId();
                if (idlower != null && idlower.toLowerCase().contains("dagger")) return DEFAULT_DAGGER_LIFESTEAL;
            } catch (Throwable ignored) {}

            return 0.0;
        }
    }
}