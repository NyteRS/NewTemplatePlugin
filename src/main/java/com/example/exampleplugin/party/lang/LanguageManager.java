package com.example.exampleplugin.party.lang;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import com.example.exampleplugin.party.config.PartyProConfig;

public class LanguageManager {
   private static LanguageManager instance;
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final Type MAP_TYPE = (new TypeToken<Map<String, String>>() {
   }).getType();
   private Map<String, String> messages = new HashMap();
   private Path languageFolder;

   private LanguageManager() {
   }

   public static LanguageManager getInstance() {
      if (instance == null) {
         instance = new LanguageManager();
      }

      return instance;
   }

   public void initialize(Path configFolder) {
      this.languageFolder = configFolder.resolve("language");

      try {
         Files.createDirectories(this.languageFolder);
      } catch (IOException var3) {
         System.out.println("[PartyPro] Failed to create language folder: " + var3.getMessage());
      }

      this.extractDefaultLanguage("en");
      this.extractDefaultLanguage("de");
      this.extractDefaultLanguage("es");
      this.extractDefaultLanguage("br");
      this.extractDefaultLanguage("hu");
      this.loadLanguage(PartyProConfig.getInstance().getLanguage());
   }

   private void extractDefaultLanguage(String lang) {
      Path langFile = this.languageFolder.resolve(lang.toLowerCase() + ".json");

      try {
         InputStream is = this.getClass().getResourceAsStream("/language/" + lang.toLowerCase() + ".json");

         label67: {
            try {
               if (is != null) {
                  String defaultContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                  Map<String, String> defaultMessages = (Map)GSON.fromJson(defaultContent, MAP_TYPE);
                  if (!Files.exists(langFile, new LinkOption[0])) {
                     Files.writeString(langFile, defaultContent, new OpenOption[0]);
                     System.out.println("[PartyPro] Extracted default language file: " + lang + ".json");
                     break label67;
                  }

                  String existingContent = Files.readString(langFile);
                  Map<String, String> existingMessages = (Map)GSON.fromJson(existingContent, MAP_TYPE);
                  int addedKeys = 0;
                  Iterator var9 = defaultMessages.entrySet().iterator();

                  while(var9.hasNext()) {
                     Entry<String, String> entry = (Entry)var9.next();
                     if (!existingMessages.containsKey(entry.getKey())) {
                        existingMessages.put((String)entry.getKey(), (String)entry.getValue());
                        ++addedKeys;
                     }
                  }

                  if (addedKeys > 0) {
                     Files.writeString(langFile, GSON.toJson(existingMessages), new OpenOption[0]);
                     System.out.println("[PartyPro] Added " + addedKeys + " new keys to " + lang + ".json");
                  }
                  break label67;
               }
            } catch (Throwable var12) {
               if (is != null) {
                  try {
                     is.close();
                  } catch (Throwable var11) {
                     var12.addSuppressed(var11);
                  }
               }

               throw var12;
            }

            if (is != null) {
               is.close();
            }

            return;
         }

         if (is != null) {
            is.close();
         }
      } catch (IOException var13) {
         System.out.println("[PartyPro] Failed to process language file " + lang + ": " + var13.getMessage());
      }

   }

   public void loadLanguage(String lang) {
      Path langFile = this.languageFolder.resolve(lang.toLowerCase() + ".json");
      if (Files.exists(langFile, new LinkOption[0])) {
         try {
            String json = Files.readString(langFile);
            this.messages = (Map)GSON.fromJson(json, MAP_TYPE);
            System.out.println("[PartyPro] Loaded language: " + lang.toUpperCase());
         } catch (IOException var4) {
            System.out.println("[PartyPro] Failed to load language file: " + var4.getMessage());
            this.loadFallback();
         }
      } else {
         System.out.println("[PartyPro] Language file not found: " + lang + ".json, using EN");
         this.loadFallback();
      }

   }

