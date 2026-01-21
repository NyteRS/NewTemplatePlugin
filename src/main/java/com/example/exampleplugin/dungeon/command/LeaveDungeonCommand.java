package com.example.exampleplugin.dungeon.command;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;

import javax.annotation.Nonnull;

/**
 * /leavedungeon — leave the current instance (uses InstancesPlugin.exitInstance)
 */
public class LeaveDungeonCommand extends AbstractPlayerCommand {
    public LeaveDungeonCommand() {
        super("leavedungeon", "Leave the current instance/dungeon");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        try {
            InstancesPlugin.exitInstance(ref, store);
            context.sendMessage(Message.raw("Leaving instance..."));
        } catch (Throwable t) {
            context.sendMessage(Message.raw("Failed to leave instance: " + t.getMessage()));
        }
    }
}