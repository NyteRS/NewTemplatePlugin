package com.example.exampleplugin.dungeon.command;

import com.example.exampleplugin.dungeon.DungeonManager;
import com.example.exampleplugin.dungeon.DungeonMeta;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;

/**
 * /createdungeon <name> <assetName?> - register a new dungeon template (assetName optional)
 *
 * Basic: registers a dungeon meta entry in DungeonManager.
 * Note: this does not create an instance asset on disk; use /savedungeon after editing to export.
 */
public class CreateDungeonCommand extends AbstractPlayerCommand {
    private final DungeonManager manager;

    public CreateDungeonCommand(DungeonManager manager) {
        super("createdungeon", "Create/register a new dungeon entry");
        this.manager = manager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String name = context.getArgString(0);
        String assetName = context.getArgString(1);
        if (name == null || name.isBlank()) {
            context.sendMessage(Message.raw("Usage: /createdungeon <name> [assetName]"));
            return;
        }
        if (assetName == null || assetName.isBlank()) {
            // default assetName to dungeons/<name> - you can change convention
            assetName = "dungeons/" + name;
        }
        // Register as non-permanent initially; admin can save later to create asset
        manager.registerDungeon(name, assetName, false);
        context.sendMessage(Message.raw("Registered dungeon " + name + " -> asset: " + assetName));
    }
}