package com.example.exampleplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * /dungeonui - opens the prototype graphical dungeon browser (player-only)
 */
public class DungeonUICommand extends AbstractPlayerCommand {
    public DungeonUICommand() {
        super("dungeonui", "Open the dungeon browser UI (prototype)");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        // Open the DungeonsPage using the player's PageManager (same style as whitelist manager)
        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        DungeonsPage page = new DungeonsPage(playerRef);
        player.getPageManager().openCustomPage(ref, store, page);
    }
}