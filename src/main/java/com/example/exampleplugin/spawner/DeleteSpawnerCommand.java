package com.example.exampleplugin.spawner;

import com.example.exampleplugin.ExamplePlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Iterator;

/**
 * /deletespawner <id>
 */
public class DeleteSpawnerCommand extends CommandBase {
    private final ExamplePlugin plugin;
    private final RequiredArg<String> idArg = withRequiredArg("id", "Spawner id", ArgTypes.STRING);

    public DeleteSpawnerCommand(ExamplePlugin plugin) {
        super("deletespawner", "Delete a spawner from spawns.json");
        this.plugin = plugin;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        String id = idArg.get(context);
        List<SpawnDefinition> defs = SpawnConfigLoader.load(plugin);
        if (defs == null || defs.isEmpty()) {
            context.sendMessage(Message.raw("No spawners to delete."));
            return;
        }

        boolean removed = false;
        Iterator<SpawnDefinition> it = defs.iterator();
        while (it.hasNext()) {
            SpawnDefinition d = it.next();
            if (d != null && d.id != null && d.id.equals(id)) {
                it.remove();
                removed = true;
            }
        }

        if (!removed) {
            context.sendMessage(Message.raw("No spawner with id: " + id));
            return;
        }

        // write back
        try {
            File dataFolder = plugin.getDataDirectory().toFile();
            if (!dataFolder.exists()) dataFolder.mkdirs();
            File file = new File(dataFolder, "spawns.json");
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter w = new FileWriter(file)) {
                gson.toJson(defs, w);
            }

            // reload into running manager
            int count = plugin.reloadSpawns();
            context.sendMessage(Message.raw("Deleted spawner " + id + ". Spawns reloaded (" + count + " active)."));
        } catch (Throwable t) {
            context.sendMessage(Message.raw("Failed to delete spawner: " + t.getMessage()));
            t.printStackTrace();
        }
    }
}