package com.example.exampleplugin.party.party;

import java.util.HashMap;
import java.util.UUID;
import java.util.Map.Entry;

public class PlayerNameTracker {
   private HashMap<UUID, String> names = new HashMap();

   public String getPlayerName(UUID uuid) {
      return (String)this.names.getOrDefault(uuid, "Unknown");
   }

   public void setPlayerName(UUID uuid, String name) {
      this.names.put(uuid, name);
   }

   public UUID getUuidByName(String name) {
      return (UUID)this.names.entrySet().stream().filter((entry) -> {
         return ((String)entry.getValue()).equalsIgnoreCase(name);
      }).map(Entry::getKey).findFirst().orElse((Object)null);
   }

   public HashMap<UUID, String> getNames() {
      return this.names;
   }

   public void setNames(HashMap<UUID, String> names) {
      this.names = names;
   }
}
