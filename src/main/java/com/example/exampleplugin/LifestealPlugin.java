package com.example.exampleplugin;

import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class LifestealPlugin extends JavaPlugin {
    public static final PluginManifest MANIFEST = PluginManifest.corePlugin(LifestealPlugin.class).build();

    private static LifestealPlugin instance;

    public static LifestealPlugin get() {
        return instance;
    }

    public LifestealPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        ComponentRegistryProxy<EntityStore> entityStoreRegistry = getEntityStoreRegistry();
        entityStoreRegistry.registerSystem(new LifestealSystems.LifestealOnDamage());
    }
}
