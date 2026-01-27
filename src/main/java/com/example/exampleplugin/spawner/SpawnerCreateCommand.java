package com.example.exampleplugin.spawner;

import com.example.exampleplugin.ExamplePlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
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
 * /spawnercreate <mob> <count> <cooldownSeconds> <activationRadius>
 *
 * Creates a spawn entry using the player's current world & position and appends it to spawns.json.
 * Example: /spawnercreate zombie 3 30 10
 */
public class SpawnerCreateCommand extends AbstractPlayerCommand {
    private final ExamplePlugin plugin;

    // Required arguments
    private final RequiredArg<String> mobArg = withRequiredArg("mob", "Mob type id (e.g. zombie)", ArgTypes.STRING);
    private final RequiredArg<Integer> countArg = withRequiredArg("count", "How many to spawn each activation", ArgTypes.INTEGER);
    private final RequiredArg<Integer> cooldownArg = withRequiredArg("cooldown", "Cooldown in seconds", ArgTypes.INTEGER);
    private final RequiredArg<Integer> radiusArg = withRequiredArg("radius", "Activation radius in blocks", ArgTypes.INTEGER);

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
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        // Validate world & player ref
        if (ref == null || !ref.isValid()) {
            context.sendMessage(Message.raw("Unable to determine your entity reference."));
            return;
        }

        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Player entity not found."));
            return;
        }

        // get transform component for position
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not read your position."));
            return;
        }

        // read args
        String mob = this.mobArg.get(context);
        int count = Math.max(1, this.countArg.get(context));
        int cooldownSeconds = Math.max(0, this.cooldownArg.get(context));
        int radius = Math.max(0, this.radiusArg.get(context));

        // Build spawn definition
        SpawnDefinition sd = new SpawnDefinition();
        sd.id = "spawn_" + System.currentTimeMillis();
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

        // Fill optional fields so your loader/strategy can pick them up (since we added typed fields)
        sd.spawnCount = count;
        sd.maxNearby = 6;    // sensible default; you may want to ask the player or expose another arg
        sd.maxAttempts = 8;
        sd.debug = false;

        // Append to spawns.json (use same data folder as loader)
        try {
            List<SpawnDefinition> list = SpawnConfigLoader.load(this.plugin);
            list.add(sd);

            File dataFolder = this.plugin.getDataDirectory().toFile();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            File file = new File(dataFolder, "spawns.json");
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter w = new FileWriter(file)) {
                gson.toJson(list, w);
            }

            String json = gson.toJson(sd);
            context.sendMessage(Message.raw("Spawn created and saved to spawns.json:"));
            context.sendMessage(Message.raw(json));
        } catch (Throwable t) {
            context.sendMessage(Message.raw("Failed to save spawn definition: " + t.getMessage()));
            t.printStackTrace();
        }
    }
}