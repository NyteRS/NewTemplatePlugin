package com.example.exampleplugin.dungeon;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;

import javax.annotation.Nonnull;

/**
 * DungeonProtectionSystem
 *
 * Placeholder RefSystem where you can wire event listeners for block-place / block-break prevention.
 * The Hytale server's block event API is not present in this snippet; implement event hooks there
 * and consult getEditingSession(...) to check whether a player is allowed to build.
 *
 * This system demonstrates how to access Player & PlayerRef components on entity add (same timing used for hud).
 */
public class DungeonProtectionSystem extends RefSystem<EntityStore> {
    private final DungeonManager manager;
    private final Query<EntityStore> query;

    public DungeonProtectionSystem(DungeonManager manager) {
        this.manager = manager;
        ComponentType<EntityStore, PlayerRef> pr = PlayerRef.getComponentType();
        ComponentType<EntityStore, Player> p = Player.getComponentType();
        if (pr == null || p == null) {
            this.query = Query.any();
        } else {
            @SuppressWarnings("unchecked")
            Query<EntityStore> q = (Query<EntityStore>) Query.and(new Query[] { (Query) pr, (Query) p });
            this.query = q;
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // This hook runs when player entities are added. Use it to register player->world mapping or to attach listeners.
        Player player = (Player) commandBuffer.getComponent(ref, Player.getComponentType());
        PlayerRef pref = (PlayerRef) commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pref == null) return;

        // Hook point: register to block place/break events for this player. Those event handlers should consult
        // manager.getEditingSession(...) and decide whether to allow the action.
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull com.hypixel.hytale.component.RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // cleanup per-player listeners if necessary
    }
}