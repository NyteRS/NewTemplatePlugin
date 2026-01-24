package com.example.exampleplugin.party.party;

import java.util.UUID;

public record PartyInvite(UUID recipient, UUID sender, UUID partyId, long createdAt) {
   private static final long INVITE_DURATION = 60000L;

   public PartyInvite(UUID recipient, UUID sender, UUID partyId) {
      this(recipient, sender, partyId, System.currentTimeMillis());
   }

   public PartyInvite(UUID recipient, UUID sender, UUID partyId, long createdAt) {
      this.recipient = recipient;
      this.sender = sender;
      this.partyId = partyId;
      this.createdAt = createdAt;
   }

   public boolean isExpired() {
      return System.currentTimeMillis() - this.createdAt > 60000L;
   }

   public long getTimeRemaining() {
      long elapsed = System.currentTimeMillis() - this.createdAt;
      return Math.max(0L, 60000L - elapsed);
   }

   public UUID recipient() {
      return this.recipient;
   }

   public UUID sender() {
      return this.sender;
   }

   public UUID partyId() {
      return this.partyId;
   }

   public long createdAt() {
      return this.createdAt;
   }
}
