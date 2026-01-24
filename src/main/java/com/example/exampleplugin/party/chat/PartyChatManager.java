package com.example.exampleplugin.party.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PartyChatManager {
   private static final PartyChatManager INSTANCE = new PartyChatManager();
   private final Map<UUID, Boolean> partyChatEnabled = new ConcurrentHashMap();

   private PartyChatManager() {
   }

   public static PartyChatManager getInstance() {
      return INSTANCE;
   }

   public boolean isPartyChatEnabled(UUID playerId) {
      return (Boolean)this.partyChatEnabled.getOrDefault(playerId, false);
   }

   public void setPartyChatEnabled(UUID playerId, boolean enabled) {
      if (enabled) {
         this.partyChatEnabled.put(playerId, true);
      } else {
         this.partyChatEnabled.remove(playerId);
      }

   }

   public boolean togglePartyChat(UUID playerId) {
      boolean newState = !this.isPartyChatEnabled(playerId);
      this.setPartyChatEnabled(playerId, newState);
      return newState;
   }

   public void removePlayer(UUID playerId) {
      this.partyChatEnabled.remove(playerId);
   }
}
