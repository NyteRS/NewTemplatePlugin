package com.example.exampleplugin.command;

import com.example.exampleplugin.AssetLifestealLoader;
import com.example.exampleplugin.DescriptionMergeInjector;
import com.example.exampleplugin.LifestealRegistry;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * Command to reload lifesteal mappings from assets and update item descriptions.
 */
public class ReloadLifestealCommand extends CommandBase {

    public ReloadLifestealCommand() {
        super("reloadlifesteal", "Reloads lifesteal mappings from assets and updates item descriptions");
        this.setPermissionGroup(GameMode.Creative); // Requires creative/OP permissions
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("§aReloading lifesteal mappings..."));
        
        try {
            // Clear existing registry
            int previousSize = LifestealRegistry.size();
            LifestealRegistry.clear();
            
            // Populate from assets
            AssetLifestealLoader.populateFromAssets();
            
            // Update descriptions
            DescriptionMergeInjector.injectAll();
            
            int newSize = LifestealRegistry.size();
            
            ctx.sendMessage(Message.raw(String.format(
                "§aLifesteal reload complete! Loaded %d items (previously: %d)",
                newSize, previousSize
            )));
            
            if (newSize == 0) {
                ctx.sendMessage(Message.raw("§eNote: No lifesteal items found. Assets may not be loaded yet."));
            }
            
        } catch (Exception e) {
            ctx.sendMessage(Message.raw("§cError reloading lifesteal: " + e.getMessage()));
            e.printStackTrace();
        }
    }
}
