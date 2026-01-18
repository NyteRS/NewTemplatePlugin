package com.example.exampleplugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DungeonsPage using preloaded preview Groups in the .ui and toggling Visible.
 * IDs use alphanumeric names (no underscores).
 */
public class DungeonsPage extends InteractiveCustomUIPage<DungeonsPage.DungeonsEventData> {

    public static class DungeonsEventData {
        public String action;
        public String id;

        public static final BuilderCodec<DungeonsEventData> CODEC =
                ((BuilderCodec.Builder<DungeonsEventData>) ((BuilderCodec.Builder<DungeonsEventData>)
                        BuilderCodec.builder(DungeonsEventData.class, DungeonsEventData::new)
                                .append(new KeyedCodec<>("Action", (Codec) Codec.STRING), (DungeonsEventData o, String v) -> o.action = v, (DungeonsEventData o) -> o.action)
                                .add())
                        .append(new KeyedCodec<>("Id", (Codec) Codec.STRING), (DungeonsEventData o, String v) -> o.id = v, (DungeonsEventData o) -> o.id)
                        .add())
                        .build();
    }

    private final PlayerRef playerRef;

    // Map dungeon id -> preview control id (no underscores)
    private static final Map<String, String> PREVIEW_IDS = new HashMap<>();
    static {
        PREVIEW_IDS.put("d1", "PreviewD1");
        PREVIEW_IDS.put("d2", "PreviewD2");
        PREVIEW_IDS.put("d3", "PreviewD3");
        // Add more mappings if you add more preloaded Groups in the .ui
    }

    public DungeonsPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, DungeonsEventData.CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Pages/DungeonsPage.ui");

        commandBuilder.set("#HeaderTitle.Text", "Dungeons");

        buildDungeonList(commandBuilder, eventBuilder, DungeonManager.all());

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                new EventData().append("Action", "Close")
        );
    }

    private void buildDungeonList(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull List<DungeonDescriptor> dungeons) {
        commandBuilder.clear("#DungeonList");
        if (dungeons.isEmpty()) {
            commandBuilder.appendInline("#DungeonList", "Label { Text: \"No dungeons available\"; Anchor: (Height: 40); }");
            return;
        }

        int i = 0;
        for (DungeonDescriptor d : dungeons) {
            String selector = "#DungeonList[" + i + "]";

            commandBuilder.append("#DungeonList", "Pages/DungeonsEntry.ui");

            commandBuilder.set(selector + " #DungeonName.Text", d.getName());
            commandBuilder.set(selector + " #DungeonSummary.Text", d.getShortDescription());

            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector + " #SelectButton",
                    new EventData().append("Action", "Select").append("Id", d.getId()),
                    false
            );

            i++;
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull DungeonsEventData data) {
        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if (data.action == null) return;

        switch (data.action) {
            case "Select":
                if (data.id != null) {
                    DungeonDescriptor d = DungeonManager.getById(data.id);
                    if (d != null) {
                        playerRef.sendMessage(Message.raw("Selected dungeon: " + d.getName()));
                        updatePreviewVisibility(d.getId(), d.getShortDescription());
                    } else {
                        playerRef.sendMessage(Message.raw("Dungeon not found: " + data.id));
                    }
                }
                break;

            case "Close":
                try {
                    player.getPageManager().setPage(ref, store, Page.None);
                } catch (Throwable ignored) { }
                break;

            default:
                break;
        }
    }

    private void updatePreviewVisibility(@Nullable String dungeonId, @Nullable String description) {
        UICommandBuilder cmd = new UICommandBuilder();

        // hide all
        for (String controlId : PREVIEW_IDS.values()) {
            cmd.set("#" + controlId + ".Visible", false);
            cmd.set("#PreviewImageArea #" + controlId + ".Visible", false);
        }

        // show mapped (if any)
        if (dungeonId != null) {
            String controlId = PREVIEW_IDS.get(dungeonId);
            if (controlId != null) {
                cmd.set("#" + controlId + ".Visible", true);
                cmd.set("#PreviewImageArea #" + controlId + ".Visible", true);
            }
        }

        cmd.set("#PreviewDescription.Text", description != null ? description : "");
        this.sendUpdate(cmd);
    }
}