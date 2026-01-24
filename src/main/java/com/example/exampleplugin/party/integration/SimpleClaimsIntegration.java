package com.example.exampleplugin.party.integration;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import com.example.exampleplugin.party.party.PartyInfo;

public class SimpleClaimsIntegration {
   private static SimpleClaimsIntegration instance;
   private static boolean available = false;
   private static boolean initialized = false;
   private Class<?> claimManagerClass;
   private Method getInstanceMethod;
   private Method getPartyFromPlayerMethod;
   private Method getPartiesMethod;
   private Method markDirtyMethod;
   private Class<?> partyInfoClass;
   private Method getPlayerAlliesMethod;
   private Method getMembersMethod;

   public static SimpleClaimsIntegration getInstance() {
      if (instance == null) {
         instance = new SimpleClaimsIntegration();
      }

      return instance;
   }

   private SimpleClaimsIntegration() {
   }

   public boolean initialize() {
      if (initialized) {
         return available;
      } else {
         initialized = true;

         try {
            this.claimManagerClass = Class.forName("com.buuz135.simpleclaims.claim.ClaimManager");
            this.partyInfoClass = Class.forName("com.buuz135.simpleclaims.claim.party.PartyInfo");
            this.getInstanceMethod = this.claimManagerClass.getMethod("getInstance");
            this.getPartyFromPlayerMethod = this.claimManagerClass.getMethod("getPartyFromPlayer", UUID.class);
            this.getPartiesMethod = this.claimManagerClass.getMethod("getParties");
            this.markDirtyMethod = this.claimManagerClass.getMethod("markDirty");
            this.getPlayerAlliesMethod = this.partyInfoClass.getMethod("getPlayerAllies");
            this.getMembersMethod = this.partyInfoClass.getMethod("getMembers");
            available = true;
            System.out.println("[PartyPro] SimpleClaims integration enabled!");
            return true;
         } catch (ClassNotFoundException var2) {
            System.out.println("[PartyPro] SimpleClaims not found - integration disabled");
            available = false;
            return false;
         } catch (Exception var3) {
            System.out.println("[PartyPro] SimpleClaims integration failed: " + var3.getMessage());
            available = false;
            return false;
         }
      }
   }

   public boolean isAvailable() {
      return available;
   }

   public void syncPartyMembers(PartyInfo partyProParty) {
      if (available && partyProParty != null) {
         try {
            Object claimManager = this.getInstanceMethod.invoke((Object)null);
            UUID[] allMembers = partyProParty.getAllPartyMembers();
            UUID[] var4 = allMembers;
            int var5 = allMembers.length;

            for(int var6 = 0; var6 < var5; ++var6) {
               UUID memberId = var4[var6];
               Object simpleClaimsParty = this.getPartyFromPlayerMethod.invoke(claimManager, memberId);
               if (simpleClaimsParty != null) {
                  Set<UUID> playerAllies = (Set)this.getPlayerAlliesMethod.invoke(simpleClaimsParty);
                  boolean changed = false;
                  UUID[] var11 = allMembers;
                  int var12 = allMembers.length;

                  for(int var13 = 0; var13 < var12; ++var13) {
                     UUID otherMemberId = var11[var13];
                     if (!otherMemberId.equals(memberId) && !playerAllies.contains(otherMemberId)) {
                        playerAllies.add(otherMemberId);
                        changed = true;
                     }
                  }

                  if (changed) {
                     this.markDirtyMethod.invoke(claimManager);
                  }
               }
            }
         } catch (Exception var15) {
            System.out.println("[PartyPro] SimpleClaims sync error: " + var15.getMessage());
         }

      }
   }

   public void removeFromAllies(UUID playerId, PartyInfo partyProParty) {
      if (available && partyProParty != null) {
         try {
            Object claimManager = this.getInstanceMethod.invoke((Object)null);
            UUID[] remainingMembers = partyProParty.getAllPartyMembers();
            UUID[] var5 = remainingMembers;
            int var6 = remainingMembers.length;

            for(int var7 = 0; var7 < var6; ++var7) {
               UUID memberId = var5[var7];
               if (!memberId.equals(playerId)) {
                  Object simpleClaimsParty = this.getPartyFromPlayerMethod.invoke(claimManager, memberId);
                  if (simpleClaimsParty != null) {
                     Set<UUID> playerAllies = (Set)this.getPlayerAlliesMethod.invoke(simpleClaimsParty);
                     if (playerAllies.remove(playerId)) {
                        this.markDirtyMethod.invoke(claimManager);
                     }
                  }
               }
            }

            Object leavingPlayerParty = this.getPartyFromPlayerMethod.invoke(claimManager, playerId);
            if (leavingPlayerParty != null) {
               Set<UUID> playerAllies = (Set)this.getPlayerAlliesMethod.invoke(leavingPlayerParty);
               boolean changed = false;
               UUID[] var16 = remainingMembers;
               int var17 = remainingMembers.length;

               for(int var18 = 0; var18 < var17; ++var18) {
                  UUID memberId = var16[var18];
                  if (playerAllies.remove(memberId)) {
                     changed = true;
                  }
               }

               if (changed) {
                  this.markDirtyMethod.invoke(claimManager);
               }
            }
         } catch (Exception var12) {
            System.out.println("[PartyPro] SimpleClaims remove ally error: " + var12.getMessage());
         }

      }
   }

   public void onPartyDisband(PartyInfo partyProParty) {
      if (available && partyProParty != null) {
         try {
            Object claimManager = this.getInstanceMethod.invoke((Object)null);
            UUID[] allMembers = partyProParty.getAllPartyMembers();
            UUID[] var4 = allMembers;
            int var5 = allMembers.length;

            for(int var6 = 0; var6 < var5; ++var6) {
               UUID memberId = var4[var6];
               Object simpleClaimsParty = this.getPartyFromPlayerMethod.invoke(claimManager, memberId);
               if (simpleClaimsParty != null) {
                  Set<UUID> playerAllies = (Set)this.getPlayerAlliesMethod.invoke(simpleClaimsParty);
                  boolean changed = false;
                  UUID[] var11 = allMembers;
                  int var12 = allMembers.length;

                  for(int var13 = 0; var13 < var12; ++var13) {
                     UUID otherMemberId = var11[var13];
                     if (!otherMemberId.equals(memberId) && playerAllies.remove(otherMemberId)) {
                        changed = true;
                     }
                  }

                  if (changed) {
                     this.markDirtyMethod.invoke(claimManager);
                  }
               }
            }
         } catch (Exception var15) {
            System.out.println("[PartyPro] SimpleClaims disband sync error: " + var15.getMessage());
         }

      }
   }
}
