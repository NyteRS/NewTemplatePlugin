package com.example.exampleplugin.party.party;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nullable;
import com.example.exampleplugin.party.config.PartyProConfig;
import com.example.exampleplugin.party.integration.SimpleClaimsIntegration;
import com.example.exampleplugin.party.lang.LanguageManager;
import com.example.exampleplugin.party.stats.PartyStatsTracker;
import com.example.exampleplugin.party.util.NotificationHelper;

public class PartyManager {
   private static final PartyManager INSTANCE = new PartyManager();
   private HashMap<String, PartyInfo> parties = new HashMap();
   private HashMap<String, PartyInvite> partyInvites = new HashMap();
   private Map<UUID, UUID> playerToPartyCache = new ConcurrentHashMap();
   private PlayerNameTracker playerNameTracker = new PlayerNameTracker();
   private Thread cleanupThread = new Thread(() -> {
      while(true) {
         this.cleanExpiredInvites();
         this.cleanOfflinePlayers();
         if (this.dirty && PartyProConfig.getInstance().isPersistPartiesOnRestart()) {
            this.saveParties();
            this.dirty = false;
         }

         try {
            Thread.sleep(5000L);
         } catch (InterruptedException var2) {
            return;
         }
      }
   });
   private HytaleLogger logger = HytaleLogger.getLogger().getSubLogger("PartyPro");
   private boolean dirty = false;
   private Map<UUID, Long> offlineTimestamps = new ConcurrentHashMap();

   public static PartyManager getInstance() {
      return INSTANCE;
   }

   private PartyManager() {
      this.cleanupThread.setDaemon(true);
      this.cleanupThread.start();
      this.logger.at(Level.INFO).log("PartyManager initialized");
   }

   public void loadParties() {
      if (PartyProConfig.getInstance().isPersistPartiesOnRestart()) {
         this.parties = PartyStorage.loadParties();
         this.rebuildCache();
         this.logger.at(Level.INFO).log("Loaded " + this.parties.size() + " parties from storage");
      }

   }

   private void rebuildCache() {
      this.playerToPartyCache.clear();
      Iterator var1 = this.parties.values().iterator();

      while(var1.hasNext()) {
         PartyInfo party = (PartyInfo)var1.next();
         UUID partyId = party.getId();
         this.playerToPartyCache.put(party.getLeader(), partyId);
         UUID[] var4 = party.getMembers();
         int var5 = var4.length;

         for(int var6 = 0; var6 < var5; ++var6) {
            UUID member = var4[var6];
            this.playerToPartyCache.put(member, partyId);
         }
      }

   }

   private void addToCache(UUID playerId, UUID partyId) {
      this.playerToPartyCache.put(playerId, partyId);
   }

   private void removeFromCache(UUID playerId) {
      this.playerToPartyCache.remove(playerId);
   }

   public void saveParties() {
      if (PartyProConfig.getInstance().isPersistPartiesOnRestart()) {
         PartyStorage.saveParties(this.parties);
      }

   }

   private void cleanExpiredInvites() {
      this.partyInvites.entrySet().removeIf((entry) -> {
         return ((PartyInvite)entry.getValue()).isExpired();
      });
   }

   private void cleanOfflinePlayers() {
      int removalMinutes = PartyProConfig.getInstance().getOfflineRemovalTime();
      if (removalMinutes <= 0) {
         this.offlineTimestamps.clear();
      } else {
         long removalMs = (long)(removalMinutes * 60) * 1000L;
         long now = System.currentTimeMillis();
         Iterator var6 = (new ArrayList(this.parties.values())).iterator();

         while(var6.hasNext()) {
            PartyInfo party = (PartyInfo)var6.next();
            UUID[] allMembers = party.getAllPartyMembers();
            UUID[] var9 = allMembers;
            int var10 = allMembers.length;

            for(int var11 = 0; var11 < var10; ++var11) {
               UUID memberId = var9[var11];
               PlayerRef playerRef = Universe.get().getPlayer(memberId);
               boolean isOnline = playerRef != null && playerRef.isValid();
               if (isOnline) {
                  this.offlineTimestamps.remove(memberId);
               } else if (!this.offlineTimestamps.containsKey(memberId)) {
                  this.offlineTimestamps.put(memberId, now);
               } else {
                  long offlineSince = (Long)this.offlineTimestamps.get(memberId);
                  if (now - offlineSince >= removalMs) {
                     this.removeOfflinePlayer(memberId, party);
                     this.offlineTimestamps.remove(memberId);
                  }
               }
            }
         }

      }
   }

