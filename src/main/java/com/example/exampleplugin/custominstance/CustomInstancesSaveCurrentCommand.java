package com.example.exampleplugin.custominstance;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Save the currently-loaded world into an instance template directory.
 * Mirrors decompiled plugin behavior: attempts to flush/save world, finds world directory (via well-known methods or reflection),
 * and copies files excluding instance.bson (keeps or overwrites based on --force).
 */
public class CustomInstancesSaveCurrentCommand extends AbstractPlayerCommand {
    private final OptionalArg<String> instanceNameArg;
    private final OptionalArg<String> toPackArg;
    private final OptionalArg<Boolean> forceArg;

    public CustomInstancesSaveCurrentCommand() {
        super("custominstancessave", "Save current instance changes into an instance template (root overwrite)");
        this.instanceNameArg = this.withOptionalArg("name", "Destination instance name (template folder name)", ArgTypes.STRING);
        this.toPackArg = this.withOptionalArg("toPack", "Destination asset pack", ArgTypes.STRING);
        this.forceArg = this.withOptionalArg("force", "Overwrite destination instance content (keeps instance.bson)", ArgTypes.BOOLEAN);
    }

    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        try {
            boolean force = Boolean.TRUE.equals(this.forceArg.get(context));
            String destInstanceName = (String) this.instanceNameArg.get(context);
            String toPackId = (String) this.toPackArg.get(context);

            AssetPack toPack;
            if (toPackId != null) {
                toPack = AssetModule.get().getAssetPack(toPackId);
                if (toPack == null) {
                    context.sendMessage(Message.raw("Unknown asset pack: " + toPackId));
                    return;
                }
            } else {
                toPack = AssetModule.get().getBaseAssetPack();
            }

            if (toPack.isImmutable()) {
                context.sendMessage(Message.raw("Destination pack is read-only. Use --toPack with a writable pack."));
                return;
            }

            if (destInstanceName == null || destInstanceName.isBlank()) {
                context.sendMessage(Message.raw("You must provide --name <InstanceTemplateName>"));
                context.sendMessage(Message.raw("Example: /custominstancesave --toPack Group:PackName --name MyInstanceName --force true"));
                return;
            }

            Path instanceDir = toPack.getRoot().resolve("Server").resolve("Instances").resolve(destInstanceName);
            Path instanceConfig = instanceDir.resolve("instance.bson");
            if (!Files.exists(instanceConfig, new LinkOption[0])) {
                context.sendMessage(Message.raw("Destination instance template not found (missing instance.bson): " + String.valueOf(instanceConfig)));
                context.sendMessage(Message.raw("Create/copy the instance first, then save into it."));
                return;
            }

            context.sendMessage(Message.raw("Attempting to flush world data to disk..."));
            this.attemptFlush(context, world);

            Path sourceWorldDir = this.tryFindWorldDirectory(world);
            context.sendMessage(Message.raw("Detected source world dir: " + (sourceWorldDir == null ? "<null>" : sourceWorldDir.toString())));
            context.sendMessage(Message.raw("Destination instance dir: " + String.valueOf(instanceDir)));

            if (sourceWorldDir == null) {
                context.sendMessage(Message.raw("Cannot find current world directory on disk (API differs)."));
                return;
            }

            if (!Files.exists(sourceWorldDir, new LinkOption[0]) || !Files.isDirectory(sourceWorldDir, new LinkOption[0])) {
                context.sendMessage(Message.raw("Source world dir does not exist / not a directory: " + String.valueOf(sourceWorldDir)));
                return;
            }

            long sourceFiles = this.countFiles(sourceWorldDir);
            context.sendMessage(Message.raw("Source file count: " + sourceFiles));
            if (sourceFiles == 0L) {
                context.sendMessage(Message.raw("Source folder is empty -> nothing to save."));
                return;
            }

            if (force) {
                this.deleteInstanceContentButKeepConfig(instanceDir, instanceConfig);
            }

            context.sendMessage(Message.raw("Copying world data into instance root..."));
            long copied = this.copyRecursivelyProviderSafeExcludeInstanceBson(sourceWorldDir, instanceDir, instanceConfig.getFileName().toString(), true);
            context.sendMessage(Message.raw("Copied files: " + copied));
            if (copied == 0L) {
                context.sendMessage(Message.raw("Copied 0 files -> nothing changed."));
                return;
            }

            context.sendMessage(Message.raw("Saved! Rejoin/recreate the instance: your edits should persist now."));
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (msg == null || msg.trim().isEmpty()) msg = "<no message>";
            context.sendMessage(Message.raw("Save failed: " + t.getClass().getName() + " : " + msg));
        }
    }

    private void attemptFlush(CommandContext context, World world) {
        String[] methods = new String[]{"save", "flush", "saveAll", "saveNow", "commit", "writeToDisk"};
        for (String m : methods) {
            if (this.invokeNoArg(world, m)) {
                context.sendMessage(Message.raw("Flush invoked: world." + m + "()"));
                return;
            }
        }

        Object storage = this.getNoArg(world, "getStorage");
        if (storage != null) {
            for (String m : methods) {
                if (this.invokeNoArg(storage, m)) {
                    context.sendMessage(Message.raw("Flush invoked: world.getStorage()." + m + "()"));
                    return;
                }
            }
        }

        Object store = this.getNoArg(world, "getStore");
        if (store != null) {
            for (String m : methods) {
                if (this.invokeNoArg(store, m)) {
                    context.sendMessage(Message.raw("Flush invoked: world.getStore()." + m + "()"));
                    return;
                }
            }
        }

        context.sendMessage(Message.raw("No flush method found (ok)."));
    }

    private Path tryFindWorldDirectory(World world) {
        String[] methods = new String[]{"getSavePath", "getWorldPath", "getPath", "getDirectory", "getRoot", "getRootPath", "getStoragePath", "getWorldDirectory", "getFolder"};
        for (String m : methods) {
            Object v = this.getNoArg(world, m);
            Path p = this.toPath(v);
            if (p != null) return p;
        }

        Object storage = this.getNoArg(world, "getStorage");
        if (storage != null) {
            for (String m : methods) {
                Object v = this.getNoArg(storage, m);
                Path p = this.toPath(v);
                if (p != null) return p;
            }
        }

        Object store = this.getNoArg(world, "getStore");
        if (store != null) {
            for (String m : methods) {
                Object v = this.getNoArg(store, m);
                Path p = this.toPath(v);
                if (p != null) return p;
            }
        }

        return null;
    }

    private long countFiles(Path dir) throws IOException {
        final long[] count = new long[]{0L};
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                count[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }

    private Object getNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(methodName);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean invokeNoArg(Object target, String methodName) {
        if (target == null) return false;
        try {
            Method m = target.getClass().getMethod(methodName);
            m.setAccessible(true);
            m.invoke(target);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private Path toPath(Object v) {
        if (v == null) return null;
        if (v instanceof Path) return (Path) v;
        if (v instanceof String) {
            String s = (String) v;
            if (!s.isBlank()) {
                try {
                    return Paths.get(s);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private void deleteInstanceContentButKeepConfig(final Path instanceDir, final Path instanceConfig) throws IOException {
        if (!Files.exists(instanceDir)) return;
        Files.walkFileTree(instanceDir, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.equals(instanceConfig)) return FileVisitResult.CONTINUE;
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (dir.equals(instanceDir)) return FileVisitResult.CONTINUE;
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    if (!stream.iterator().hasNext()) {
                        Files.deleteIfExists(dir);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private long copyRecursivelyProviderSafeExcludeInstanceBson(Path sourceRoot, final Path targetRoot, final String excludedFileName, final boolean overwrite) throws IOException {
        final String sourceRootStr = this.normalizeRootString(sourceRoot.toString());
        final long[] copied = new long[]{0L};
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String rel = CustomInstancesSaveCurrentCommand.this.relativeString(sourceRootStr, dir.toString());
                Path dstDir = targetRoot.resolve(rel);
                Files.createDirectories(dstDir);
                return FileVisitResult.CONTINUE;
            }

            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = CustomInstancesSaveCurrentCommand.this.relativeString(sourceRootStr, file.toString());
                Path dstFile = targetRoot.resolve(rel);
                if (dstFile.getFileName() != null && excludedFileName.equals(dstFile.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                } else {
                    Files.createDirectories(dstFile.getParent());
                    InputStream in = Files.newInputStream(file);
                    try {
                        if (overwrite) {
                            Files.copy(in, dstFile, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
                        } else if (!Files.exists(dstFile, new LinkOption[0])) {
                            Files.copy(in, dstFile);
                        }
                    } finally {
                        if (in != null) in.close();
                    }
                    copied[0]++;
                    return FileVisitResult.CONTINUE;
                }
            }
        });
        return copied[0];
    }

    private String normalizeRootString(String s) {
        String x = s.replace('\\', '/');
        if (!x.endsWith("/")) x = x + "/";
        return x;
    }

    private String relativeString(String rootWithSlash, String fullPath) {
        String p = fullPath.replace('\\', '/');
        if (p.startsWith(rootWithSlash)) return p.substring(rootWithSlash.length());
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