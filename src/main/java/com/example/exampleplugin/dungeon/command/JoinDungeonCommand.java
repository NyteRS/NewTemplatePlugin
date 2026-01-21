package com.example.exampleplugin.dungeon.command;

import com.example.exampleplugin.dungeon.DungeonManager;
import com.example.exampleplugin.dungeon.DungeonMeta;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * /joindungeon <name> — spawn or load a dungeon instance and teleport the player to it (play mode).
 */
public class JoinDungeonCommand extends AbstractPlayerCommand {
    private final DungeonManager manager;

    public JoinDungeonCommand(DungeonManager manager) {
        super("joindungeon", "Join a dungeon instance");
        this.manager = manager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World currentWorld) {
        String name = context.getArgString(0);
        if (name == null || name.isBlank()) {
            context.sendMessage(Message.raw("Usage: /joindungeon <name>"));
            return;
        }
        DungeonMeta meta = manager.getDungeon(name);
        if (meta == null) {
            context.sendMessage(Message.raw("Unknown dungeon: " + name));
            return;
        }

        Transform returnPoint = null; // optional: create a return point
        CompletableFuture<World> future = manager.spawnDungeonInstanceForPlay(meta, currentWorld, returnPoint);
        future.whenComplete((world, ex) -> {
            if (ex != null) {
                playerRef.sendMessage(Message.raw("Failed to spawn dungeon instance: " + ex.getMessage()));
                return;
            }
            try {
                InstancesPlugin.teleportPlayerToLoadingInstance(ref, store, CompletableFuture.completedFuture(world), returnPoint);
                playerRef.sendMessage(Message.raw("Entering dungeon " + name));
            } catch (Throwable t) {
                playerRef.sendMessage(Message.raw("Failed to teleport to dungeon: " + t.getMessage()));
            }
        });

        playerRef.sendMessage(Message.raw("Spawning dungeon " + name + "..."));
    }
}