   private void removeOfflinePlayer(UUID playerId, PartyInfo party) {
      String playerName = this.playerNameTracker.getPlayerName(playerId);
      if (party.isLeader(playerId)) {
         if (party.getMembers().length == 0) {
            this.disbandParty(party);
            Api var10000 = this.logger.at(Level.INFO);
            String var10001 = party.getName();
            var10000.log("Disbanded party " + var10001 + " - leader " + playerName + " offline too long");
         } else {
            UUID newLeader = party.getMembers()[0];
            party.removeMember(newLeader);
            party.setLeader(newLeader);
            this.removeFromCache(playerId);
            String msg = LanguageManager.getInstance().get("notify.offline_removed_leader", playerName);
            NotificationHelper.sendInfoByUuid(newLeader, "Party", msg);
            this.logger.at(Level.INFO).log("Removed offline leader " + playerName + " from party " + party.getName());
            this.markDirty();
         }
      } else {
         party.removeMember(playerId);
         this.removeFromCache(playerId);
         String msg = LanguageManager.getInstance().get("notify.offline_removed", playerName);
         NotificationHelper.sendInfoByUuid(party.getLeader(), "Party", msg);
         this.logger.at(Level.INFO).log("Removed offline member " + playerName + " from party " + party.getName());
         this.markDirty();
      }

   }

   public void markDirty() {
      this.dirty = true;
   }

   @Nullable
   public PartyInfo getPartyFromPlayer(UUID player) {
      UUID partyId = (UUID)this.playerToPartyCache.get(player);
      return partyId == null ? null : (PartyInfo)this.parties.get(partyId.toString());
   }

   @Nullable
   public PartyInfo getPartyById(UUID partyId) {
      return (PartyInfo)this.parties.get(partyId.toString());
   }

   public PartyInfo createParty(Player owner, PlayerRef playerRef) {
      return this.createParty(playerRef, owner.getDisplayName() + "'s Party");
   }

   public PartyInfo createParty(PlayerRef playerRef, String partyName) {
      PartyInfo party = new PartyInfo(UUID.randomUUID(), playerRef.getUuid(), partyName, new UUID[0]);
      this.parties.put(party.getId().toString(), party);
      this.addToCache(playerRef.getUuid(), party.getId());
      this.markDirty();
      return party;
   }

   public HashMap<String, PartyInfo> getParties() {
      return this.parties;
   }

   public PlayerNameTracker getPlayerNameTracker() {
      return this.playerNameTracker;
   }

   public void invitePlayerToParty(PlayerRef recipient, PartyInfo partyInfo, PlayerRef sender) {
      this.partyInvites.put(recipient.getUuid().toString(), new PartyInvite(recipient.getUuid(), sender.getUuid(), partyInfo.getId()));
   }

   @Nullable
   public PartyInvite getPendingInvite(UUID playerUuid) {
      PartyInvite invite = (PartyInvite)this.partyInvites.get(playerUuid.toString());
      if (invite != null && invite.isExpired()) {
         this.partyInvites.remove(playerUuid.toString());
         return null;
      } else {
         return invite;
      }
   }

   public PartyInvite acceptInvite(PlayerRef player) {
      String key = player.getUuid().toString();
      PartyInvite invite = (PartyInvite)this.partyInvites.get(key);
      if (invite != null && !invite.isExpired()) {
         PartyInfo party = this.getPartyById(invite.partyId());
         if (party != null && party.canAddMember()) {
            party.addMember(player.getUuid());
            this.addToCache(player.getUuid(), party.getId());
            PartyStatsTracker.getInstance().onMemberJoined(party.getId());
            this.partyInvites.remove(key);
            this.markDirty();
            if (PartyProConfig.getInstance().isSimpleClaimsIntegration()) {
               SimpleClaimsIntegration.getInstance().syncPartyMembers(party);
            }

            return invite;
         } else {
            return null;
         }
      } else {
         this.partyInvites.remove(key);
         return null;
      }
   }

