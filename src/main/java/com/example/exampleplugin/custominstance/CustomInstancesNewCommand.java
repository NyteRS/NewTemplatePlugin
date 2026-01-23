package com.example.exampleplugin.custominstance;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

/**
 * Create an empty instance template (instance.bson) inside an asset pack (default -> base pack).
 */
public class CustomInstancesNewCommand extends AbstractAsyncCommand {
    private final RequiredArg<String> instanceNameArg;
    private final OptionalArg<String> packName;
    private final OptionalArg<Boolean> forceArg;

    public CustomInstancesNewCommand() {
        super("custominstances", "Create an instance without immutable check");
        this.instanceNameArg = this.withRequiredArg("instanceName", "Instance name", ArgTypes.STRING);
        this.packName = this.withOptionalArg("pack", "Asset pack", ArgTypes.STRING);
        this.forceArg = this.withOptionalArg("force", "Overwrite if exists", ArgTypes.BOOLEAN);
    }

    @Nonnull
    public CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        String name = (String) this.instanceNameArg.get(context);
        boolean force = Boolean.TRUE.equals(this.forceArg.get(context));
        String packId = (String) this.packName.get(context);

        AssetPack pack;
        if (packId != null) {
            pack = AssetModule.get().getAssetPack(packId);
            if (pack == null) {
                context.sendMessage(Message.raw("Unknown asset pack: " + packId));
                return CompletableFuture.completedFuture(null);
            }
        } else {
            pack = AssetModule.get().getBaseAssetPack();
        }

        Path path = pack.getRoot().resolve("Server").resolve("Instances").resolve(name);
        Path instanceFile = path.resolve("instance.bson");

        if (Files.exists(instanceFile, new LinkOption[0]) && !force) {
            context.sendMessage(Message.raw("Instance already exists. Use --force true to overwrite."));
            return CompletableFuture.completedFuture(null);
        } else {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                context.sendMessage(Message.raw("Failed to create directory: " + e.getMessage()));
                return CompletableFuture.completedFuture(null);
            }

            WorldConfig defaultConfig = new WorldConfig();
            return WorldConfig.save(path.resolve("instance.bson"), defaultConfig).thenRun(() -> {
                context.sendMessage(Message.raw("Instance created: " + name));
            });
        }
    }
}