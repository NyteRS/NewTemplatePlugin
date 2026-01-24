package com.example.exampleplugin.party.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec.Builder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.example.exampleplugin.party.config.PartyProConfig;
import com.example.exampleplugin.party.lang.LanguageManager;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyInvite;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.util.NotificationHelper;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class PartyInviteGui extends InteractiveCustomUIPage<PartyInviteGui.GuiData> {
   private final PartyInvite invite;
   private final PartyInfo party;
   private final String senderName;

   public PartyInviteGui(@NonNullDecl PlayerRef playerRef, PartyInvite invite, PartyInfo party) {
      super(playerRef, CustomPageLifetime.CanDismiss, PartyInviteGui.GuiData.CODEC);
      this.invite = invite;
      this.party = party;
      this.senderName = PartyManager.getInstance().getPlayerNameTracker().getPlayerName(invite.sender());
   }

   public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store, @NonNullDecl PartyInviteGui.GuiData data) {
      super.handleDataEvent(ref, store, data);
      PlayerRef playerRef = (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
      if (playerRef != null) {
         if (data.accept != null) {
            PartyInvite result = PartyManager.getInstance().acceptInvite(playerRef);
            if (result != null) {
               NotificationHelper.sendSuccess(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.party_joined", this.party.getName()));
            } else {
               NotificationHelper.sendError(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.invite_expired"));
            }

            this.close();
         } else if (data.decline != null) {
            PartyManager.getInstance().declineInvite(playerRef.getUuid());
            NotificationHelper.sendInfo(playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.invite_declined"));
            this.close();
         } else {
            if (data.close != null) {
               this.close();
            }

         }
      }
   }

   public void build(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl UICommandBuilder cmd, @NonNullDecl UIEventBuilder evt, @NonNullDecl Store<EntityStore> store) {
      cmd.append("Pages/PartyPro/PartyInvite.ui");
      cmd.set("#InviteTitle.Text", LanguageManager.getInstance().get("gui.invite_title"));
      cmd.set("#InviteMessage.Text", LanguageManager.getInstance().get("gui.invite_message", this.senderName));
      cmd.set("#PartyName.Text", this.party.getName());
      cmd.set("#MemberCount.Text", LanguageManager.getInstance().get("gui.members_count", this.party.getTotalMemberCount(), PartyProConfig.getInstance().getMaxPartySize()));
      long remaining = this.invite.getTimeRemaining();
      int seconds = (int)(remaining / 1000L);
      cmd.set("#TimeRemaining.Text", LanguageManager.getInstance().get("gui.expires_in", seconds));
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#AcceptButton", EventData.of("Accept", "true"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#DeclineButton", EventData.of("Decline", "true"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Close", "true"), false);
   }

   public static class GuiData {
      public static final BuilderCodec<PartyInviteGui.GuiData> CODEC;
      private String accept;
      private String decline;
      private String close;

      static {
         CODEC = ((Builder)((Builder)((Builder)BuilderCodec.builder(PartyInviteGui.GuiData.class, PartyInviteGui.GuiData::new).addField(new KeyedCodec("Accept", Codec.STRING), (d, s) -> {
            d.accept = s;
         }, (d) -> {
            return d.accept;
         })).addField(new KeyedCodec("Decline", Codec.STRING), (d, s) -> {
            d.decline = s;
         }, (d) -> {
            return d.decline;
         })).addField(new KeyedCodec("Close", Codec.STRING), (d, s) -> {
            d.close = s;
         }, (d) -> {
            return d.close;
         })).build();
      }
   }
}
