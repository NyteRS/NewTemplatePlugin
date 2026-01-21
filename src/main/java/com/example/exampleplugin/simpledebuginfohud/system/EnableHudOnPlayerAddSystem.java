package com.example.exampleplugin.simpledebuginfohud.system;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.example.exampleplugin.simpledebuginfohud.data.DebugManager;

import javax.annotation.Nonnull;

/**
 * EnableHudOnPlayerAddSystem
 *
 * Runs when a Player + PlayerRef entity is added to a world. This is the same timing the engine uses
 * to send the default visible HUD components (see PlayerHudManagerSystems.InitializeSystem).
 *
 * Enabling the HUD here ensures the client's packet handler and the stock HUD initialization have been
 * set up, avoiding the "enable too early -> kicked" issue.
 */
public class EnableHudOnPlayerAddSystem extends RefSystem<EntityStore> {
    private static final ComponentType<EntityStore, PlayerRef> PLAYER_REF_COMPONENT_TYPE = PlayerRef.getComponentType();
    private static final ComponentType<EntityStore, Player> PLAYER_COMPONENT_TYPE = Player.getComponentType();
    private static final Query<EntityStore> QUERY = (Query<EntityStore>) Query.and(new Query[] { (Query) PLAYER_REF_COMPONENT_TYPE, (Query) PLAYER_COMPONENT_TYPE });

    private final DebugManager debugManager;

    public EnableHudOnPlayerAddSystem(DebugManager debugManager) {
        this.debugManager = debugManager;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Use commandBuffer.getComponent(...) here because this runs during entity addition.
        Player playerComponent = (Player) commandBuffer.getComponent(ref, PLAYER_COMPONENT_TYPE);
        if (playerComponent == null) return;

        PlayerRef playerRefComponent = (PlayerRef) commandBuffer.getComponent(ref, PLAYER_REF_COMPONENT_TYPE);
        if (playerRefComponent == null) return;

        // Best effort checks: HudManager & PacketHandler should be available here in the core flow.
        try {
            HudManager hudManager = playerComponent.getHudManager();
            PacketHandler packetHandler = playerRefComponent.getPacketHandler();

            if (hudManager != null && packetHandler != null) {
                // Enable the debug/scoreboard HUD for this entity ref. This mirrors engine timing.
                // debugManager.setDebugEnabled will be read by your DebugHudSystem and cause the HUD to show.
                this.debugManager.setDebugEnabled(ref, true);
            }
        } catch (Throwable ignored) {
            // swallow — don't break entity initialization if something odd happens
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull com.hypixel.hytale.component.RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // nothing special to do on remove
    }
}