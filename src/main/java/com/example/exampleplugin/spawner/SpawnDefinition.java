package com.example.exampleplugin.spawner;

public class SpawnDefinition {
    public String id;               // optional identifier
    public String world;            // optional world/instance name (match against World.getName())
    public double x;
    public double y;
    public double z;
    public double radius = 10.0;    // trigger radius
    public String mob;              // optional entity type id (for reflection strategy)
    public String commandTemplate;  // optional command template (preferred): e.g. "spawnmob zombie %x %y %z"
    public long cooldownMillis = 30000L; // cooldown between spawns
    public boolean enabled = true;

    // Optional control fields
    public Integer spawnCount;      // number of mobs to attempt per activation
    public Integer maxNearby;       // maximum nearby similar entities allowed
    public Integer maxAttempts;     // maximum attempts to find valid spawn points per activation
    public Boolean debug;           // enable detailed debug logging for this spawn

    // New: if true, spawn exactly at the coordinate (no random offset). If false, spawn randomly within radius.
    public Boolean spawnOnExact;

    @Override
    public String toString() {
        return "SpawnDefinition{" +
                "id='" + id + '\'' +
                ", world='" + world + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", radius=" + radius +
                ", mob='" + mob + '\'' +
                ", commandTemplate='" + commandTemplate + '\'' +
                ", cooldownMillis=" + cooldownMillis +
                ", enabled=" + enabled +
                ", spawnCount=" + spawnCount +
                ", maxNearby=" + maxNearby +
                ", maxAttempts=" + maxAttempts +
                ", debug=" + debug +
                ", spawnOnExact=" + spawnOnExact +
                '}';
    }
}