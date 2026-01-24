package com.example.exampleplugin.custominstance;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * /instances list
 *
 * Lists instance templates present in the configured asset packs (Server/Instances/*).
 * This mirrors how custominstance tools find instance folders.
 */
public class ListInstancesCommand extends AbstractCommandCollection {
    public ListInstancesCommand() {
        super("instances", "Instance template utilities");
        addSubCommand(new ListCommand());
    }

    private static class ListCommand extends CommandBase {
        public ListCommand() {
            super("list", "List available instance templates (scans asset packs Server/Instances)");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            Set<String> names = new HashSet<>();
            try {
                for (AssetPack pack : AssetModule.get().getAssetPacks()) {
                    if (pack == null) continue;
                    Path instancesRoot = pack.getRoot().resolve("Server").resolve("Instances");
                    if (!Files.exists(instancesRoot, new LinkOption[0]) || !Files.isDirectory(instancesRoot, new LinkOption[0])) {
                        continue;
                    }
                    try {
                        Files.list(instancesRoot).forEach(p -> {
                            try {
                                if (Files.isDirectory(p, new LinkOption[0])) {
                                    names.add(p.getFileName().toString());
                                }
                            } catch (Throwable ignored) {}
                        });
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                context.sendMessage(Message.raw("Failed to list instance packs: " + t.getMessage()));
                return;
            }

            if (names.isEmpty()) {
                context.sendMessage(Message.raw("No instance templates found in asset packs."));
                return;
            }

            context.sendMessage(Message.raw("Instance templates:"));
            names.stream().sorted().forEach(n -> context.sendMessage(Message.raw(" - " + n)));
        }
    }
}