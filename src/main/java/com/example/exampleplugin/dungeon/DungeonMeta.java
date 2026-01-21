package com.example.exampleplugin.dungeon;

/**
 * DungeonMeta — simple metadata holder for a dungeon entry.
 */
public class DungeonMeta {
    private final String name;
    private final String assetName;
    private final boolean permanent;

    public DungeonMeta(String name, String assetName, boolean permanent) {
        this.name = name;
        this.assetName = assetName;
        this.permanent = permanent;
    }

    public String getName() {
        return name;
    }

    /**
     * Asset name (string InstancesPlugin.spawnInstance expects)
     */
    public String getAssetName() {
        return assetName;
    }

    /**
     * Whether this dungeon is a permanent template (true) or ephemeral (false)
     */
    public boolean isPermanent() {
        return permanent;
    }
}