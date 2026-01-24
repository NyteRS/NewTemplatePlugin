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

public class PartyHud extends CustomUIHud implements IPartyHud {
   private static final int MAX_NORMAL_MEMBERS = 8;
   private static final long PING_GOOD_THRESHOLD_MS = 100L;
   private static final long PING_MEDIUM_THRESHOLD_MS = 200L;
   private static final String PING_COLOR_GOOD = "#88cc88";
   private static final String PING_COLOR_MEDIUM = "#cccc44";
   private static final String PING_COLOR_BAD = "#cc4444";
   private static final String PING_COLOR_OFFLINE = "#888888";
   private static final double MICROS_TO_MS = 1000.0D;
   private ScheduledFuture<?> updateTask;
   private final UUID playerUuid;

   public PartyHud(@Nonnull PlayerRef playerRef) {
      super(playerRef);
      this.playerUuid = playerRef.getUuid();
   }

   protected void build(@Nonnull UICommandBuilder uiCommandBuilder) {
      uiCommandBuilder.append("Hud/PartyPro/PartyHud.ui");
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
      anchor.setWidth(Value.of(config.getHudWidth()));
      uiCommandBuilder.setObject("#PartyHud.Anchor", anchor);
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
         System.out.println("[PartyHud] REFRESH ERROR: " + var2.getMessage());
         var2.printStackTrace();
      }

   }

   private void updateContent(@Nonnull UICommandBuilder uiCommandBuilder) {
      PartyInfo party = PartyManager.getInstance().getPartyFromPlayer(this.playerUuid);
      PartyHudManager.HudSettings settings = PartyHudManager.getSettings(this.playerUuid);
      if (party != null && settings.enabled) {
         PartyManager manager = PartyManager.getInstance();
         uiCommandBuilder.set("#PartyHeader.Visible", true);
         uiCommandBuilder.set("#PartyName.Text", party.getName());
         boolean chatEnabled = PartyChatManager.getInstance().isPartyChatEnabled(this.playerUuid);
         uiCommandBuilder.set("#ChatIndicator.Visible", chatEnabled);
         Vector3d viewerPos = PartyHealthTracker.getPosition(this.playerUuid);
         boolean showOnlyOnline = settings.showOnlyOnline;
         UUID[] allMembers = party.getAllPartyMembers();
         List<UUID> displayMembers = new ArrayList();
         UUID[] var10 = allMembers;
         int memberIndex = allMembers.length;

         for(int var12 = 0; var12 < memberIndex; ++var12) {
            UUID memberId = var10[var12];
            boolean isOnline = PartyHealthTracker.isOnline(memberId);
            if (!showOnlyOnline || isOnline) {
               displayMembers.add(memberId);
            }
         }

         for(int slot = 1; slot <= 8; ++slot) {
            memberIndex = slot - 1;
            if (memberIndex < displayMembers.size()) {
               UUID memberId = (UUID)displayMembers.get(memberIndex);
               String memberName = manager.getPlayerNameTracker().getPlayerName(memberId);
               PartyHealthTracker.PlayerStats stats = PartyHealthTracker.getStats(memberId);
               boolean isOnline = PartyHealthTracker.isOnline(memberId);
               boolean isLeader = party.isLeader(memberId);
               uiCommandBuilder.set("#Member" + slot + ".Visible", true);
               uiCommandBuilder.set("#Name" + slot + ".Text", memberName);
               if (slot == 1) {
                  uiCommandBuilder.set("#LeaderIcon1.Visible", isLeader);
               }

               this.setMemberOnlineState(uiCommandBuilder, slot, isOnline, stats);
               this.setPingAndDistance(uiCommandBuilder, slot, memberId, isOnline, viewerPos);
            } else {
               uiCommandBuilder.set("#Member" + slot + ".Visible", false);
            }
         }

         uiCommandBuilder.set("#HintText.Visible", true);
      } else {
         this.hideAllMembers(uiCommandBuilder);
      }
   }

   private void hideAllMembers(UICommandBuilder builder) {
      builder.set("#PartyHeader.Visible", false);

      for(int i = 1; i <= 8; ++i) {
         builder.set("#Member" + i + ".Visible", false);
      }

      builder.set("#HintText.Visible", false);
   }

   private void setPingAndDistance(UICommandBuilder builder, int memberNum, UUID memberId, boolean isOnline, Vector3d viewerPos) {
      boolean isSelf = memberId.equals(this.playerUuid);
      PlayerRef memberRef = isOnline ? Universe.get().getPlayer(memberId) : null;
      if (isOnline && memberRef != null && memberRef.isValid()) {
         double dx;
         try {
            PacketHandler handler = memberRef.getPacketHandler();
            if (handler != null) {
               dx = handler.getPingInfo(PongType.Raw).getPingMetricSet().getAverage(0);
               long pingMs = Math.round(dx / 1000.0D);
               builder.set("#Ping" + memberNum + ".Text", pingMs + "ms");
               builder.set("#Ping" + memberNum + ".Style.TextColor", this.getPingColor(pingMs));
            } else {
               this.setOfflinePingDisplay(builder, memberNum);
            }
         } catch (Exception var17) {
            this.setOfflinePingDisplay(builder, memberNum);
         }

         if (isSelf) {
            builder.set("#Distance" + memberNum + ".Text", "");
         } else if (viewerPos != null) {
            Vector3d memberPos = PartyHealthTracker.getPosition(memberId);
            if (memberPos != null) {
               dx = viewerPos.getX() - memberPos.getX();
               double dy = viewerPos.getY() - memberPos.getY();
               double dz = viewerPos.getZ() - memberPos.getZ();
               double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
               builder.set("#Distance" + memberNum + ".Text", Math.round(distance) + "m");
            } else {
               builder.set("#Distance" + memberNum + ".Text", "-");
            }
         } else {
            builder.set("#Distance" + memberNum + ".Text", "-");
         }

      } else {
         this.setOfflinePingDisplay(builder, memberNum);
      }
   }

   private void setOfflinePingDisplay(UICommandBuilder builder, int memberNum) {
      builder.set("#Ping" + memberNum + ".Text", "-");
      builder.set("#Ping" + memberNum + ".Style.TextColor", "#888888");
      builder.set("#Distance" + memberNum + ".Text", "-");
   }

   private String getPingColor(long pingMs) {
      if (pingMs < 100L) {
         return "#88cc88";
      } else {
         return pingMs < 200L ? "#cccc44" : "#cc4444";
      }
   }

   private void setMemberOnlineState(UICommandBuilder builder, int memberNum, boolean isOnline, PartyHealthTracker.PlayerStats stats) {
      if (isOnline) {
         builder.set("#HpRow" + memberNum + ".Visible", true);
         builder.set("#HealthGroup" + memberNum + ".Visible", true);
         builder.set("#EnergyGroup" + memberNum + ".Visible", true);
         builder.set("#OfflineBox" + memberNum + ".Visible", false);
         this.setBars(builder, memberNum, stats);
      } else {
         builder.set("#HpRow" + memberNum + ".Visible", false);
         builder.set("#HealthGroup" + memberNum + ".Visible", false);
         builder.set("#EnergyGroup" + memberNum + ".Visible", false);
         builder.set("#OfflineBox" + memberNum + ".Visible", true);
      }

   }

   private void setBars(UICommandBuilder builder, int memberNum, PartyHealthTracker.PlayerStats stats) {
      float healthPercent = stats.maxHealth > 0.0F ? stats.currentHealth / stats.maxHealth : 0.0F;
      float energyPercent = stats.maxEnergy > 0.0F ? stats.currentEnergy / stats.maxEnergy : 0.0F;
      String var10001 = "#Hp" + memberNum + ".Text";
      int var10002 = (int)stats.currentHealth;
      builder.set(var10001, var10002 + " / " + (int)stats.maxHealth);
      if (stats.isCreative) {
         builder.set("#HealthBar" + memberNum + ".Visible", false);
         builder.set("#EnergyBar" + memberNum + ".Visible", false);
         builder.set("#HealthBarCreative" + memberNum + ".Visible", true);
         builder.set("#EnergyBarCreative" + memberNum + ".Visible", true);
         builder.set("#HealthBarCreative" + memberNum + ".Value", healthPercent);
         builder.set("#EnergyBarCreative" + memberNum + ".Value", energyPercent);
      } else {
         builder.set("#HealthBar" + memberNum + ".Visible", true);
         builder.set("#EnergyBar" + memberNum + ".Visible", true);
         builder.set("#HealthBarCreative" + memberNum + ".Visible", false);
         builder.set("#EnergyBarCreative" + memberNum + ".Visible", false);
         builder.set("#HealthBar" + memberNum + ".Value", healthPercent);
         builder.set("#EnergyBar" + memberNum + ".Value", energyPercent);
      }

   }
}
