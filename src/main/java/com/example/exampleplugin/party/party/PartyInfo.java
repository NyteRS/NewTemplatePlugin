package com.example.exampleplugin.party.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.example.exampleplugin.party.config.PartyProConfig;

public class PartyInfo {
   private UUID id;
   private UUID leader;
   private String name;
   private List<UUID> members;
   private long createdAt;
   private boolean pvpEnabled;
   private boolean isPublic;
   private String password;
   private int maxSize;

   public PartyInfo(UUID id, UUID leader, String name, UUID[] members) {
      this.pvpEnabled = false;
      this.isPublic = false;
      this.password = "";
      this.maxSize = 0;
      this.id = id;
      this.leader = leader;
      this.name = name;
      this.members = new ArrayList();
      UUID[] var5 = members;
      int var6 = members.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         UUID m = var5[var7];
         this.members.add(m);
      }

      this.createdAt = System.currentTimeMillis();
   }

   public PartyInfo() {
      this(UUID.randomUUID(), UUID.randomUUID(), "", new UUID[0]);
   }

   public UUID getId() {
      return this.id;
   }

   public void setId(UUID id) {
      this.id = id;
   }

   public UUID getLeader() {
      return this.leader;
   }

   public void setLeader(UUID leader) {
      this.leader = leader;
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public long getCreatedAt() {
      return this.createdAt;
   }

   public void setCreatedAt(long createdAt) {
      this.createdAt = createdAt;
   }

   public boolean isPvpEnabled() {
      return this.pvpEnabled;
   }

   public void setPvpEnabled(boolean pvpEnabled) {
      this.pvpEnabled = pvpEnabled;
   }

   public boolean isPublic() {
      return this.isPublic;
   }

   public void setPublic(boolean isPublic) {
      this.isPublic = isPublic;
   }

   public String getPassword() {
      return this.password;
   }

   public void setPassword(String password) {
      this.password = password != null ? password : "";
   }

   public boolean hasPassword() {
      return !this.password.isEmpty();
   }

   public boolean checkPassword(String input) {
      return this.password.equals(input);
   }

   public boolean isLeader(UUID uuid) {
      return this.leader.equals(uuid);
   }

   public boolean isMember(UUID uuid) {
      return this.members.contains(uuid);
   }

   public boolean isLeaderOrMember(UUID uuid) {
      return this.isLeader(uuid) || this.isMember(uuid);
   }

   public int getTotalMemberCount() {
      return 1 + this.members.size();
   }

   public int getMaxSize() {
      return this.maxSize <= 0 ? PartyProConfig.getInstance().getMaxPartySize() : Math.min(this.maxSize, PartyProConfig.getInstance().getMaxPartySize());
   }

   public void setMaxSize(int maxSize) {
      this.maxSize = Math.max(2, Math.min(maxSize, PartyProConfig.getInstance().getMaxPartySize()));
   }

   public boolean canAddMember() {
      return this.getTotalMemberCount() < this.getMaxSize();
   }

   public UUID[] getMembers() {
      return (UUID[])this.members.toArray(new UUID[0]);
   }

   public void setMembers(UUID[] members) {
      this.members.clear();
      UUID[] var2 = members;
      int var3 = members.length;

      for(int var4 = 0; var4 < var3; ++var4) {
         UUID m = var2[var4];
         this.members.add(m);
      }

   }

   public void addMember(UUID uuid) {
      if (this.canAddMember() && !this.isLeaderOrMember(uuid)) {
         this.members.add(uuid);
      }
   }

   public void removeMember(UUID uuid) {
      this.members.remove(uuid);
   }

   public UUID[] getAllPartyMembers() {
      UUID[] all = new UUID[1 + this.members.size()];
      all[0] = this.leader;

      for(int i = 0; i < this.members.size(); ++i) {
         all[i + 1] = (UUID)this.members.get(i);
      }

      return all;
   }

   public String toString() {
      String var10000 = String.valueOf(this.id);
      return "PartyInfo{id=" + var10000 + ", leader=" + String.valueOf(this.leader) + ", name='" + this.name + "', members=" + String.valueOf(this.members) + "}";
   }
}
