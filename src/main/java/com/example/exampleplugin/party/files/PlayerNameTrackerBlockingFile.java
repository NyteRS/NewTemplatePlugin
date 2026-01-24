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
import com.example.exampleplugin.party.party.PlayerNameTracker;
import com.example.exampleplugin.party.util.FileUtils;

public class PlayerNameTrackerBlockingFile extends BlockingDiskFile {
   private PlayerNameTracker tracker = new PlayerNameTracker();

   public PlayerNameTrackerBlockingFile() {
      super(Path.of(FileUtils.NAMES_CACHE_PATH, new String[0]));
   }

   protected void read(BufferedReader bufferedReader) throws IOException {
      JsonObject root = JsonParser.parseReader(bufferedReader).getAsJsonObject();
      if (root != null) {
         JsonArray namesArray = root.getAsJsonArray("Names");
         if (namesArray != null) {
            HashMap<UUID, String> names = new HashMap();
            namesArray.forEach((jsonElement) -> {
               JsonObject nameObj = jsonElement.getAsJsonObject();
               UUID uuid = UUID.fromString(nameObj.get("Uuid").getAsString());
               String name = nameObj.get("Name").getAsString();
               names.put(uuid, name);
            });
            this.tracker.setNames(names);
         }
      }
   }

   protected void write(BufferedWriter bufferedWriter) throws IOException {
      JsonObject root = new JsonObject();
      JsonArray namesArray = new JsonArray();
      this.tracker.getNames().forEach((uuid, name) -> {
         JsonObject nameObj = new JsonObject();
         nameObj.addProperty("Uuid", uuid.toString());
         nameObj.addProperty("Name", name);
         namesArray.add(nameObj);
      });
      root.add("Names", namesArray);
      bufferedWriter.write(root.toString());
   }

   protected void create(BufferedWriter bufferedWriter) throws IOException {
      JsonObject root = new JsonObject();
      JsonArray names = new JsonArray();
      root.add("Names", names);
      bufferedWriter.write(root.toString());
   }

   public PlayerNameTracker getTracker() {
      return this.tracker;
   }
}
