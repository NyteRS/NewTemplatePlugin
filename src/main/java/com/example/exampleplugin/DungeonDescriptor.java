package com.example.exampleplugin;

/**
 * Simple data holder for a dungeon definition.
 */
public final class DungeonDescriptor {
    private final String id;
    private final String name;
    private final String shortDescription;
    private final String imageAssetId; // optional asset id / URL for later UI

    public DungeonDescriptor(String id, String name, String shortDescription, String imageAssetId) {
        this.id = id;
        this.name = name;
        this.shortDescription = shortDescription;
        this.imageAssetId = imageAssetId;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getShortDescription() { return shortDescription; }
    public String getImageAssetId() { return imageAssetId; }
}