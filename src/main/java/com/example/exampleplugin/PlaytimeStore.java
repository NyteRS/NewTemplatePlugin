package com.example.exampleplugin;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple persistent store for per-player cumulative playtime (milliseconds).
 *
 * File format: Java Properties at plugin-data/NewTemplatePlugin/playtimes.properties
 * Key = player-uuid (string), Value = total milliseconds (string long)
 *
 * This class is thread-safe.
 */
public class PlaytimeStore {
    private final Path storeFile;
    private final Map<UUID, Long> playtimes = new ConcurrentHashMap<>();

    public PlaytimeStore() {
        // Store under a directory we control. This should be writable by the server process.
        // Location: working-dir/plugin-data/NewTemplatePlugin/playtimes.properties
        Path dir = Paths.get("plugin-data", "NewTemplatePlugin");
        this.storeFile = dir.resolve("playtimes.properties");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            // best-effort; actual load/save will report errors
        }
    }

    /**
     * Load playtimes from disk. Best-effort; throws no errors to callers.
     */
    public synchronized void load() {
        if (!Files.exists(storeFile)) return;
        Properties props = new Properties();
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(storeFile))) {
            props.load(in);
            for (String key : props.stringPropertyNames()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    long ms = Long.parseLong(props.getProperty(key));
                    playtimes.put(uuid, ms);
                } catch (Exception ignored) {
                    // skip malformed entries
                }
            }
        } catch (IOException e) {
            // ignore load failure (we'll treat as empty store)
        }
    }

    /**
     * Save the current in-memory playtimes to disk. Best-effort; logs nothing here.
     */
    public synchronized void save() {
        Properties props = new Properties();
        for (Map.Entry<UUID, Long> e : playtimes.entrySet()) {
            props.setProperty(e.getKey().toString(), Long.toString(e.getValue()));
        }
        try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(storeFile))) {
            props.store(out, "Player cumulative playtimes (ms) for NewTemplatePlugin");
        } catch (IOException e) {
            // best-effort; ignore
        }
    }

    /**
     * Get stored total ms (0 if not present).
     */
    public long getTotalMillis(UUID playerUuid) {
        Long v = playtimes.get(playerUuid);
        return (v == null) ? 0L : v.longValue();
    }

    /**
     * Add ms to stored total and persist in memory (but not automatically saved to disk here).
     * Caller can call save() to flush immediately.
     */
    public void addMillis(UUID playerUuid, long millis) {
        playtimes.merge(playerUuid, millis, Long::sum);
    }

    /**
     * Set absolute stored total.
     */
    public void setTotalMillis(UUID playerUuid, long totalMillis) {
        playtimes.put(playerUuid, totalMillis);
    }
}