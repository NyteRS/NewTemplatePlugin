package com.example.exampleplugin.party.ping;

import com.hypixel.hytale.math.vector.Vector3d;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.util.NotificationHelper;

public class PingManager {
   private static PingManager instance;
   private final Map<UUID, Map<UUID, Ping>> partyPings = new ConcurrentHashMap();

   private PingManager() {
   }

   public static PingManager getInstance() {
      if (instance == null) {
         instance = new PingManager();
      }

      return instance;
   }

   @Nullable
   public Ping createPing(@Nonnull UUID playerUuid, @Nonnull Vector3d position, @Nonnull String worldName) {
      PartyManager partyManager = PartyManager.getInstance();
      PartyInfo party = partyManager.getPartyFromPlayer(playerUuid);
      if (party == null) {
         return null;
      } else {
         String playerName = partyManager.getPlayerNameTracker().getPlayerName(playerUuid);
         if (playerName == null) {
            playerName = "Unknown";
         }

         UUID partyId = party.getId();
         int ownerIndex = this.getPlayerIndexInParty(party, playerUuid);
         Ping ping = new Ping(playerUuid, playerName, partyId, position, worldName, ownerIndex);
         Map<UUID, Ping> playerPings = (Map)this.partyPings.computeIfAbsent(partyId, (k) -> {
            return new ConcurrentHashMap();
         });
         playerPings.put(playerUuid, ping);
         PingBeaconManager.getInstance().startBeacon(ping);
         this.notifyPartyOfPing(party, ping);
         return ping;
      }
   }

   private int getPlayerIndexInParty(PartyInfo party, UUID playerUuid) {
      if (party.getLeader().equals(playerUuid)) {
         return 0;
      } else {
         UUID[] members = party.getMembers();

         for(int i = 0; i < members.length; ++i) {
            if (members[i].equals(playerUuid)) {
               return i + 1;
            }
         }

         return 0;
      }
   }

   public void removePing(@Nonnull UUID partyId, @Nonnull UUID playerUuid) {
      Map<UUID, Ping> playerPings = (Map)this.partyPings.get(partyId);
      if (playerPings != null) {
         playerPings.remove(playerUuid);
      }

      PingBeaconManager.getInstance().stopBeacon(playerUuid);
   }

   @Nonnull
   public Collection<Ping> getPartyPings(@Nonnull UUID partyId) {
      Map<UUID, Ping> playerPings = (Map)this.partyPings.get(partyId);
      return (Collection)(playerPings == null ? Collections.emptyList() : playerPings.values());
   }

   @Nonnull
   public Collection<Ping> getPingsForPlayer(@Nonnull UUID playerUuid) {
      PartyInfo party = PartyManager.getInstance().getPartyFromPlayer(playerUuid);
      return (Collection)(party == null ? Collections.emptyList() : this.getPartyPings(party.getId()));
   }

   public void cleanupExpiredPings() {
      Iterator var1 = this.partyPings.values().iterator();

      while(var1.hasNext()) {
         Map<UUID, Ping> playerPings = (Map)var1.next();
         playerPings.entrySet().removeIf((entry) -> {
            return ((Ping)entry.getValue()).isExpired();
         });
      }

      this.partyPings.entrySet().removeIf((entry) -> {
         return ((Map)entry.getValue()).isEmpty();
      });
   }

   public void cleanupPartyPings(@Nonnull UUID partyId) {
      this.partyPings.remove(partyId);
   }

   public void cleanupPlayerPings(@Nonnull UUID playerUuid, @Nonnull UUID partyId) {
      Map<UUID, Ping> playerPings = (Map)this.partyPings.get(partyId);
      if (playerPings != null) {
         playerPings.remove(playerUuid);
      }

   }

   private void notifyPartyOfPing(@Nonnull PartyInfo party, @Nonnull Ping ping) {
      NotificationHelper.sendInfoByUuid(party.getLeader(), "Ping", ping.getOwnerName() + " pinged a location!");
      UUID[] var3 = party.getMembers();
      int var4 = var3.length;

      for(int var5 = 0; var5 < var4; ++var5) {
         UUID memberUuid = var3[var5];
         NotificationHelper.sendInfoByUuid(memberUuid, "Ping", ping.getOwnerName() + " pinged a location!");
      }

   }
}
