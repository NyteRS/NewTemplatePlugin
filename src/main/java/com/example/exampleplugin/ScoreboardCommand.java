package com.example.exampleplugin;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;

import javax.annotation.Nonnull;

/**
 * /scoreboard - toggles the scoreboard HUD on/off
 */
public class ScoreboardCommand extends AbstractPlayerCommand {
    public ScoreboardCommand() {
        super("scoreboard", "Toggle the scoreboard HUD.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("No player found"));
            return;
        }

        HudManager hudManager = player.getHudManager();
        if (hudManager == null) {
            context.sendMessage(Message.raw("No HUD manager"));
            return;
        }

        if (hudManager.getCustomHud() instanceof ScoreboardHud) {
            // use registrar to hide so MultipleHUD will handle it if present
            com.example.exampleplugin.darkvalehud.hud.DarkvaleHudRegistrar.hideHud(player, playerRef);
            context.sendMessage(Message.raw("Scoreboard hidden."));
            return;
        }

        ScoreboardHud hud = new ScoreboardHud(playerRef);
        hud.setServerName("ExampleSMP");
        hud.setGold("Gold: 0");
        hud.setRank("Rank: Member");
        hud.setPlaytime("Playtime: 0m");
        hud.setCoords("Coords: 0, 0, 0");
        hud.setFooter("www.example.server");

        // register via registrar (MultipleHUD aware)
        com.example.exampleplugin.darkvalehud.hud.DarkvaleHudRegistrar.showHud(player, playerRef, hud);
        hud.show();
        context.sendMessage(Message.raw("Scoreboard shown."));
    }
}