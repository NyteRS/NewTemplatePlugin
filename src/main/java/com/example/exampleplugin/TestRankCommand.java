package com.example.exampleplugin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.UUIDComponent;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * /test - prints "Your rank = <primaryGroup>" using LuckPerms
 */
public class TestRankCommand extends AbstractPlayerCommand {
    public TestRankCommand() {
        super("test", "Print your LuckPerms primary group.");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        UUIDComponent uuidComp = (UUIDComponent) store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) {
            context.sendMessage(Message.raw("Could not determine your UUID."));
            return;
        }

        UUID uuid = uuidComp.getUuid();

        try {
            LuckPerms lp = LuckPermsProvider.get();
            if (lp == null) {
                context.sendMessage(Message.raw("LuckPerms provider not available."));
                return;
            }

            User user = lp.getUserManager().getUser(uuid);
            if (user != null) {
                String primary = user.getPrimaryGroup();
                context.sendMessage(Message.raw("Your rank = " + primary));
                return;
            }
            context.sendMessage(Message.raw("Loading rank..."));

            lp.getUserManager().loadUser(uuid).thenAccept(loadedUser -> {
                if (loadedUser == null) {
                    playerRef.sendMessage(Message.raw("Could not load LuckPerms user data."));
                    return;
                }
                String primary = loadedUser.getPrimaryGroup();
                playerRef.sendMessage(Message.raw("Your rank = " + primary));
            }).exceptionally(ex -> {
                playerRef.sendMessage(Message.raw("Failed to load LuckPerms user data."));
                return null;
            });

        } catch (Throwable t) {
            context.sendMessage(Message.raw("Error while checking LuckPerms: " + t.getClass().getSimpleName()));
        }
    }
}