package com.example.exampleplugin.party.hud;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.connection.PongType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import com.example.exampleplugin.party.chat.PartyChatManager;
import com.example.exampleplugin.party.config.PartyProConfig;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.systems.PartyHealthTracker;

public class PartyHudCompact extends CustomUIHud implements IPartyHud {
   private static final int MAX_COMPACT_MEMBERS = 16;
   private static final int COMPACT_HUD_WIDTH = 340;
   private static final long PING_GOOD_THRESHOLD_MS = 100L;
   private static final long PING_MEDIUM_THRESHOLD_MS = 200L;
   private static final String PING_COLOR_GOOD = "#88cc88";
   private static final String PING_COLOR_MEDIUM = "#cccc44";
   private static final String PING_COLOR_BAD = "#cc4444";
   private static final String PING_COLOR_OFFLINE = "#888888";
   private static final double MICROS_TO_MS = 1000.0D;
   private static final String[] SLOT_COLORS = new String[]{"#e94560", "#58a6ff", "#56d364", "#f0c040", "#00bcd4", "#9c27b0", "#ff9800", "#e91e63", "#607d8b", "#795548", "#8bc34a", "#03a9f4", "#ff5722", "#673ab7", "#009688", "#cddc39"};
   private static final String NAME_COLOR_OFFLINE = "#666666";
   private ScheduledFuture<?> updateTask;
   private final UUID playerUuid;

   public PartyHudCompact(@Nonnull PlayerRef playerRef) {
      super(playerRef);
      this.playerUuid = playerRef.getUuid();
   }

   protected void build(@Nonnull UICommandBuilder uiCommandBuilder) {
      uiCommandBuilder.append("Hud/PartyPro/PartyHudCompact.ui");
      PartyProConfig config = PartyProConfig.getInstance();
      PartyHudManager.HudSettings playerSettings = PartyHudManager.getSettings(this.playerUuid);
      Anchor anchor = new Anchor();
      boolean useLeftSide = playerSettings.isHudOnLeft();
      if (useLeftSide) {
         anchor.setLeft(Value.of(config.getHudOffset()));
      } else {
         anchor.setRight(Value.of(config.getHudOffset()));
      }

      anchor.setTop(Value.of(config.getHudTop()));
      anchor.setWidth(Value.of(340));
      uiCommandBuilder.setObject("#PartyHudCompact.Anchor", anchor);
      this.updateContent(uiCommandBuilder);
   }

   public void startAutoUpdate() {
      long interval = (long)PartyProConfig.getInstance().getHudUpdateInterval();
      this.updateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(this::refreshHud, interval, interval, TimeUnit.MILLISECONDS);
   }

   public void stopAutoUpdate() {
      if (this.updateTask != null && !this.updateTask.isCancelled()) {
         this.updateTask.cancel(false);
         this.updateTask = null;
      }

   }

   public void refreshHud() {
      try {
         UICommandBuilder commandBuilder = new UICommandBuilder();
         this.updateContent(commandBuilder);
         this.update(false, commandBuilder);
      } catch (Exception var2) {
      }

   }

