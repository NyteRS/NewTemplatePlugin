package com.example.exampleplugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.store.entity.system.DamageEventSystem;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Damage event system that implements lifesteal combat mechanics.
 */
public class LifestealSystems {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Hardcoded lifesteal overrides that take precedence over registry values.
     */
    private static final Map<String, Double> ITEM_LIFESTEAL_MAP = new HashMap<>();

    static {
        // Example hardcoded overrides
        // ITEM_LIFESTEAL_MAP.put("hytale:vampire_blade", 0.25);
    }

    /**
     * DamageEventSystem implementation that handles lifesteal on damage.
     */
    public static class LifestealOnDamage extends DamageEventSystem {
        private static final double DEFAULT_DAGGER_LIFESTEAL = 0.12; // 12%

        @Override
        public void onDamage(Object damageEvent) {
            try {
                // Extract attacker entity using reflection
                Object attackerEntity = getFieldValue(damageEvent, "attacker", "source", "damageSource");
                if (attackerEntity == null) {
                    return;
                }

                // Extract held item using reflection
                Object heldItem = getHeldItem(attackerEntity);
                if (heldItem == null) {
                    return;
                }

                // Get item ID
                String itemId = getItemId(heldItem);
                if (itemId == null) {
                    return;
                }

                // Determine lifesteal fraction with precedence:
                // 1. Hardcoded ITEM_LIFESTEAL_MAP
                // 2. LifestealRegistry
                // 3. Dagger category default (12%)
                Double lifestealFraction = null;

                // Check hardcoded map first
                if (ITEM_LIFESTEAL_MAP.containsKey(itemId)) {
                    lifestealFraction = ITEM_LIFESTEAL_MAP.get(itemId);
                }

                // Check registry
                if (lifestealFraction == null) {
                    lifestealFraction = LifestealRegistry.getItemLifesteal(itemId);
                }

                // Check if it's a dagger (default fallback)
                if (lifestealFraction == null && isDagger(heldItem, itemId)) {
                    lifestealFraction = DEFAULT_DAGGER_LIFESTEAL;
                }

                // If no lifesteal applicable, return
                if (lifestealFraction == null || lifestealFraction <= 0) {
                    return;
                }

                // Get damage amount
                Double damageAmount = getDamageAmount(damageEvent);
                if (damageAmount == null || damageAmount <= 0) {
                    return;
                }

                // Calculate heal amount
                double healAmount = damageAmount * lifestealFraction;

                // Apply heal using EntityStatMap
                applyHeal(attackerEntity, healAmount);

                LOGGER.atDebug().log("Lifesteal applied: item=%s, damage=%.2f, heal=%.2f, fraction=%.2f",
                        itemId, damageAmount, healAmount, lifestealFraction);

            } catch (Exception e) {
                LOGGER.atWarn().withThrowable(e).log("Error processing lifesteal on damage");
            }
        }

        /**
         * Gets the held item from an entity using reflection.
         */
        private Object getHeldItem(Object entity) {
            try {
                // Try various method names for getting held item
                Object item = invokeMethod(entity, "getHeldItem");
                if (item == null) {
                    item = invokeMethod(entity, "getMainHandItem");
                }
                if (item == null) {
                    item = invokeMethod(entity, "getEquippedItem");
                }
                if (item == null) {
                    // Try getting through inventory
                    Object inventory = invokeMethod(entity, "getInventory");
                    if (inventory != null) {
                        item = invokeMethod(inventory, "getHeldItem");
                        if (item == null) {
                            item = invokeMethod(inventory, "getSelectedItem");
                        }
                    }
                }
                return item;
            } catch (Exception e) {
                LOGGER.atDebug().log("Could not get held item: %s", e.getMessage());
                return null;
            }
        }

        /**
         * Gets the item ID from an item object using reflection.
         */
        private String getItemId(Object item) {
            try {
                // Try getId() method
                Object id = invokeMethod(item, "getId");
                if (id != null) {
                    return id.toString();
                }

                // Try getItemId() method
                id = invokeMethod(item, "getItemId");
                if (id != null) {
                    return id.toString();
                }

                // Try getType() method
                Object type = invokeMethod(item, "getType");
                if (type != null) {
                    Object typeId = invokeMethod(type, "getId");
                    if (typeId != null) {
                        return typeId.toString();
                    }
                }

                return null;
            } catch (Exception e) {
                LOGGER.atDebug().log("Could not get item ID: %s", e.getMessage());
                return null;
            }
        }

        /**
         * Checks if an item is a dagger using reflection.
         */
        private boolean isDagger(Object item, String itemId) {
            // Check by ID pattern
            if (itemId != null && (itemId.toLowerCase().contains("dagger") || 
                                   itemId.toLowerCase().contains("knife"))) {
                return true;
            }

            try {
                // Try to get category
                Object category = invokeMethod(item, "getCategory");
                if (category == null) {
                    Object type = invokeMethod(item, "getType");
                    if (type != null) {
                        category = invokeMethod(type, "getCategory");
                    }
                }

                if (category != null) {
                    String categoryStr = category.toString().toLowerCase();
                    return categoryStr.contains("dagger") || categoryStr.contains("knife");
                }
            } catch (Exception e) {
                LOGGER.atDebug().log("Could not check dagger category: %s", e.getMessage());
            }

            return false;
        }

