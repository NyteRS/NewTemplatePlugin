package com.example.exampleplugin.custominstance;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

/**
 * Copy an instance template between asset packs (or within the same pack).
 * Behavior mirrors the decompiled plugin: verifies instance.bson exists at source,
 * optionally deletes destination, copies recursively using provider-safe streams.
 */
public class CustomInstancesCopyCommand extends AbstractAsyncCommand {
    private final RequiredArg<String> sourceInstanceArg;
    private final RequiredArg<String> newInstanceNameArg;
    private final OptionalArg<String> fromPackArg;
    private final OptionalArg<String> toPackArg;
    private final OptionalArg<Boolean> forceArg;

    public CustomInstancesCopyCommand() {
        super("custominstancescopy", "Copy an instance between packs");
        this.sourceInstanceArg = this.withRequiredArg("instanceToCopy", "Instance to copy", ArgTypes.STRING);
        this.newInstanceNameArg = this.withRequiredArg("newInstanceName", "New instance name", ArgTypes.STRING);
        this.fromPackArg = this.withOptionalArg("fromPack", "Source asset pack", ArgTypes.STRING);
        this.toPackArg = this.withOptionalArg("toPack", "Destination asset pack", ArgTypes.STRING);
        this.forceArg = this.withOptionalArg("force", "Overwrite destination if exists", ArgTypes.BOOLEAN);
    }

    @Nonnull
    public CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        try {
            String sourceName = (String) this.sourceInstanceArg.get(context);
            String destName = (String) this.newInstanceNameArg.get(context);
            boolean force = Boolean.TRUE.equals(this.forceArg.get(context));

            AssetPack fromPack = this.resolvePack(context, (String) this.fromPackArg.get(context), true);
            if (fromPack == null) {
                return CompletableFuture.completedFuture(null);
            }

            AssetPack toPack = this.resolvePack(context, (String) this.toPackArg.get(context), true);
            if (toPack == null) {
                return CompletableFuture.completedFuture(null);
            }

            if (toPack.isImmutable()) {
                context.sendMessage(Message.raw("Destination pack is read-only (immutable). Use --toPack with a writable pack."));
                return CompletableFuture.completedFuture(null);
            }

            Path srcDir = this.instanceDir(fromPack, sourceName);
            Path dstDir = this.instanceDir(toPack, destName);

            context.sendMessage(Message.raw("Copying..."));
            context.sendMessage(Message.raw("Source: " + String.valueOf(srcDir)));
            context.sendMessage(Message.raw("Dest:   " + String.valueOf(dstDir)));

            if (Files.exists(srcDir, new LinkOption[0]) && Files.isDirectory(srcDir, new LinkOption[0])) {
                Path srcInstanceFile = srcDir.resolve("instance.bson");
                if (!Files.exists(srcInstanceFile, new LinkOption[0])) {
                    context.sendMessage(Message.raw("Source has no instance.bson: " + String.valueOf(srcInstanceFile)));
                    return CompletableFuture.completedFuture(null);
                }

                if (Files.exists(dstDir, new LinkOption[0])) {
                    if (!force) {
                        context.sendMessage(Message.raw("Destination already exists: " + destName + " (use --force true)"));
                        return CompletableFuture.completedFuture(null);
                    }
                    this.deleteRecursively(dstDir);
                }

                Files.createDirectories(dstDir);
                this.copyRecursivelyProviderSafe(srcDir, dstDir);

                String fromLabel = this.packLabel((String) this.fromPackArg.get(context), fromPack);
                String toLabel = this.packLabel((String) this.toPackArg.get(context), toPack);
                context.sendMessage(Message.raw("Instance copied: " + sourceName + " -> " + destName + " | from=" + fromLabel + " | to=" + toLabel));
                return CompletableFuture.completedFuture(null);
            } else {
                context.sendMessage(Message.raw("Source instance does not exist: " + sourceName));
                return CompletableFuture.completedFuture(null);
            }
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (msg == null || msg.trim().isEmpty()) msg = "<no message>";
            context.sendMessage(Message.raw("Copy failed: " + t.getClass().getName() + " : " + msg));
            return CompletableFuture.completedFuture(null);
        }
    }

    private String packLabel(String providedId, AssetPack pack) {
        if (providedId != null && !providedId.isBlank()) {
            return providedId;
        } else {
            Path root = pack.getRoot();
            Path fileName = root.getFileName();
            return fileName != null ? fileName.toString() : root.toString();
        }
    }

    private AssetPack resolvePack(CommandContext context, String packId, boolean fallbackToBase) {
        if (packId != null) {
            AssetPack pack = AssetModule.get().getAssetPack(packId);
            if (pack == null) {
                context.sendMessage(Message.raw("Unknown asset pack: " + packId));
                return null;
            } else {
                return pack;
            }
        } else {
            return fallbackToBase ? AssetModule.get().getBaseAssetPack() : null;
        }
    }

    private Path instanceDir(AssetPack pack, String instanceName) {
        return pack.getRoot().resolve("Server").resolve("Instances").resolve(instanceName);
    }

    private void copyRecursivelyProviderSafe(Path sourceRoot, final Path targetRoot) throws IOException {
        final String sourceRootStr = this.normalizeRootString(sourceRoot.toString());
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String rel = CustomInstancesCopyCommand.this.relativeString(sourceRootStr, dir.toString());
                Path dstDir = targetRoot.resolve(rel);
                Files.createDirectories(dstDir);
                return FileVisitResult.CONTINUE;
            }

            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = CustomInstancesCopyCommand.this.relativeString(sourceRootStr, file.toString());
                Path dstFile = targetRoot.resolve(rel);
                Files.createDirectories(dstFile.getParent());
                InputStream in = Files.newInputStream(file);
                try {
                    Files.copy(in, dstFile, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
                } finally {
                    if (in != null) in.close();
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private String normalizeRootString(String s) {
        String x = s.replace('\\', '/');
        if (!x.endsWith("/")) {
            x = x + "/";
        }
        return x;
    }

    private String relativeString(String rootWithSlash, String fullPath) {
        String p = fullPath.replace('\\', '/');
        if (p.startsWith(rootWithSlash)) {
            return p.substring(rootWithSlash.length());
        } else {
            String rootNoSlash = rootWithSlash.endsWith("/") ? rootWithSlash.substring(0, rootWithSlash.length() - 1) : rootWithSlash;
            if (p.startsWith(rootNoSlash)) {
                String cut = p.substring(rootNoSlash.length());
                if (cut.startsWith("/")) cut = cut.substring(1);
                return cut;
            } else {
                int idx = p.lastIndexOf('/');
                return idx >= 0 ? p.substring(idx + 1) : p;
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.exists(path, new LinkOption[0])) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}