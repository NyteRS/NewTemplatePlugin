package com.example.exampleplugin;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * AutoScoreboardSystem
 * - Attaches a ScoreboardHud to players when their entity is added (auto-show on join).
 *
 * Register it in your plugin setup():
 *   this.getEntityStoreRegistry().registerSystem(new AutoScoreboardSystem());
 */
public class AutoScoreboardSystem extends RefSystem<EntityStore> {
    // Don't statically capture component types; build the Query lazily to avoid class-load order issues.

    @Override
    public Query<EntityStore> getQuery() {
        // The Hytale API commonly casts component types to Query to form AND queries.
        // We create the Query here (not in a static initializer) so component types are ready.
        return (Query<EntityStore>) Query.and(new Query[] {
                (Query) PlayerRef.getComponentType(),
                (Query) Player.getComponentType()
        });
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Acquire components via commandBuffer (pattern used by other systems in the API)
        Player playerComponent = (Player) commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) return;

        PlayerRef playerRefComponent = (PlayerRef) commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRefComponent == null) return;

        HudManager hudManager = playerComponent.getHudManager();

        // Don't overwrite an existing custom HUD (safety)
        if (hudManager.getCustomHud() instanceof ScoreboardHud) return;

        // Create and populate the scoreboard (replace stub getters with real accessors)
        ScoreboardHud hud = new ScoreboardHud(playerRefComponent);
        hud.setTitle("ExampleSMP");
        hud.setLine1("Money: " + getMoney(playerComponent));
        hud.setLine2("Shards: " + getShards(playerComponent));
        hud.setLine3("Kills: " + getKills(playerComponent));
        hud.setLine4("Playtime: " + getPlaytime(playerComponent));

        hudManager.setCustomHud(playerRefComponent, hud);
        hud.show();
    }

    // Required by RefSystem (implement even if empty)
    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // No cleanup currently required when player entity is removed.
    }

    // Stub stat getters - replace these with your plugin's real stat accessors.
    private String getMoney(Player p) { return "0"; }
    private String getShards(Player p) { return "0"; }
    private String getKills(Player p) { return "0"; }
    private String getPlaytime(Player p) { return "0m"; }
}