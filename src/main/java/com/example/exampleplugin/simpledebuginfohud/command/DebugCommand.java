package com.example.exampleplugin.simpledebuginfohud.command;

import com.example.exampleplugin.ExamplePlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandOwner;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.example.exampleplugin.simpledebuginfohud.data.DebugManager;

public class DebugCommand extends AbstractPlayerCommand {
    private final ExamplePlugin plugin;
    private final DebugManager debugManager;

    public DebugCommand(ExamplePlugin plugin, DebugManager debugManager) {
        super("debug", "Toggle the debug HUD");
        this.plugin = plugin;
        this.debugManager = debugManager;
    }

    public CommandOwner getOwner() {
        return this.plugin;
    }

    protected boolean canGeneratePermission() {
        return false;
    }

    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        boolean currentState = this.debugManager.isDebugEnabled(ref);
        boolean newState = !currentState;
        this.debugManager.setDebugEnabled(ref, newState);
        String statusText = newState ? "enabled" : "disabled";
        context.sendMessage(Message.raw("Debug HUD " + statusText));
    }
}