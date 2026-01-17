package com.example.exampleplugin;

import com.hypixel.hytale.logger.HytaleLogger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Safe merge logic to update item descriptions with lifesteal information.
 * Reads existing descriptions and replaces or appends lifesteal lines without wiping other lore.
 */
public class DescriptionMergeInjector {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String LIFESTEAL_PREFIX = "Lifesteal: ";

    /**
     * Injects lifesteal descriptions into all items that have lifesteal values.
     */
    public static void injectAll() {
        LOGGER.atInfo().log("Starting lifesteal description injection...");
        
        int injectedCount = 0;
        
        try {
            // Try to get Item class and asset map
            Class<?> itemClass = Class.forName("com.hypixel.hytale.server.core.item.Item");
            
            // Get asset map using reflection
            Method getAssetMapMethod = itemClass.getMethod("getAssetMap");
            Object assetMapWrapper = getAssetMapMethod.invoke(null);
            
            if (assetMapWrapper == null) {
                LOGGER.atWarn().log("Asset map wrapper is null, cannot inject descriptions");
                return;
            }
            
            // Get the actual asset map
            Method getAssetMapInnerMethod = assetMapWrapper.getClass().getMethod("getAssetMap");
            Object assetMap = getAssetMapInnerMethod.invoke(assetMapWrapper);
            
            if (assetMap == null) {
                LOGGER.atWarn().log("Asset map is null, no items to update");
                return;
            }
            
            // Iterate through asset map entries
            if (assetMap instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> itemAssets = (Map<String, Object>) assetMap;
                
                LOGGER.atInfo().log("Scanning %d items for lifesteal description updates", itemAssets.size());
                
                for (Map.Entry<String, Object> entry : itemAssets.entrySet()) {
                    String itemId = entry.getKey();
                    Object itemAsset = entry.getValue();
                    
                    // Check if this item has lifesteal
                    Double lifestealValue = LifestealRegistry.getItemLifesteal(itemId);
                    
                    if (lifestealValue != null && lifestealValue > 0) {
                        boolean success = injectDescription(itemId, itemAsset, lifestealValue);
                        if (success) {
                            injectedCount++;
                        }
                    }
                }
            }
            
            LOGGER.atInfo().log("Lifesteal description injection complete. Updated %d items.", injectedCount);
            
        } catch (ClassNotFoundException e) {
            LOGGER.atWarn().log("Item class not found. Assets may not be available in this environment.");
        } catch (Exception e) {
            LOGGER.atWarn().withThrowable(e).log("Error injecting lifesteal descriptions");
        }
    }

    /**
     * Injects or updates the lifesteal description for a single item.
     */
    private static boolean injectDescription(String itemId, Object itemAsset, double lifestealValue) {
        try {
            // Format lifesteal percentage
            String lifestealLine = String.format("%s%.1f%%", LIFESTEAL_PREFIX, lifestealValue * 100);
            
            // Try to get existing description
            List<String> description = getDescription(itemAsset);
            
            if (description == null) {
                description = new ArrayList<>();
            }
            
            // Check if lifesteal line already exists and update it, or add new line
            boolean found = false;
            for (int i = 0; i < description.size(); i++) {
                String line = description.get(i);
                if (line != null && line.startsWith(LIFESTEAL_PREFIX)) {
                    description.set(i, lifestealLine);
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                description.add(lifestealLine);
            }
            
            // Try to set the description back
            boolean success = setDescription(itemAsset, description);
            
            if (success) {
                LOGGER.atDebug().log("Updated description for %s: %s", itemId, lifestealLine);
                return true;
            }
            
        } catch (Exception e) {
            LOGGER.atDebug().log("Could not inject description for %s: %s", itemId, e.getMessage());
        }
        
        return false;
    }

    /**
     * Gets the description from an item asset using reflection.
     */
    private static List<String> getDescription(Object itemAsset) {
        try {
            // Try common field/method names for description
            Object descriptionObj = getNestedValue(itemAsset, "Description", "description", "lore", "Lore");
            
            if (descriptionObj == null) {
                return null;
            }
            
            // Convert to list
            if (descriptionObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) descriptionObj;
                return new ArrayList<>(list);
            } else if (descriptionObj instanceof String) {
                List<String> list = new ArrayList<>();
                list.add((String) descriptionObj);
                return list;
            }
            
        } catch (Exception e) {
            LOGGER.atDebug().log("Could not get description: %s", e.getMessage());
        }
        
        return null;
    }

    /**
     * Sets the description on an item asset using reflection.
     */
    private static boolean setDescription(Object itemAsset, List<String> description) {
        try {
            // Try to find DescriptionEditor if available
            Class<?> descriptionEditorClass = null;
            try {
                descriptionEditorClass = Class.forName("com.hypixel.hytale.server.core.item.DescriptionEditor");
            } catch (ClassNotFoundException e) {
                // DescriptionEditor not available, try direct field access
            }
            
            if (descriptionEditorClass != null) {
                // Use DescriptionEditor methods
                Method editMethod = descriptionEditorClass.getMethod("editDescription", Object.class, List.class);
                editMethod.invoke(null, itemAsset, description);
                return true;
            } else {
                // Try direct field/method access
                return setNestedValue(itemAsset, description, "Description", "description", "lore", "Lore");
            }
            
        } catch (Exception e) {
            LOGGER.atDebug().log("Could not set description: %s", e.getMessage());
        }
        
        return false;
    }

    /**
     * Gets a nested value from an object using reflection.
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
     * Sets a nested value on an object using reflection.
     */
    private static boolean setNestedValue(Object obj, Object value, String... fieldNames) {
        if (obj == null) {
            return false;
        }
        
        // Try as Map first
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            for (String fieldName : fieldNames) {
                map.put(fieldName, value);
                return true;
            }
        }
        
        // Try reflection on fields
        for (String fieldName : fieldNames) {
            try {
                java.lang.reflect.Field field = findField(obj.getClass(), fieldName);
                if (field != null) {
                    field.setAccessible(true);
                    field.set(obj, value);
                    return true;
                }
            } catch (Exception e) {
                // Try next field name
            }
            
            // Try setter methods
            try {
                Method setter = findSetter(obj.getClass(), fieldName, value.getClass());
                if (setter != null) {
                    setter.setAccessible(true);
                    setter.invoke(obj, value);
                    return true;
                }
            } catch (Exception e) {
                // Try next field name
            }
        }
        
        return false;
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

    /**
     * Finds a setter method for a field.
     */
    private static Method findSetter(Class<?> clazz, String fieldName, Class<?> paramType) {
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        
        String setterName = "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        
        Class<?> current = clazz;
        while (current != null) {
            try {
                for (Method method : current.getDeclaredMethods()) {
                    if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
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