   private void updateContent(@Nonnull UICommandBuilder cmd) {
      PartyInfo party = PartyManager.getInstance().getPartyFromPlayer(this.playerUuid);
      PartyHudManager.HudSettings settings = PartyHudManager.getSettings(this.playerUuid);
      if (party != null && settings.enabled) {
         PartyManager manager = PartyManager.getInstance();
         cmd.set("#PartyHeader.Visible", true);
         cmd.set("#PartyName.Text", party.getName());
         boolean chatEnabled = PartyChatManager.getInstance().isPartyChatEnabled(this.playerUuid);
         cmd.set("#ChatIndicator.Visible", chatEnabled);
         Vector3d viewerPos = PartyHealthTracker.getPosition(this.playerUuid);
         boolean showOnlyOnline = settings.showOnlyOnline;
         UUID[] allMembers = party.getAllPartyMembers();
         List<UUID> displayMembers = new ArrayList();
         UUID[] var10 = allMembers;
         int slot = allMembers.length;

         int memberIndex;
         UUID memberId;
         for(memberIndex = 0; memberIndex < slot; ++memberIndex) {
            memberId = var10[memberIndex];
            boolean isOnline = PartyHealthTracker.isOnline(memberId);
            if (!showOnlyOnline || isOnline) {
               displayMembers.add(memberId);
            }
         }

         int displayCount = displayMembers.size();

         for(slot = 1; slot <= 16; ++slot) {
            memberIndex = slot - 1;
            if (memberIndex < displayCount) {
               memberId = (UUID)displayMembers.get(memberIndex);
               String memberName = manager.getPlayerNameTracker().getPlayerName(memberId);
               boolean isOnline = PartyHealthTracker.isOnline(memberId);
               boolean isLeader = party.isLeader(memberId);
               int originalIndex = Arrays.asList(allMembers).indexOf(memberId);
               if (originalIndex < 0) {
                  originalIndex = memberIndex;
               }

               cmd.set("#Member" + slot + ".Visible", true);
               cmd.set("#Name" + slot + ".Text", memberName);
               String nameColor = isOnline ? SLOT_COLORS[originalIndex % SLOT_COLORS.length] : "#666666";
               cmd.set("#Name" + slot + ".Style.TextColor", nameColor);
               if (slot == 1) {
                  cmd.set("#LeaderIcon1.Visible", isLeader);
               }

               if (isOnline) {
                  PartyHealthTracker.PlayerStats stats = PartyHealthTracker.getStats(memberId);
                  float healthPercent = stats.maxHealth > 0.0F ? stats.currentHealth / stats.maxHealth : 0.0F;
                  cmd.set("#HealthGroup" + slot + ".Visible", true);
                  cmd.set("#HealthBar" + slot + ".Value", healthPercent);
                  cmd.set("#OfflineBox" + slot + ".Visible", false);
               } else {
                  cmd.set("#HealthGroup" + slot + ".Visible", false);
                  cmd.set("#OfflineBox" + slot + ".Visible", true);
               }

               this.setPingAndDistance(cmd, slot, memberId, isOnline, viewerPos);
            } else {
               cmd.set("#Member" + slot + ".Visible", false);
            }
         }

         cmd.set("#Row1.Visible", displayCount > 0);
         cmd.set("#Row2.Visible", displayCount > 2);
         cmd.set("#Row3.Visible", displayCount > 4);
         cmd.set("#Row4.Visible", displayCount > 6);
         cmd.set("#Row5.Visible", displayCount > 8);
         cmd.set("#Row6.Visible", displayCount > 10);
         cmd.set("#Row7.Visible", displayCount > 12);
         cmd.set("#Row8.Visible", displayCount > 14);
         cmd.set("#HintText.Visible", true);
      } else {
         this.hideAllMembers(cmd);
      }
   }

   private void hideAllMembers(UICommandBuilder cmd) {
      cmd.set("#PartyHeader.Visible", false);

      int row;
      for(row = 1; row <= 16; ++row) {
         cmd.set("#Member" + row + ".Visible", false);
      }

      for(row = 1; row <= 8; ++row) {
         cmd.set("#Row" + row + ".Visible", false);
      }

      cmd.set("#HintText.Visible", false);
   }

   private void setPingAndDistance(UICommandBuilder cmd, int slot, UUID memberId, boolean isOnline, Vector3d viewerPos) {
      boolean isSelf = memberId.equals(this.playerUuid);
      PlayerRef memberRef = isOnline ? Universe.get().getPlayer(memberId) : null;
      if (isOnline && memberRef != null && memberRef.isValid()) {
         double dx;
         try {
            PacketHandler handler = memberRef.getPacketHandler();
            if (handler != null) {
               dx = handler.getPingInfo(PongType.Raw).getPingMetricSet().getAverage(0);
               long pingMs = Math.round(dx / 1000.0D);
               cmd.set("#Ping" + slot + ".Text", pingMs + "ms");
               cmd.set("#Ping" + slot + ".Style.TextColor", this.getPingColor(pingMs));
            } else {
               this.setOfflinePingDisplay(cmd, slot);
            }
         } catch (Exception var17) {
            this.setOfflinePingDisplay(cmd, slot);
         }

         if (isSelf) {
            cmd.set("#Distance" + slot + ".Text", "");
         } else if (viewerPos != null) {
            Vector3d memberPos = PartyHealthTracker.getPosition(memberId);
            if (memberPos != null) {
               dx = viewerPos.getX() - memberPos.getX();
               double dy = viewerPos.getY() - memberPos.getY();
               double dz = viewerPos.getZ() - memberPos.getZ();
               double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
               cmd.set("#Distance" + slot + ".Text", Math.round(distance) + "m");
            } else {
               cmd.set("#Distance" + slot + ".Text", "-");
            }
         } else {
            cmd.set("#Distance" + slot + ".Text", "-");
         }

      } else {
         this.setOfflinePingDisplay(cmd, slot);
      }
   }

   private void setOfflinePingDisplay(UICommandBuilder cmd, int slot) {
      cmd.set("#Ping" + slot + ".Text", "-");
      cmd.set("#Ping" + slot + ".Style.TextColor", "#888888");
      cmd.set("#Distance" + slot + ".Text", "-");
   }

   private String getPingColor(long pingMs) {
      if (pingMs < 100L) {
         return "#88cc88";
      } else {
         return pingMs < 200L ? "#cccc44" : "#cc4444";
      }
   }
}
