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
 * /scoreboard - toggle scoreboard HUD
 */
public class ScoreboardCommand extends AbstractPlayerCommand {
    public ScoreboardCommand() {
        super("scoreboard", "Toggle the server scoreboard HUD.");
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
            // hide
            hudManager.setCustomHud(playerRef, null);
            context.sendMessage(Message.raw("Scoreboard hidden."));
            return;
        }

        // show
        ScoreboardHud hud = new ScoreboardHud(playerRef);
        hud.setServerName("ExampleSMP");
        hud.setGold("Gold: " + getMoney(player));
        hud.setRank("Rank: Member");
        hud.setPlaytime("Playtime: " + getPlaytime(player));
        hud.setCoords("Coords: 0, 0, 0");
        hud.setFooter("www.example.server");

        hudManager.setCustomHud(playerRef, hud);
        hud.show();
        context.sendMessage(Message.raw("Scoreboard shown."));
    }

    private String getMoney(Player p) { return "0"; }
    private String getPlaytime(Player p) { return "0m"; }
}