   private void loadFallback() {
      Path enFile = this.languageFolder.resolve("en.json");
      if (Files.exists(enFile, new LinkOption[0])) {
         try {
            String json = Files.readString(enFile);
            this.messages = (Map)GSON.fromJson(json, MAP_TYPE);
         } catch (IOException var3) {
            this.messages = this.getDefaultMessages();
         }
      } else {
         this.messages = this.getDefaultMessages();
      }

   }

   public String get(String key) {
      return (String)this.messages.getOrDefault(key, key);
   }

   public String get(String key, Object... args) {
      String message = (String)this.messages.getOrDefault(key, key);
      if (args.length > 0) {
         for(int i = 0; i < args.length; ++i) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
         }
      }

      return message;
   }

   public void reload() {
      this.loadLanguage(PartyProConfig.getInstance().getLanguage());
   }

   private Map<String, String> getDefaultMessages() {
      Map<String, String> m = new HashMap();
      m.put("error.not_in_party", "You are not in a party");
      m.put("error.already_in_party", "You are already in a party");
      m.put("error.party_full", "The party is full");
      m.put("error.not_leader", "You are not the party leader");
      m.put("error.party_not_found", "Party not found");
      m.put("error.player_not_found", "Player not found");
      m.put("error.player_in_party", "This player is already in a party");
      m.put("error.player_not_member", "{0} is not in your party");
      m.put("error.target_not_in_world", "Target player is not in the world");
      m.put("error.cannot_invite_self", "You cannot invite yourself");
      m.put("error.cannot_kick_self", "You cannot kick yourself");
      m.put("error.cannot_tp_self", "You cannot teleport to yourself");
      m.put("error.leader_only_kick", "Only the party leader can kick members");
      m.put("error.leader_only_transfer", "Only the party leader can transfer ownership");
      m.put("error.leader_only_rename", "Only the party leader can rename the party");
      m.put("error.no_pending_invite", "You have no pending invite");
      m.put("error.invite_expired", "The invite has expired");
      m.put("error.invite_auto_declined", "{0} has auto-decline enabled");
      m.put("error.teleport_disabled", "Teleport is disabled");
      m.put("error.ping_need_party", "You need to be in a party to ping");
      m.put("error.ping_look_at_block", "Look at a block to ping");
      m.put("error.cooldown_invite", "Please wait {0}s before sending another invite");
      m.put("error.cooldown_teleport", "Please wait {0}s before teleporting again");
      m.put("error.cooldown_ping", "Please wait {0}s before pinging again");
      m.put("success.party_created", "Party created");
      m.put("success.party_disbanded", "Party disbanded");
      m.put("success.party_left", "You left the party");
      m.put("success.ownership_transferred", "Leadership transferred to {0}");
      m.put("success.invite_sent", "Invite sent to {0}");
      m.put("success.party_joined", "You joined {0}");
      m.put("success.invite_declined", "Invite declined");
      m.put("success.player_kicked", "{0} has been kicked from the party");
      m.put("success.teleporting", "Teleporting to {0}...");
      m.put("notify.invite_received", "{0} invited you to {1}");
      m.put("notify.player_joined", "{0} joined your party");
      m.put("notify.player_left", "{0} left the party");
      m.put("notify.you_were_kicked", "You have been kicked from the party");
      m.put("notify.new_leader", "{0} is now the party leader");
      m.put("notify.party_disbanded", "The party has been disbanded");
      m.put("notify.offline_removed", "{0} was removed from the party (offline too long)");
      m.put("notify.offline_removed_leader", "{0} was removed as leader (offline too long). You are now the leader");
      m.put("title.party", "Party");
      m.put("title.party_invite", "Party Invite");
      m.put("title.party_info", "=== Party Info ===");
      m.put("gui.accept", "Accept");
      m.put("gui.decline", "Decline");
      m.put("gui.invite_from", "Invite from {0}");
      m.put("gui.members", "Members: {0}/{1}");
      return m;
   }
}
