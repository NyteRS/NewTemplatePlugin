package com.example.exampleplugin.party.util;

import com.hypixel.hytale.server.core.Constants;
import java.io.File;
import java.io.FileWriter;

public class FileUtils {
   public static String MAIN_PATH;
   public static String PARTY_PATH;
   public static String NAMES_CACHE_PATH;

   public static void ensureDirectory(String path) {
      File file = new File(path);
      if (!file.exists()) {
         file.mkdirs();
      }

   }

   public static void ensureMainDirectory() {
      ensureDirectory(MAIN_PATH);
   }

   public static File ensureFile(String path, String defaultContent) {
      File file = new File(path);
      if (!file.exists()) {
         try {
            file.createNewFile();
            FileWriter writer = new FileWriter(file);
            writer.write(defaultContent);
            writer.close();
         } catch (Exception var4) {
            var4.printStackTrace();
         }
      }

      return file;
   }

   static {
      MAIN_PATH = Constants.UNIVERSE_PATH.resolve("PartyPro").toAbsolutePath().toString();
      PARTY_PATH = MAIN_PATH + File.separator + "Parties.json";
      NAMES_CACHE_PATH = MAIN_PATH + File.separator + "NameCache.json";
   }
}
