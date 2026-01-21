package com.example.exampleplugin.dungeon.command;

import com.example.exampleplugin.dungeon.DungeonManager;
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
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.entity.entities.Player;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * /editdungeon <name> — spawn instance for editing and teleport caller
 */
public class EditDungeonCommand extends AbstractPlayerCommand {
    private final DungeonManager manager;

    public EditDungeonCommand(DungeonManager manager) {
        super("editdungeon", "Spawn and enter a dungeon instance for editing");
        this.manager = manager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World currentWorld) {
        String name = context.getArgString(0);
        if (name == null || name.isBlank()) {
            context.sendMessage(Message.raw("Usage: /editdungeon <name>"));
            return;
        }

        // Determine a sensible return point (spawn provider) or use player's current position
        Transform returnPoint = null;
        try {
            Player p = (Player) store.getComponent(ref, Player.getComponentType());
            if (p != null) {
                returnPoint = p.getTransform().clone();
            }
        } catch (Throwable ignored) {}

        if (returnPoint == null) {
            returnPoint = new Transform();
        }

        // Spawn instance async and teleport player while loading
        CompletableFuture<World> future = manager.spawnDungeonInstanceForEdit(name, currentWorld, returnPoint, ref);
        future.whenComplete((world, ex) -> {
            if (ex != null) {
                playerRef.sendMessage(Message.raw("Failed to spawn edit instance for " + name + ": " + ex.getMessage()));
                return;
            }
            // teleport to loaded instance using InstancesPlugin helper (teleportPlayerToInstance would be used inside InstancesPlugin teleport helpers from UI code)
            try {
                InstancesPlugin.teleportPlayerToInstance(ref, store, world, returnPoint);
            } catch (Throwable t) {
                // As fallback, teleport via loading teleport which most commands use (teleportPlayerToLoadingInstance)
                try {
                    InstancesPlugin.teleportPlayerToLoadingInstance(ref, store, CompletableFuture.completedFuture(world), returnPoint);
                } catch (Throwable t2) {
                    playerRef.sendMessage(Message.raw("Failed to teleport to edit instance: " + t2.getMessage()));
                }
            }
            playerRef.sendMessage(Message.raw("Entered edit instance for dungeon " + name));
        });

        playerRef.sendMessage(Message.raw("Spawning edit instance for " + name + " ..."));
    }
}