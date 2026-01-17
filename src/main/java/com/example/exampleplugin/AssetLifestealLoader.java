package com.example.exampleplugin;

import com.hypixel.hytale.logger.HytaleLogger;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Loads lifesteal values from game assets and populates the LifestealRegistry.
 */
public class AssetLifestealLoader {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Populates the LifestealRegistry from loaded Item assets.
     * Scans Item.getAssetMap().getAssetMap() for lifesteal values.
     */
    public static void populateFromAssets() {
        LOGGER.atInfo().log("Starting lifesteal asset population...");
        
        int loadedCount = 0;
        
        try {
            // Try to get Item class and asset map
            Class<?> itemClass = Class.forName("com.hypixel.hytale.server.core.item.Item");
            
            // Get asset map using reflection
            Method getAssetMapMethod = itemClass.getMethod("getAssetMap");
            Object assetMapWrapper = getAssetMapMethod.invoke(null);
            
            if (assetMapWrapper == null) {
                LOGGER.atWarn().log("Asset map wrapper is null, assets may not be loaded yet");
                return;
            }
            
            // Get the actual asset map
            Method getAssetMapInnerMethod = assetMapWrapper.getClass().getMethod("getAssetMap");
            Object assetMap = getAssetMapInnerMethod.invoke(assetMapWrapper);
            
            if (assetMap == null) {
                LOGGER.atWarn().log("Asset map is null, no assets to load");
                return;
            }
            
            // Iterate through asset map entries
            if (assetMap instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> itemAssets = (Map<String, Object>) assetMap;
                
                LOGGER.atInfo().log("Found %d item assets to scan", itemAssets.size());
                
                for (Map.Entry<String, Object> entry : itemAssets.entrySet()) {
                    String itemId = entry.getKey();
                    Object itemAsset = entry.getValue();
                    
                    // Try to extract lifesteal value
                    Double lifestealValue = extractLifestealValue(itemAsset);
                    
                    if (lifestealValue != null && lifestealValue > 0) {
                        LifestealRegistry.setItemLifesteal(itemId, lifestealValue);
                        loadedCount++;
                        LOGGER.atDebug().log("Registered lifesteal for %s: %.2f%%", itemId, lifestealValue * 100);
                    }
                }
            }
            
            LOGGER.atInfo().log("Lifesteal asset population complete. Loaded %d items.", loadedCount);
            
        } catch (ClassNotFoundException e) {
            LOGGER.atWarn().log("Item class not found. Assets may not be available in this environment.");
        } catch (Exception e) {
            LOGGER.atWarn().withThrowable(e).log("Error loading lifesteal from assets");
        }
    }

    /**
     * Extracts lifesteal value from an item asset using reflection.
     * Looks for patterns like Weapon.StatModifiers.Lifesteal[*].Amount
     */
    private static Double extractLifestealValue(Object itemAsset) {
        try {
            // Try to get weapon data
            Object weaponData = getNestedValue(itemAsset, "Weapon", "weapon", "WeaponData");
            if (weaponData == null) {
                return null;
            }
            
            // Try to get stat modifiers
            Object statModifiers = getNestedValue(weaponData, "StatModifiers", "statModifiers", "Modifiers");
            if (statModifiers == null) {
                return null;
            }
            
            // Try to get lifesteal data
            Object lifestealData = getNestedValue(statModifiers, "Lifesteal", "lifesteal", "LifeSteal");
            if (lifestealData == null) {
                return null;
            }
            
            // If lifestealData is a list/array, get first element
            if (lifestealData instanceof Iterable) {
                for (Object item : (Iterable<?>) lifestealData) {
                    Double amount = extractAmount(item);
                    if (amount != null) {
                        return amount;
                    }
                }
            } else if (lifestealData.getClass().isArray()) {
                Object[] array = (Object[]) lifestealData;
                if (array.length > 0) {
                    Double amount = extractAmount(array[0]);
                    if (amount != null) {
                        return amount;
                    }
                }
            } else {
                // Try to extract amount directly
                return extractAmount(lifestealData);
            }
            
        } catch (Exception e) {
            // Silently fail for items that don't have lifesteal
            LOGGER.atDebug().log("Could not extract lifesteal: %s", e.getMessage());
        }
        
        return null;
    }

    /**
     * Extracts an amount value from an object.
     */
    private static Double extractAmount(Object obj) {
        if (obj == null) {
            return null;
        }
        
        try {
            // If it's already a number
            if (obj instanceof Number) {
                return ((Number) obj).doubleValue();
            }
            
            // Try to get Amount field
            Object amount = getNestedValue(obj, "Amount", "amount", "value", "Value");
            if (amount instanceof Number) {
                return ((Number) amount).doubleValue();
            }
            
        } catch (Exception e) {
            LOGGER.atDebug().log("Could not extract amount: %s", e.getMessage());
        }
        
        return null;
    }

    /**
     * Gets a nested value from an object using reflection with multiple possible field names.
     */
    private static Object getNestedValue(Object obj, String... fieldNames) {
        if (obj == null) {
            return null;
        }
        
        // Try as Map first
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            for (String fieldName : fieldNames) {
                if (map.containsKey(fieldName)) {
                    return map.get(fieldName);
                }
            }
            return null;
        }
        
        // Try reflection on fields
        for (String fieldName : fieldNames) {
            try {
                java.lang.reflect.Field field = findField(obj.getClass(), fieldName);
                if (field != null) {
                    field.setAccessible(true);
                    return field.get(obj);
                }
            } catch (Exception e) {
                // Try next field name
            }
            
            // Try getter methods
            try {
                Method getter = findGetter(obj.getClass(), fieldName);
                if (getter != null) {
                    getter.setAccessible(true);
                    return getter.invoke(obj);
                }
            } catch (Exception e) {
                // Try next field name
            }
        }
        
        return null;
    }

    /**
     * Finds a field in a class hierarchy.
     */
    private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
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
     * Finds a getter method for a field.
     */
    private static Method findGetter(Class<?> clazz, String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        
        String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        
        Class<?> current = clazz;
        while (current != null) {
            try {
                for (Method method : current.getDeclaredMethods()) {
                    if (method.getName().equals(getterName) && method.getParameterCount() == 0) {
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