        /**
         * Gets the damage amount from a damage event using reflection.
         */
        private Double getDamageAmount(Object damageEvent) {
            try {
                Object amount = getFieldValue(damageEvent, "damage", "amount", "damageAmount");
                if (amount instanceof Number) {
                    return ((Number) amount).doubleValue();
                }
                return null;
            } catch (Exception e) {
                LOGGER.atDebug().log("Could not get damage amount: %s", e.getMessage());
                return null;
            }
        }

        /**
         * Applies healing to an entity using EntityStatMap.
         */
        private void applyHeal(Object entity, double healAmount) {
            try {
                // Try to use EntityStatMap.addStatValue
                Class<?> entityStatMapClass = Class.forName("com.hypixel.hytale.server.core.store.entity.EntityStatMap");
                Method addStatValueMethod = entityStatMapClass.getMethod("addStatValue", Object.class, String.class, double.class);
                
                // Add to health stat
                addStatValueMethod.invoke(null, entity, "health", healAmount);
                
                LOGGER.atDebug().log("Heal applied: %.2f", healAmount);
            } catch (ClassNotFoundException e) {
                LOGGER.atDebug().log("EntityStatMap not found, trying alternative healing method");
                applyHealAlternative(entity, healAmount);
            } catch (Exception e) {
                LOGGER.atDebug().log("Could not apply heal via EntityStatMap: %s", e.getMessage());
                applyHealAlternative(entity, healAmount);
            }
        }

        /**
         * Alternative healing method using reflection.
         */
        private void applyHealAlternative(Object entity, double healAmount) {
            try {
                // Try setHealth method
                Method getHealthMethod = findMethod(entity.getClass(), "getHealth");
                Method setHealthMethod = findMethodWithParam(entity.getClass(), "setHealth", double.class);
                
                if (getHealthMethod != null && setHealthMethod != null) {
                    Object currentHealth = getHealthMethod.invoke(entity);
                    if (currentHealth instanceof Number) {
                        double newHealth = ((Number) currentHealth).doubleValue() + healAmount;
                        setHealthMethod.invoke(entity, newHealth);
                        LOGGER.atDebug().log("Heal applied via alternative method: %.2f", healAmount);
                    }
                }
            } catch (Exception e) {
                LOGGER.atDebug().log("Alternative heal method failed: %s", e.getMessage());
            }
        }

        /**
         * Finds a method with a single parameter in a class or its superclasses.
         */
        private Method findMethodWithParam(Class<?> clazz, String methodName, Class<?> paramType) {
            Class<?> current = clazz;
            while (current != null) {
                try {
                    for (Method method : current.getDeclaredMethods()) {
                        if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                            Class<?>[] paramTypes = method.getParameterTypes();
                            if (paramTypes[0].equals(paramType) || 
                                (paramType.isPrimitive() && isCompatiblePrimitive(paramTypes[0], paramType))) {
                                return method;
                            }
                        }
                    }
                    current = current.getSuperclass();
                } catch (Exception e) {
                    break;
                }
            }
            return null;
        }

        /**
         * Checks if a wrapper class is compatible with a primitive type.
         */
        private boolean isCompatiblePrimitive(Class<?> wrapper, Class<?> primitive) {
            if (primitive == double.class) {
                return wrapper == Double.class;
            } else if (primitive == float.class) {
                return wrapper == Float.class;
            } else if (primitive == int.class) {
                return wrapper == Integer.class;
            } else if (primitive == long.class) {
                return wrapper == Long.class;
            }
            return false;
        }

        /**
         * Helper method to get field value using reflection with multiple possible field names.
         */
        private Object getFieldValue(Object obj, String... fieldNames) {
            for (String fieldName : fieldNames) {
                try {
                    Field field = findField(obj.getClass(), fieldName);
                    if (field != null) {
                        field.setAccessible(true);
                        return field.get(obj);
                    }
                } catch (Exception e) {
                    // Try next field name
                }
            }
            return null;
        }

        /**
         * Helper method to invoke a method using reflection.
         */
        private Object invokeMethod(Object obj, String methodName) {
            try {
                Method method = findMethod(obj.getClass(), methodName);
                if (method != null) {
                    method.setAccessible(true);
                    return method.invoke(obj);
                }
            } catch (Exception e) {
                // Method not found or invocation failed
            }
            return null;
        }

        /**
         * Finds a field in a class or its superclasses.
         */
        private Field findField(Class<?> clazz, String fieldName) {
            Class<?> current = clazz;
            while (current != null) {
                try {
                    return current.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                }
            }
            return null;
        }

        /**
         * Finds a method in a class or its superclasses.
         */
        private Method findMethod(Class<?> clazz, String methodName) {
            Class<?> current = clazz;
            while (current != null) {
                try {
                    for (Method method : current.getDeclaredMethods()) {
                        if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                            return method;
                        }
                    }
                    current = current.getSuperclass();
                } catch (Exception e) {
                    break;
                }
            }
            return null;
        }
    }
}
