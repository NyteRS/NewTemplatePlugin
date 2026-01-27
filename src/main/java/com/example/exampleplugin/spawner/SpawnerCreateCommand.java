package com.example.exampleplugin.spawner;

import com.example.exampleplugin.ExamplePlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileWriter;
import java.util.List;

/**
 * /spawnercreate <mob> <count> <cooldownSeconds> <activationRadius> <exact> [--id <id>]
 *
 * exact: true -> spawn exactly on coordinates
 *        false -> spawn randomly within radius
 *
 * id is optional. If provided, the command will refuse duplicate ids.
 * The new spawn will be appended to spawns.json and immediately registered in the running manager.
 */
public class SpawnerCreateCommand extends AbstractPlayerCommand {
    private final ExamplePlugin plugin;

    private final RequiredArg<String> mobArg = withRequiredArg("mob", "Mob type id (e.g. zombie)", ArgTypes.STRING);
    private final RequiredArg<Integer> countArg = withRequiredArg("count", "How many to spawn each activation", ArgTypes.INTEGER);
    private final RequiredArg<Integer> cooldownArg = withRequiredArg("cooldown", "Cooldown in seconds", ArgTypes.INTEGER);
    private final RequiredArg<Integer> radiusArg = withRequiredArg("radius", "Activation radius in blocks", ArgTypes.INTEGER);
    private final RequiredArg<Boolean> exactArg = withRequiredArg("exact", "true/false - spawn exactly at coords", ArgTypes.BOOLEAN);

    // optional id argument (user-specified)
    private final OptionalArg<String> idArg = withOptionalArg("id", "Optional spawn id (string) - if provided, must be unique", ArgTypes.STRING);

    public SpawnerCreateCommand(ExamplePlugin plugin) {
        super("spawnercreate", "Create a spawn entry at your current location");
        this.plugin = plugin;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    public com.hypixel.hytale.server.core.command.system.CommandOwner getOwner() {
        return this.plugin;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        if (ref == null || !ref.isValid()) {
            context.sendMessage(Message.raw("Unable to determine your entity reference."));
            return;
        }

        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Player entity not found."));
            return;
        }

        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not read your position."));
            return;
        }

        String mob = this.mobArg.get(context);
        int count = Math.max(1, this.countArg.get(context));
        int cooldownSeconds = Math.max(0, this.cooldownArg.get(context));
        int radius = Math.max(0, this.radiusArg.get(context));
        boolean exact = Boolean.TRUE.equals(this.exactArg.get(context));

        // optional id (user-provided)
        String providedId = null;
        try {
            providedId = this.idArg.get(context);
        } catch (Throwable ignored) {
            providedId = null;
        }

        // Load existing definitions to check for duplicates
        List<SpawnDefinition> list = SpawnConfigLoader.load(this.plugin);
        if (list == null) {
            context.sendMessage(Message.raw("Failed to read spawns.json (cannot check duplicates)."));
            return;
        }
        if (providedId != null && !providedId.isBlank()) {
            for (SpawnDefinition existing : list) {
                if (existing != null && providedId.equals(existing.id)) {
                    context.sendMessage(Message.raw("A spawn with id '" + providedId + "' already exists. Choose a different id."));
                    return;
                }
            }
        }

        SpawnDefinition sd = new SpawnDefinition();
        if (providedId != null && !providedId.isBlank()) {
            sd.id = providedId;
        } else {
            sd.id = "spawn_" + System.currentTimeMillis();
        }

        try {
            sd.world = (world == null) ? null : world.getName();
        } catch (Throwable ignored) {
            sd.world = null;
        }

        Vector3d pos = transform.getPosition();
        sd.x = pos.getX();
        sd.y = pos.getY();
        sd.z = pos.getZ();

        sd.radius = radius;
        sd.mob = mob;
        sd.cooldownMillis = (long) cooldownSeconds * 1000L;
        sd.enabled = true;
        sd.spawnCount = count;
        sd.maxNearby = 6;
        sd.maxAttempts = 8;
        sd.debug = false;
        sd.spawnOnExact = exact;

        try {
            // Append to list (no duplicate check needed here because we checked above)
            list.add(sd);

            File dataFolder = this.plugin.getDataDirectory().toFile();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            File file = new File(dataFolder, "spawns.json");
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter w = new FileWriter(file)) {
                gson.toJson(list, w);
            }

            // Immediately register with running manager
            boolean registered = this.plugin.registerSpawn(sd);
            if (!registered) {
                context.sendMessage(Message.raw("Spawn saved to spawns.json, but failed to register in running manager (duplicate id or manager missing)."));
                context.sendMessage(Message.raw("You may need to run /reloadspawners."));
                return;
            }

            String json = gson.toJson(sd);
            context.sendMessage(Message.raw("Spawn created, saved to spawns.json, and registered:"));
            context.sendMessage(Message.raw(json));
        } catch (Throwable t) {
            context.sendMessage(Message.raw("Failed to save spawn definition: " + t.getMessage()));
            t.printStackTrace();
        }
    }
}