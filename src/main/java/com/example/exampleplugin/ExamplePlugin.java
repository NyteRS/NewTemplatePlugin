package com.example.exampleplugin;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class ExamplePlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public ExamplePlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from %s version %s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        ComponentRegistryProxy<EntityStore> registry = getEntityStoreRegistry();
        registry.registerSystem(new LifestealSystems.LifestealOnDamage());
        this.getCommandRegistry().registerCommand(new DungeonUICommand());
        this.getEntityStoreRegistry().registerSystem(new AutoScoreboardSystem());
        this.getCommandRegistry().registerCommand(new ScoreboardCommand());
        // Register any other systems here
        this.getCommandRegistry().registerCommand(new ExampleCommand(this.getName(), this.getManifest().getVersion().toString()));
        this.getEntityStoreRegistry().registerSystem(new com.example.exampleplugin.BleedSystems.BleedOnDamage());
        this.getEntityStoreRegistry().registerSystem(new com.example.exampleplugin.BleedSystems.BleedTicking());
    }
}
