package com.example.exampleplugin.party.party;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class PartyStorage {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static Path storagePath;

   public static void initialize(Path path) {
      storagePath = path;
      if (!Files.exists(path, new LinkOption[0])) {
         try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, "[]", new OpenOption[0]);
            System.out.println("[PartyPro] Created empty party_storage.json");
         } catch (IOException var2) {
            System.out.println("[PartyPro] Failed to create party_storage.json: " + var2.getMessage());
         }
      }

   }

   public static void saveParties(HashMap<String, PartyInfo> parties) {
      if (storagePath == null) {
         System.out.println("[PartyPro] Storage path not initialized, cannot save parties");
      } else {
         try {
            List<PartyStorage.PartyData> partyDataList = new ArrayList();
            Iterator var2 = parties.values().iterator();

            while(var2.hasNext()) {
               PartyInfo party = (PartyInfo)var2.next();
               partyDataList.add(PartyStorage.PartyData.fromPartyInfo(party));
            }

            Files.createDirectories(storagePath.getParent());
            Files.writeString(storagePath, GSON.toJson(partyDataList), new OpenOption[0]);
            System.out.println("[PartyPro] Saved " + partyDataList.size() + " parties to storage");
         } catch (IOException var4) {
            System.out.println("[PartyPro] Failed to save parties: " + var4.getMessage());
         }

      }
   }

   public static HashMap<String, PartyInfo> loadParties() {
      HashMap<String, PartyInfo> parties = new HashMap();
      if (storagePath != null && Files.exists(storagePath, new LinkOption[0])) {
         try {
            String json = Files.readString(storagePath);
            Type listType = (new TypeToken<List<PartyStorage.PartyData>>() {
            }).getType();
            List<PartyStorage.PartyData> partyDataList = (List)GSON.fromJson(json, listType);
            if (partyDataList != null) {
               Iterator var4 = partyDataList.iterator();

               while(var4.hasNext()) {
                  PartyStorage.PartyData data = (PartyStorage.PartyData)var4.next();
                  PartyInfo party = data.toPartyInfo();
                  parties.put(party.getId().toString(), party);
               }
            }

            System.out.println("[PartyPro] Loaded " + parties.size() + " parties from storage");
         } catch (Exception var7) {
            System.out.println("[PartyPro] Failed to load parties: " + var7.getMessage());
         }

         return parties;
      } else {
         return parties;
      }
   }

   public static class PartyData {
      public String id;
      public String leader;
      public String name;
      public String[] members;
      public long createdAt;
      public boolean pvpEnabled;
      public boolean isPublic;
      public String password;

      public static PartyStorage.PartyData fromPartyInfo(PartyInfo party) {
         PartyStorage.PartyData data = new PartyStorage.PartyData();
         data.id = party.getId().toString();
         data.leader = party.getLeader().toString();
         data.name = party.getName();
         data.members = new String[party.getMembers().length];

         for(int i = 0; i < party.getMembers().length; ++i) {
            data.members[i] = party.getMembers()[i].toString();
         }

         data.createdAt = party.getCreatedAt();
         data.pvpEnabled = party.isPvpEnabled();
         data.isPublic = party.isPublic();
         data.password = party.getPassword();
         return data;
      }

      public PartyInfo toPartyInfo() {
         UUID[] memberUuids = new UUID[this.members.length];

         for(int i = 0; i < this.members.length; ++i) {
            memberUuids[i] = UUID.fromString(this.members[i]);
         }

         PartyInfo party = new PartyInfo(UUID.fromString(this.id), UUID.fromString(this.leader), this.name, memberUuids);
         party.setCreatedAt(this.createdAt);
         party.setPvpEnabled(this.pvpEnabled);
         party.setPublic(this.isPublic);
         party.setPassword(this.password);
         return party;
      }
   }
}
