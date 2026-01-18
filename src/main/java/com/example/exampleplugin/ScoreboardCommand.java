package com.example.exampleplugin;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase; // if needed elsewhere
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;

import javax.annotation.Nonnull;

/**
 * /scoreboard - toggles the player's scoreboard HUD on/off.
 *
 * Register this in setup(): this.getCommandRegistry().registerCommand(new ScoreboardCommand());
 */
public class ScoreboardCommand extends AbstractPlayerCommand {
    public ScoreboardCommand() {
        super("scoreboard", "Toggle the server scoreboard HUD.");
        this.setPermissionGroup(GameMode.Adventure); // allow all players
    }

    /**
     * Implement the exact signature required by your AbstractPlayerCommand (same pattern as DungeonUICommand).
     */
    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        // Get the Player component from the store/ref (the pattern used in your repo)
        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("No player found in context."));
            return;
        }

        // Access the player's HudManager and toggle our custom hud
        HudManager hudManager = player.getHudManager();
        if (hudManager == null) {
            context.sendMessage(Message.raw("Unable to access your HUD manager."));
            return;
        }

        if (hudManager.getCustomHud() instanceof ScoreboardHud) {
            // Hide/reset our scoreboard
            hudManager.resetHud(playerRef);
            context.sendMessage(Message.raw("Scoreboard hidden."));
        } else {
            // Create and set a new ScoreboardHud for this player
            ScoreboardHud hud = new ScoreboardHud(playerRef);

            // Fill initial values - replace these helpers with your real stat getters
            hud.setTitle("ExampleSMP");
            hud.setLine1("Money: " + getMoney(player));
            hud.setLine2("Shards: " + getShards(player));
            hud.setLine3("Kills: " + getKills(player));
            hud.setLine4("Playtime: " + getPlaytime(player));

            hudManager.setCustomHud(playerRef, hud);
            hud.show();
            context.sendMessage(Message.raw("Scoreboard shown. Use /scoreboard to hide."));
        }
    }

    // TODO: Replace these stub getters with your plugin's real player stat accessors
    private String getMoney(Player p) { return "0"; }
    private String getShards(Player p) { return "0"; }
    private String getKills(Player p) { return "0"; }
    private String getPlaytime(Player p) { return "0m"; }
}