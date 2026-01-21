package com.example.exampleplugin.dungeon;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Ref;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

/**
 * DungeonManager
 *
 * Keeps a registry of named dungeons and provides helper methods to spawn/edit/join them using InstancesPlugin.
 *
 * Notes:
 * - This class is a starting point. The "saveAsAsset" method is left as a TODO because exporting a running World
 *   to an instance asset depends on your server's instance-asset layout and world persistence APIs.
 */
public class DungeonManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, DungeonMeta> dungeons = new ConcurrentHashMap<>();
    private final Map<String, EditingSession> editingSessions = new ConcurrentHashMap<>();

    public DungeonManager() {}

    /**
     * Register a dungeon by name. assetName is the instance asset ID that InstancesPlugin.spawnInstance will accept.
     * Example: "dungeons/my-first-dungeon" or simply "my-dungeon" depending on your asset layout.
     */
    public void registerDungeon(String name, String assetName, boolean permanent) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(assetName);
        dungeons.put(name.toLowerCase(), new DungeonMeta(name, assetName, permanent));
    }

    public DungeonMeta getDungeon(String name) {
        if (name == null) return null;
        return dungeons.get(name.toLowerCase());
    }

    public boolean hasDungeon(String name) {
        return getDungeon(name) != null;
    }

    /**
     * Spawn an instance for play (async). Returns a future that completes with the World instance.
     * parentWorld: the world that will be treated as parent (return location).
     */
    public CompletableFuture<World> spawnDungeonInstanceForPlay(DungeonMeta meta, World parentWorld, Transform returnPoint) {
        Objects.requireNonNull(meta);
        Objects.requireNonNull(parentWorld);
        String assetName = meta.getAssetName();
        LOGGER.atInfo().log("DungeonManager: spawning instance for play asset=%s parent=%s", assetName, parentWorld.getName());
        try {
            return InstancesPlugin.get().spawnInstance(assetName, parentWorld, returnPoint);
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("DungeonManager: failed to call spawnInstance for asset=%s", assetName);
            CompletableFuture<World> failed = new CompletableFuture<>();
            failed.completeExceptionally(t);
            return failed;
        }
    }

    /**
     * Spawn an instance for editing (admin). We track an EditingSession so we can later save it.
     * Returns the future world.
     */
    public CompletableFuture<World> spawnDungeonInstanceForEdit(String dungeonName, World parentWorld, Transform returnPoint, Ref<EntityStore> editorRef) {
        DungeonMeta meta = getDungeon(dungeonName);
        if (meta == null) {
            CompletableFuture<World> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Unknown dungeon: " + dungeonName));
            return failed;
        }
        LOGGER.atInfo().log("DungeonManager: spawning instance for edit name=%s asset=%s", dungeonName, meta.getAssetName());
        CompletableFuture<World> future = InstancesPlugin.get().spawnInstance(meta.getAssetName(), parentWorld, returnPoint);
        // track editing session (will fill world once future completes)
        EditingSession session = new EditingSession(dungeonName, editorRef, future, returnPoint);
        editingSessions.put(dungeonName.toLowerCase(), session);

        future.whenComplete((world, ex) -> {
            if (ex != null) {
                LOGGER.atWarning().withCause(ex).log("DungeonManager: failed to spawn edit instance for %s", dungeonName);
                editingSessions.remove(dungeonName.toLowerCase());
                return;
            }
            session.setInstanceWorld(world);
            LOGGER.atInfo().log("DungeonManager: edit instance ready for %s -> world=%s", dungeonName, world.getName());
        });
        return future;
    }

    public EditingSession getEditingSession(String dungeonName) {
        return editingSessions.get(dungeonName == null ? null : dungeonName.toLowerCase());
    }

    public void endEditingSession(String dungeonName) {
        if (dungeonName == null) return;
        editingSessions.remove(dungeonName.toLowerCase());
    }

    /**
     * Save edited instance to an instance asset that can later be spawned by spawnInstance(assetName,...).
     *
     * NOTE: Implementing this properly requires:
     * - locating the instance world's on-disk path (World API / Universe API)
     * - copying the world folder into the Instances assets directory following InstancesPlugin conventions
     * - writing an instance.bson / WorldConfig suitable for InstancesPlugin to spawn later.
     *
     * The exact implementation depends on your runtime and the InstancesPlugin helper methods available.
     * Below is a placeholder that indicates expected behaviour.
     */
    public void saveEditedInstanceAsAsset(String dungeonName, String assetName) throws Exception {
        EditingSession session = getEditingSession(dungeonName);
        if (session == null || session.getInstanceWorld() == null) {
            throw new IllegalStateException("No active edit session or world not ready for dungeon " + dungeonName);
        }
        World instanceWorld = session.getInstanceWorld();

        // TODO: implement actual copy/export logic here.
        // Suggested approach:
        // 1) Determine instance asset path: InstancesPlugin.getInstanceAssetPath(assetName) (if available)
        // 2) Determine instanceWorld filesystem path (World.getPath() or Universe.getPath()/world folder)
        // 3) Stop non-essential ticking / set config flags while copying if needed
        // 4) Copy files from instanceWorld folder -> instance asset folder
        // 5) Create/write instance.bson (WorldConfig) describing the instance template (InstanceWorldConfig)
        //
        // For now, we throw to indicate the operation is unimplemented.
        throw new UnsupportedOperationException("saveEditedInstanceAsAsset not implemented. Implement copy of world folder -> instance asset and write instance.bson");
    }
}