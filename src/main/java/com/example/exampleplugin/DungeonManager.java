package com.example.exampleplugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry and simple pagination helpers for dungeons.
 * For now this is in-memory. Later we can persist to JSON/assets or a datastore.
 */
public final class DungeonManager {
    private static final List<DungeonDescriptor> DUNGEONS = new ArrayList<>();

    static {
        // Example/demo entries - replace with your real dungeons or register them at runtime
        DUNGEONS.add(new DungeonDescriptor("d1", "Dungeon 1", "A small starter dungeon", "assets/dungeon1"));
        DUNGEONS.add(new DungeonDescriptor("d2", "Dungeon 2", "A medium difficulty den", "assets/dungeon2"));
        DUNGEONS.add(new DungeonDescriptor("d3", "Dungeon 3", "A cavern of trials", "assets/dungeon3"));
        // Add more test items so pagination can be tested
        for (int i = 4; i <= 18; i++) {
            DUNGEONS.add(new DungeonDescriptor("d" + i, "Dungeon " + i, "Generated test dungeon #" + i, "assets/dungeon" + i));
        }
    }

    public static List<DungeonDescriptor> all() {
        return Collections.unmodifiableList(DUNGEONS);
    }

    public static DungeonDescriptor getById(String id) {
        for (DungeonDescriptor d : DUNGEONS) {
            if (d.getId().equalsIgnoreCase(id)) return d;
        }
        return null;
    }

    public static void register(DungeonDescriptor descriptor) {
        if (descriptor == null) return;
        DUNGEONS.add(descriptor);
    }

    /**
     * Returns a sublist for a page (1-based page).
     */
    public static List<DungeonDescriptor> listPage(int page, int pageSize) {
        if (page < 1) page = 1;
        int total = DUNGEONS.size();
        int from = (page - 1) * pageSize;
        if (from >= total) return Collections.emptyList();
        int to = Math.min(total, from + pageSize);
        return Collections.unmodifiableList(DUNGEONS.subList(from, to));
    }

    public static int pageCount(int pageSize) {
        if (pageSize <= 0) return 1;
        return (DUNGEONS.size() + pageSize - 1) / pageSize;
    }
}