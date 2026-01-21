package com.example.exampleplugin.simpledebuginfohud.hud;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Map;
import com.example.exampleplugin.simpledebuginfohud.data.DebugManager;

public class DebugHudSystem extends EntityTickingSystem<EntityStore> {
    private final DebugManager debugManager;
    private final Query<EntityStore> query;
    private final Map<PlayerRef, DebugHud> huds = new HashMap();

    public DebugHudSystem(DebugManager debugManager) {
        this.debugManager = debugManager;
        this.query = Query.and(new Query[]{Player.getComponentType()});
    }

    public Query<EntityStore> getQuery() {
        return this.query;
    }

    public void tick(float deltaTime, int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        try {
            Holder<EntityStore> holder = EntityUtils.toHolder(entityIndex, chunk);
            Player player = (Player)holder.getComponent(Player.getComponentType());
            PlayerRef playerRef = (PlayerRef)holder.getComponent(PlayerRef.getComponentType());
            if (player == null || playerRef == null) {
                return;
            }

            Ref<EntityStore> ref = chunk.getReferenceTo(entityIndex);
            DebugHud hud;
            if (!this.huds.containsKey(playerRef)) {
                hud = new DebugHud(playerRef, this.debugManager, ref);
                this.huds.put(playerRef, hud);
                player.getHudManager().setCustomHud(playerRef, hud);
            } else {
                hud = (DebugHud)this.huds.get(playerRef);
            }

            TransformComponent transform = (TransformComponent)holder.getComponent(TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d pos = transform.getPosition();
                int x = MathUtil.floor(pos.getX());
                int y = MathUtil.floor(pos.getY());
                int z = MathUtil.floor(pos.getZ());
                hud.setPosition(x, y, z);
            }

            try {
                WorldMapTracker tracker = player.getWorldMapTracker();
                if (tracker != null) {
                    String currentBiome = tracker.getCurrentBiomeName();
                    if (currentBiome != null && !currentBiome.isEmpty()) {
                        hud.setBiomeName(this.formatBiomeName(currentBiome));
                    }
                }
            } catch (Exception var20) {
            }

            try {
                EntityStatMap stats = (EntityStatMap)store.getComponent(ref, EntityStatMap.getComponentType());
                if (stats != null) {
                    int healthIndex = EntityStatType.getAssetMap().getIndex("Health");
                    EntityStatValue healthStat = stats.get(healthIndex);
                    if (healthStat != null) {
                        hud.setHealth(healthStat.get(), healthStat.getMax());
                    }

                    int staminaIndex = EntityStatType.getAssetMap().getIndex("Stamina");
                    EntityStatValue staminaStat = stats.get(staminaIndex);
                    if (staminaStat != null) {
                        hud.setStamina(staminaStat.get(), staminaStat.getMax());
                    }

                    int manaIndex = EntityStatType.getAssetMap().getIndex("Mana");
                    EntityStatValue manaStat = stats.get(manaIndex);
                    if (manaStat != null) {
                        hud.setMana(manaStat.get(), manaStat.getMax());
                    }
                }
            } catch (Exception var19) {
            }

            hud.show();
        } catch (Exception var21) {
        }

    }

    private String formatBiomeName(String internalName) {
        if (internalName == null) {
            return "Unknown";
        } else {
            String name = internalName.replace("zone_1_", "").replace("zone_2_", "").replace("zone_3_", "").replace("zone_4_", "").replace("Zone1_", "").replace("Zone2_", "").replace("Zone3_", "").replace("Zone4_", "").replace("biome_", "").replace("_biome", "");
            StringBuilder result = new StringBuilder();
            String[] words = name.split("_");

            for(String word : words) {
                if (!word.isEmpty()) {
                    result.append(Character.toUpperCase(word.charAt(0)));
                    if (word.length() > 1) {
                        result.append(word.substring(1).toLowerCase());
                    }

                    result.append(" ");
                }
            }

            return result.toString().trim();
        }
    }
}