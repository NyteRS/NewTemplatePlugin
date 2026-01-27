package com.example.exampleplugin.spawner;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginBase;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * /spawners list
 */
public class SpawnersCommand extends AbstractCommandCollection {
    private final JavaPlugin plugin;

    public SpawnersCommand(JavaPlugin plugin) {
        super("spawners", "Spawner management commands");
        this.plugin = plugin;
        addSubCommand(new ListCommand());
    }

    private class ListCommand extends CommandBase {
        ListCommand() {
            super("list", "List configured spawners");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            List<SpawnDefinition> defs = SpawnConfigLoader.load(plugin);
            if (defs == null || defs.isEmpty()) {
                context.sendMessage(Message.raw("No spawners configured (spawns.json is empty)."));
                return;
            }

            context.sendMessage(Message.raw("Configured spawners:"));
            for (SpawnDefinition def : defs) {
                if (def == null) continue;
                String id = def.id != null ? def.id : "<no-id>";
                String mob = def.mob != null ? def.mob : (def.commandTemplate != null ? "[command]" : "<none>");
                String enabled = def.enabled ? "enabled" : "disabled";
                String onExact = (def.spawnOnExact != null && def.spawnOnExact) ? "exact" : "radius";
                String line = String.format("- %s : %s @ (%.2f, %.2f, %.2f) world=%s radius=%.2f mode=%s %s",
                        id, mob, def.x, def.y, def.z, def.world == null ? "<any>" : def.world, def.radius, onExact, enabled);
                context.sendMessage(Message.raw(line));
            }
        }
    }
}