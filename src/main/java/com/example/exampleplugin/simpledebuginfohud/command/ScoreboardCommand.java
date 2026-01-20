package com.example.exampleplugin.simpledebuginfohud.command;

import com.example.exampleplugin.ExamplePlugin;
import com.example.exampleplugin.simpledebuginfohud.data.ScoreboardManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandOwner;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * /autoscoreboard - toggle the automatic scoreboard for the calling player (per-player)
 *
 * Mirrors the DebugCommand pattern and owner so permissions/owner behavior match Debug HUD.
 */
public class ScoreboardCommand extends AbstractPlayerCommand {
    private final ExamplePlugin plugin;
    private final ScoreboardManager manager;

    public ScoreboardCommand(ExamplePlugin plugin, ScoreboardManager manager) {
        super("autoscoreboard", "Toggle the automatic scoreboard HUD");
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public CommandOwner getOwner() {
        return this.plugin;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        boolean current = this.manager.isEnabled(ref);
        boolean next = !current;
        this.manager.setEnabled(ref, next);
        String status = next ? "enabled" : "disabled";
        context.sendMessage(Message.raw("Auto-scoreboard " + status));
    }
}
