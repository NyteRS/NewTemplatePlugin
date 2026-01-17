package com.example.exampleplugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe registry for storing item lifesteal fractions.
 * Maps item IDs to their lifesteal percentages (0.0 to 1.0).
 */
public class LifestealRegistry {
    private static final ConcurrentMap<String, Double> lifestealMap = new ConcurrentHashMap<>();

    /**
     * Sets the lifesteal fraction for a specific item.
     *
     * @param itemId The item identifier
     * @param lifestealFraction The lifesteal fraction (0.0 to 1.0)
     */
    public static void setItemLifesteal(String itemId, double lifestealFraction) {
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        lifestealMap.put(itemId, lifestealFraction);
    }

    /**
     * Gets the lifesteal fraction for a specific item.
     *
     * @param itemId The item identifier
     * @return The lifesteal fraction, or null if not found
     */
    public static Double getItemLifesteal(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        return lifestealMap.get(itemId);
    }

    /**
     * Clears all lifesteal mappings from the registry.
     */
    public static void clear() {
        lifestealMap.clear();
    }

    /**
     * Gets the number of registered items.
     *
     * @return The size of the registry
     */
    public static int size() {
        return lifestealMap.size();
    }
}
