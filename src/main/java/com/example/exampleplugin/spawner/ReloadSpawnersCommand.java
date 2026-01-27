package com.example.exampleplugin.spawner;

import com.example.exampleplugin.ExamplePlugin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * /reloadspawners
 */
public class ReloadSpawnersCommand extends CommandBase {
    private final ExamplePlugin plugin;

    public ReloadSpawnersCommand(ExamplePlugin plugin) {
        super("reloadspawners", "Reload spawners from spawns.json");
        this.plugin = plugin;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        try {
            int count = plugin.reloadSpawns();
            context.sendMessage(Message.raw("Spawns reloaded. Active spawns: " + count));
        } catch (Throwable t) {
            context.sendMessage(Message.raw("Failed to reload spawns: " + t.getMessage()));
            t.printStackTrace();
        }
    }
}