   public PartyManager.JoinResult joinParty(UUID playerId, PartyInfo party) {
      if (party == null) {
         return PartyManager.JoinResult.PARTY_NOT_FOUND;
      } else if (!party.canAddMember()) {
         return PartyManager.JoinResult.PARTY_FULL;
      } else if (party.isLeaderOrMember(playerId)) {
         return PartyManager.JoinResult.ALREADY_IN_PARTY;
      } else {
         PartyInfo existingParty = this.getPartyFromPlayer(playerId);
         if (existingParty != null) {
            return PartyManager.JoinResult.ALREADY_IN_OTHER_PARTY;
         } else {
            party.addMember(playerId);
            this.addToCache(playerId, party.getId());
            PartyStatsTracker.getInstance().onMemberJoined(party.getId());
            this.markDirty();
            if (PartyProConfig.getInstance().isSimpleClaimsIntegration()) {
               SimpleClaimsIntegration.getInstance().syncPartyMembers(party);
            }

            return PartyManager.JoinResult.SUCCESS;
         }
      }
   }

   public void declineInvite(UUID playerUuid) {
      this.partyInvites.remove(playerUuid.toString());
   }

   public void leaveParty(PlayerRef player, PartyInfo partyInfo) {
      UUID playerId = player.getUuid();
      PartyStatsTracker.getInstance().onMemberLeft(partyInfo.getId());
      if (PartyProConfig.getInstance().isSimpleClaimsIntegration()) {
         SimpleClaimsIntegration.getInstance().removeFromAllies(playerId, partyInfo);
      }

      if (partyInfo.isLeader(playerId)) {
         if (partyInfo.getMembers().length == 0) {
            this.disbandParty(partyInfo);
            NotificationHelper.sendSuccess(player, "Party", "Party disbanded!");
            return;
         }

         UUID newLeader = partyInfo.getMembers()[0];
         partyInfo.removeMember(newLeader);
         partyInfo.setLeader(newLeader);
         NotificationHelper.sendSuccess(player, "Party", "Ownership transferred to " + this.getPlayerNameTracker().getPlayerName(newLeader));
      } else {
         partyInfo.removeMember(playerId);
      }

      this.removeFromCache(playerId);
      NotificationHelper.sendSuccess(player, "Party", "You left the party!");
      this.markDirty();
   }

   public void kickPlayer(UUID playerToKick, PartyInfo partyInfo) {
      PartyStatsTracker.getInstance().onMemberLeft(partyInfo.getId());
      if (PartyProConfig.getInstance().isSimpleClaimsIntegration()) {
         SimpleClaimsIntegration.getInstance().removeFromAllies(playerToKick, partyInfo);
      }

      partyInfo.removeMember(playerToKick);
      this.removeFromCache(playerToKick);
      this.markDirty();
   }

   public void disbandParty(PartyInfo partyInfo) {
      if (PartyProConfig.getInstance().isSimpleClaimsIntegration()) {
         SimpleClaimsIntegration.getInstance().onPartyDisband(partyInfo);
      }

      this.removeFromCache(partyInfo.getLeader());
      UUID[] var2 = partyInfo.getMembers();
      int var3 = var2.length;

      for(int var4 = 0; var4 < var3; ++var4) {
         UUID member = var2[var4];
         this.removeFromCache(member);
      }

      this.parties.remove(partyInfo.getId().toString());
      this.markDirty();
   }

   public HytaleLogger getLogger() {
      return this.logger;
   }

   public static enum JoinResult {
      SUCCESS,
      PARTY_NOT_FOUND,
      PARTY_FULL,
      ALREADY_IN_PARTY,
      ALREADY_IN_OTHER_PARTY;

      // $FF: synthetic method
      private static PartyManager.JoinResult[] $values() {
         return new PartyManager.JoinResult[]{SUCCESS, PARTY_NOT_FOUND, PARTY_FULL, ALREADY_IN_PARTY, ALREADY_IN_OTHER_PARTY};
      }
   }

   public static enum KickResult {
      SUCCESS,
      CANNOT_KICK_SELF,
      NO_PARTY,
      NOT_HOST,
      NOT_IN_PARTY;

      // $FF: synthetic method
      private static PartyManager.KickResult[] $values() {
         return new PartyManager.KickResult[]{SUCCESS, CANNOT_KICK_SELF, NO_PARTY, NOT_HOST, NOT_IN_PARTY};
      }
   }

   public static enum InviteResult {
      SUCCESS,
      CANNOT_INVITE_SELF,
      NO_PARTY,
      NOT_HOST,
      PLAYER_OFFLINE,
      ALREADY_IN_PARTY,
      ALREADY_INVITED;

      // $FF: synthetic method
      private static PartyManager.InviteResult[] $values() {
         return new PartyManager.InviteResult[]{SUCCESS, CANNOT_INVITE_SELF, NO_PARTY, NOT_HOST, PLAYER_OFFLINE, ALREADY_IN_PARTY, ALREADY_INVITED};
      }
   }
}
