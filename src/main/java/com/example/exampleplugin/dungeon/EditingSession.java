package com.example.exampleplugin.dungeon;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.CompletableFuture;

/**
 * EditingSession tracks an admin editing session for a dungeon instance.
 */
public class EditingSession {
    private final String dungeonName;
    private final Ref<EntityStore> editorRef;
    private final CompletableFuture<World> worldFuture;
    private final Transform returnPoint;

    // set once the future completes
    private volatile World instanceWorld;

    public EditingSession(String dungeonName, Ref<EntityStore> editorRef, CompletableFuture<World> worldFuture, Transform returnPoint) {
        this.dungeonName = dungeonName;
        this.editorRef = editorRef;
        this.worldFuture = worldFuture;
        this.returnPoint = returnPoint;
    }

    public String getDungeonName() {
        return dungeonName;
    }

    public Ref<EntityStore> getEditorRef() {
        return editorRef;
    }

    public CompletableFuture<World> getWorldFuture() {
        return worldFuture;
    }

    public Transform getReturnPoint() {
        return returnPoint;
    }

    public World getInstanceWorld() {
        return instanceWorld;
    }

    public void setInstanceWorld(World instanceWorld) {
        this.instanceWorld = instanceWorld;
    }
}