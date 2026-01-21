package com.example.exampleplugin.dungeon.command;

import com.example.exampleplugin.dungeon.DungeonManager;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;

/**
 * /savedungeon <name> <assetName?> — saves the current edited instance into an instance asset.
 *
 * NOTE: saveEditedInstanceAsAsset is not implemented fully in DungeonManager. This command demonstrates
 * where to call that logic and provides the place to implement proper export.
 */
public class SaveDungeonCommand extends AbstractPlayerCommand {
    private final DungeonManager manager;

    public SaveDungeonCommand(DungeonManager manager) {
        super("savedungeon", "Save the edited dungeon instance as an instance asset");
        this.manager = manager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String name = context.getArgString(0);
        String asset = context.getArgString(1);
        if (name == null || name.isBlank()) {
            context.sendMessage(Message.raw("Usage: /savedungeon <name> [assetName]"));
            return;
        }
        if (asset == null || asset.isBlank()) {
            asset = "dungeons/" + name;
        }

        try {
            manager.saveEditedInstanceAsAsset(name, asset);
            context.sendMessage(Message.raw("Saved dungeon " + name + " as asset " + asset));
        } catch (UnsupportedOperationException u) {
            context.sendMessage(Message.raw("Save not implemented on server: " + u.getMessage()));
        } catch (Throwable t) {
            context.sendMessage(Message.raw("Failed to save dungeon: " + t.getMessage()));
        }
    }
}