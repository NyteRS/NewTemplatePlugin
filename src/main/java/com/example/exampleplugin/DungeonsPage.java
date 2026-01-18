package com.example.exampleplugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * UI page using the whitelist-style append-of-entry-template approach.
 */
public class DungeonsPage extends InteractiveCustomUIPage<DungeonsPage.DungeonsEventData> {

    public static class DungeonsEventData {
        public String action;
        public String id;

        public static final BuilderCodec<DungeonsEventData> CODEC =
                ((BuilderCodec.Builder<DungeonsEventData>) ((BuilderCodec.Builder<DungeonsEventData>)
                        BuilderCodec.builder(DungeonsEventData.class, DungeonsEventData::new)
                                .append(new KeyedCodec<>("Action", Codec.STRING), (DungeonsEventData o, String v) -> o.action = v, (DungeonsEventData o) -> o.action)
                                .add())
                        .append(new KeyedCodec<>("Id", Codec.STRING), (DungeonsEventData o, String v) -> o.id = v, (DungeonsEventData o) -> o.id)
                        .add())
                        .build();
    }

    private final PlayerRef playerRef;

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
        // Load the base layout (the server resolves Pages/... from Common/UI/Custom/Pages)
        commandBuilder.append("Pages/DungeonsPage.ui");

        // Populate header
        commandBuilder.set("#HeaderTitle.Text", "Dungeons");

        // Build the list using a separate entry template (Pages/DungeonsEntry.ui)
        buildDungeonList(commandBuilder, eventBuilder, DungeonManager.all());

        // Bind static buttons
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                new EventData().append("Action", "Close")
        );
    }

    private void buildDungeonList(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull List<DungeonDescriptor> dungeons) {
        commandBuilder.clear("#DungeonList");

        if (dungeons.isEmpty()) {
            commandBuilder.appendInline("#DungeonList", "Label { Text: \"No dungeons available\"; Anchor: (Height: 40); Style: (FontSize: 14, TextColor: #6e7da1, HorizontalAlignment: Center, VerticalAlignment: Center); }");
            return;
        }

        int i = 0;
        for (DungeonDescriptor d : dungeons) {
            String selector = "#DungeonList[" + i + "]";

            // Append the entry template (exactly like the whitelist does)
            commandBuilder.append("#DungeonList", "Pages/DungeonsEntry.ui");

            // Set fields in the appended entry
            commandBuilder.set(selector + " #DungeonName.Text", d.getName());
            commandBuilder.set(selector + " #DungeonSummary.Text", d.getShortDescription());

            // Bind the select button for this entry (do not auto-close, so false)
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
                        // TODO: instance creation / teleport / party checks
                        refreshPage(ref, store);
                    } else {
                        playerRef.sendMessage(Message.raw("Dungeon not found: " + data.id));
                    }
                }
                break;

            case "Close":
                player.getPageManager().setPage(ref, store, Page.None);
                break;

            default:
                break;
        }
    }

    private void refreshPage(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        buildDungeonList(commandBuilder, eventBuilder, DungeonManager.all());
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }
}