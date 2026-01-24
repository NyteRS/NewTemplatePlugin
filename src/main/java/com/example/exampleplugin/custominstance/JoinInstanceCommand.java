package com.example.exampleplugin.custominstance;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * /joininstance <assetName>
 *
 * Spawn and join an instance template (asset) by name. Uses InstancesPlugin.spawnInstance(...)
 * and teleports the player while the instance loads.
 *
 * Example usage:
 *  /joininstance dungeons/test
 */
public class JoinInstanceCommand extends AbstractPlayerCommand {
    @Nonnull
    private final RequiredArg<String> instanceArg = withRequiredArg("instance", "Instance template name (e.g. dungeons/test)", (ArgumentType) ArgTypes.STRING);

    public JoinInstanceCommand() {
        super("joininstance", "Spawn and join an instance template");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World currentWorld) {
        String assetName = (String) this.instanceArg.get(context);
        if (assetName == null || assetName.isBlank()) {
            context.sendMessage(Message.raw("Usage: /joininstance <instanceAssetName> (e.g. dungeons/test)"));
            return;
        }

        // Determine a return point (player current transform) - fallback to new Transform()
        Transform returnPoint = computeReturnPoint(store, ref);

        context.sendMessage(Message.raw("Spawning instance " + assetName + " ..."));

        CompletableFuture<World> instanceFuture;
        try {
            instanceFuture = InstancesPlugin.get().spawnInstance(assetName, currentWorld, returnPoint);
        } catch (Throwable t) {
            context.sendMessage(Message.raw("Failed to spawn instance (sync): " + t.getMessage()));
            return;
        }

        // Teleport the player while the instance loads; this handles async world creation
        try {
            InstancesPlugin.teleportPlayerToLoadingInstance(ref, (ComponentAccessor) store, instanceFuture, returnPoint);
            playerRef.sendMessage(Message.raw("Joining instance " + assetName + " ..."));
        } catch (Throwable t) {
            // If teleport helper throws, report to player
            context.sendMessage(Message.raw("Failed to teleport to instance: " + t.getMessage()));
        }
    }

    /**
     * Read TransformComponent from the player's entity to get return point (clone it). Fallback to new Transform.
     */
    private static Transform computeReturnPoint(Store<EntityStore> store, Ref<EntityStore> ref) {
        try {
            TransformComponent tc = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
            if (tc != null) {
                Transform t = tc.getTransform();
                if (t != null) return t.clone();
            }
        } catch (Throwable ignored) {}
        return new Transform();
    }
}