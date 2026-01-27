package com.example.exampleplugin.spawner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads spawns.json from plugin data folder.
 *
 * Format: JSON array of SpawnDefinition objects (see SpawnDefinition.java).
 * If the file is missing, a sample file will be created automatically.
 */
public final class SpawnConfigLoader {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String DEFAULT_FILENAME = "spawns.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<SpawnDefinition>>(){}.getType();

    private SpawnConfigLoader() {}

    /**
     * Loads spawn definitions from plugin data folder.
     * Ensures data folder exists and writes a sample file if none exists.
     *
     * @param plugin plugin instance (used for data folder location)
     * @return non-null list (possibly empty) of spawn definitions
     */
    public static List<SpawnDefinition> load(JavaPlugin plugin) {
        try {
            File dataFolder = plugin.getDataDirectory().toFile();
            if (!dataFolder.exists()) {
                if (!dataFolder.mkdirs()) {
                    LOG.atWarning().log("Could not create plugin data folder: %s", dataFolder.getAbsolutePath());
                }
            }

            File file = new File(dataFolder, DEFAULT_FILENAME);
            if (!file.exists()) {
                // write a sample file to help admins
                List<SpawnDefinition> sample = sampleDefinitions();
                try (FileWriter w = new FileWriter(file)) {
                    GSON.toJson(sample, LIST_TYPE, w);
                } catch (Throwable t) {
                    LOG.atWarning().withCause(t).log("Failed to write sample spawns.json");
                }
                return sample;
            }

            try (FileReader r = new FileReader(file)) {
                List<SpawnDefinition> list = GSON.fromJson(r, LIST_TYPE);
                if (list == null) return new ArrayList<>();
                return list;
            } catch (Throwable t) {
                LOG.atWarning().withCause(t).log("Error reading spawns.json, returning empty list");
                return new ArrayList<>();
            }
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("Unexpected error loading spawns.json");
            return new ArrayList<>();
        }
    }

    private static List<SpawnDefinition> sampleDefinitions() {
        List<SpawnDefinition> sample = new ArrayList<>();
        SpawnDefinition s = new SpawnDefinition();
        s.id = "example_zombie_spawn";
        s.world = "Overworld"; // replace with your world/instance name if needed
        s.x = 100;
        s.y = 64;
        s.z = 200;
        s.radius = 10.0;
        s.mob = "zombie";
        s.commandTemplate = "spawnmob zombie %x %y %z"; // optional: server command to run
        s.cooldownMillis = 30_000L;
        s.enabled = true;
        sample.add(s);
        return sample;
    }
}