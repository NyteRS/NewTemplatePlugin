package com.example.exampleplugin;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ComponentType;
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
 * Important:
 * - The system builds its Query lazily and defensively: if the component types are not ready yet,
 *   it returns Query.any() so registration won't cause NPEs. The onEntityAdded() method itself
 *   still checks for the actual components before taking action, so there is no accidental behaviour.
 *
 * Register this system with:
 *   this.getEntityStoreRegistry().registerSystem(new AutoScoreboardSystem());
 *
 * Prefer registering in start() rather than setup() to avoid class-load order issues.
 */
public final class AutoScoreboardSystem extends RefSystem<EntityStore> {

    @Override
    public Query<EntityStore> getQuery() {
        // Avoid building a Query from component types too early (class-load order).
        // Fetch component types now and return a precise query only if both types are available.
        ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
        ComponentType<EntityStore, Player> playerType = Player.getComponentType();

        if (playerRefType == null || playerType == null) {
            // Component types not ready yet — return a permissive query to avoid NPE during registration.
            // onEntityAdded will return early if required components are missing.
            return Query.any();
        }

        // The Hytale unpacked API commonly casts ComponentType -> Query for simple AND queries.
        @SuppressWarnings("unchecked")
        Query<EntityStore> q = (Query<EntityStore>) Query.and(new Query[] {
                (Query) playerRefType,
                (Query) playerType
        });
        return q;
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Acquire components via commandBuffer (pattern used by the engine)
        Player playerComponent = (Player) commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) return;

        PlayerRef playerRefComponent = (PlayerRef) commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRefComponent == null) return;

        HudManager hudManager = playerComponent.getHudManager();
        if (hudManager == null) return; // defensive

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

    // Required by RefSystem: implement removal hook (even if empty)
    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // No cleanup required at the moment. If you need to remove/cleanup per-player data when they leave,
        // implement it here.
    }

    // Stub stat getters - replace these with your plugin's real player stat accessors.
    // These reference the Player parameter to avoid "parameter never used" warnings.
    private String getMoney(Player p) { if (p == null) return "0"; return "0"; }
    private String getShards(Player p) { if (p == null) return "0"; return "0"; }
    private String getKills(Player p) { if (p == null) return "0"; return "0"; }
    private String getPlaytime(Player p) { if (p == null) return "0m"; return "0m"; }
}