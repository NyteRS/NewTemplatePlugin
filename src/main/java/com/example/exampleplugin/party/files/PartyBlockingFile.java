package com.example.exampleplugin.party.files;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.util.io.BlockingDiskFile;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.UUID;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.util.FileUtils;

public class PartyBlockingFile extends BlockingDiskFile {
   private HashMap<String, PartyInfo> parties = new HashMap();

   public PartyBlockingFile() {
      super(Path.of(FileUtils.PARTY_PATH, new String[0]));
   }

   protected void read(BufferedReader bufferedReader) throws IOException {
      JsonObject root = JsonParser.parseReader(bufferedReader).getAsJsonObject();
      if (root != null) {
         JsonArray partiesArray = root.getAsJsonArray("Parties");
         if (partiesArray != null) {
            this.parties = new HashMap();
            partiesArray.forEach((jsonElement) -> {
               JsonObject party = jsonElement.getAsJsonObject();
               PartyInfo partyInfo = new PartyInfo(UUID.fromString(party.get("Id").getAsString()), UUID.fromString(party.get("Leader").getAsString()), party.get("Name").getAsString(), (UUID[])party.get("Members").getAsJsonArray().asList().stream().map((member) -> {
                  return UUID.fromString(member.getAsString());
               }).toArray((x$0) -> {
                  return new UUID[x$0];
               }));
               if (party.has("CreatedAt")) {
                  partyInfo.setCreatedAt(party.get("CreatedAt").getAsLong());
               }

               if (party.has("PvpEnabled")) {
                  partyInfo.setPvpEnabled(party.get("PvpEnabled").getAsBoolean());
               }

               this.parties.put(partyInfo.getId().toString(), partyInfo);
            });
         }
      }
   }

   protected void write(BufferedWriter bufferedWriter) throws IOException {
      JsonObject root = new JsonObject();
      JsonArray partiesArray = new JsonArray();
      this.parties.values().forEach((partyInfo) -> {
         JsonObject party = new JsonObject();
         party.addProperty("Id", partyInfo.getId().toString());
         party.addProperty("Leader", partyInfo.getLeader().toString());
         party.addProperty("Name", partyInfo.getName());
         JsonArray members = new JsonArray();
         UUID[] arr$ = partyInfo.getMembers();
         int len$ = arr$.length;

         for(int i$ = 0; i$ < len$; ++i$) {
            UUID member = arr$[i$];
            members.add(member.toString());
         }

         party.add("Members", members);
         party.addProperty("CreatedAt", partyInfo.getCreatedAt());
         party.addProperty("PvpEnabled", partyInfo.isPvpEnabled());
         partiesArray.add(party);
      });
      root.add("Parties", partiesArray);
      bufferedWriter.write(root.toString());
   }

   protected void create(BufferedWriter bufferedWriter) throws IOException {
      JsonObject root = new JsonObject();
      JsonArray parties = new JsonArray();
      root.add("Parties", parties);
      bufferedWriter.write(root.toString());
   }

   public HashMap<String, PartyInfo> getParties() {
      return this.parties;
   }
}
