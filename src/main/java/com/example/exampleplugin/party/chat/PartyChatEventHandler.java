package com.example.exampleplugin.party.chat;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent.Formatter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.example.exampleplugin.party.gui.PartyBrowserGui;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;

public class PartyChatEventHandler {
   private static final Formatter PARTY_CHAT_FORMATTER = (playerRef, msg) -> {
      return Message.join(new Message[]{Message.raw("[Party] ").color(Color.CYAN), Message.raw(playerRef.getUsername()).color(Color.GREEN), Message.raw(": ").color(Color.GRAY), Message.raw(msg).color(Color.WHITE)});
   };

   public static void handleChatEvent(PlayerChatEvent event) {
      UUID senderId = event.getSender().getUuid();
      if (PartyChatManager.getInstance().isPartyChatEnabled(senderId)) {
         PartyManager partyManager = PartyManager.getInstance();
         PartyInfo senderParty = partyManager.getPartyFromPlayer(senderId);
         if (senderParty == null) {
            PartyChatManager.getInstance().setPartyChatEnabled(senderId, false);
         } else {
            List<PlayerRef> partyTargets = new ArrayList();
            UUID[] allMembers = senderParty.getAllPartyMembers();
            UUID[] var6 = allMembers;
            int var7 = allMembers.length;

            for(int var8 = 0; var8 < var7; ++var8) {
               UUID memberId = var6[var8];
               PlayerRef memberRef = Universe.get().getPlayer(memberId);
               if (memberRef != null && memberRef.isValid()) {
                  partyTargets.add(memberRef);
               }
            }

            event.setTargets(partyTargets);
            event.setFormatter(PARTY_CHAT_FORMATTER);
            String senderName = partyManager.getPlayerNameTracker().getPlayerName(senderId);
            PartyBrowserGui.ChatMessage chatMsg = new PartyBrowserGui.ChatMessage(senderId, senderName, event.getContent());
            PartyBrowserGui.addChatMessage(senderParty.getId(), chatMsg);
         }
      }
   }
}
