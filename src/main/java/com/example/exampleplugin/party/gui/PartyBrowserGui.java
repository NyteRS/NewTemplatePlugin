package com.example.exampleplugin.party.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec.Builder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import com.example.exampleplugin.party.PartyPlugin;
import com.example.exampleplugin.party.config.PartyProConfig;
import com.example.exampleplugin.party.hud.IPartyHud;
import com.example.exampleplugin.party.hud.PartyHudManager;
import com.example.exampleplugin.party.lang.LanguageManager;
import com.example.exampleplugin.party.party.PartyInfo;
import com.example.exampleplugin.party.party.PartyInvite;
import com.example.exampleplugin.party.party.PartyManager;
import com.example.exampleplugin.party.party.PlayerNameTracker;
import com.example.exampleplugin.party.stats.PartyStats;
import com.example.exampleplugin.party.stats.PartyStatsManager;
import com.example.exampleplugin.party.stats.PartyStatsTracker;
import com.example.exampleplugin.party.stats.PlayerStats;
import com.example.exampleplugin.party.systems.CooldownManager;
import com.example.exampleplugin.party.systems.IdleTracker;
import com.example.exampleplugin.party.util.NotificationHelper;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class PartyBrowserGui extends InteractiveCustomUIPage<PartyBrowserGui.GuiData> {
   private static final int MAX_CHAT_MESSAGES = 50;
   private static final Map<UUID, List<PartyBrowserGui.ChatMessage>> partyChatHistory = new HashMap();
   private static final Map<UUID, PartyBrowserGui> openGuis = new HashMap();
   private final Map<String, PartyBrowserGui.GuiAction> actions = new HashMap();
   private int activeTab = 0;
   private String searchQuery = "";
   private int confirmingKickIndex = -1;
   private boolean confirmingDisband = false;
   private int confirmingTransferIndex = -1;
   private boolean isRenaming = false;
   private String renameInput = "";
   private String passwordInput = "";
   private UUID joiningPartyId = null;
   private String joinPasswordInput = "";
   private String chatInput = "";
   private int statsMemberIndex = -1;
   private int leaderboardCategory = 0;
   private Ref<EntityStore> lastRef;
   private Store<EntityStore> lastStore;

   public static void addChatMessage(UUID partyId, PartyBrowserGui.ChatMessage msg) {
      List<PartyBrowserGui.ChatMessage> messages = (List)partyChatHistory.computeIfAbsent(partyId, (k) -> {
         return new ArrayList();
      });
      messages.add(msg);
      if (messages.size() > 50) {
         messages.remove(0);
      }

      refreshChatForParty(partyId);
   }

   private static void refreshChatForParty(UUID partyId) {
      PartyManager manager = PartyManager.getInstance();
      Iterator var2 = openGuis.entrySet().iterator();

      while(var2.hasNext()) {
         Entry<UUID, PartyBrowserGui> entry = (Entry)var2.next();
         PartyBrowserGui gui = (PartyBrowserGui)entry.getValue();
         if (gui.activeTab == 4) {
            PartyInfo party = manager.getPartyFromPlayer((UUID)entry.getKey());
            if (party != null && party.getId().equals(partyId)) {
               gui.refreshGui();
            }
         }
      }

   }

   public void refreshGui() {
      if (this.lastRef != null && this.lastStore != null) {
         UICommandBuilder commandBuilder = new UICommandBuilder();
         UIEventBuilder eventBuilder = new UIEventBuilder();
         this.build(this.lastRef, commandBuilder, eventBuilder, this.lastStore);
         this.sendUpdate(commandBuilder, eventBuilder, true);
      }

   }

   public static List<PartyBrowserGui.ChatMessage> getChatHistory(UUID partyId) {
      return (List)partyChatHistory.getOrDefault(partyId, new ArrayList());
   }

   public static void clearChatHistory(UUID partyId) {
      partyChatHistory.remove(partyId);
   }

   public static void registerGui(UUID playerUuid, PartyBrowserGui gui) {
      openGuis.put(playerUuid, gui);
   }

   public static void unregisterGui(UUID playerUuid) {
      openGuis.remove(playerUuid);
   }

   public PartyBrowserGui(@NonNullDecl PlayerRef playerRef) {
      super(playerRef, CustomPageLifetime.CanDismiss, PartyBrowserGui.GuiData.CODEC);
      registerGui(playerRef.getUuid(), this);
      this.initActions();
   }

   private void initActions() {
      this.actions.put("Kick", this::handleKick);
      this.actions.put("CancelKick", (ctx) -> {
         this.confirmingKickIndex = -1;
      });
      this.actions.put("Transfer", this::handleTransfer);
      this.actions.put("CancelTransfer", (ctx) -> {
         this.confirmingTransferIndex = -1;
      });
      this.actions.put("Disband", this::handleDisband);
      this.actions.put("CancelDisband", (ctx) -> {
         this.confirmingDisband = false;
      });
      this.actions.put("TogglePvp", this::handleTogglePvp);
      this.actions.put("SizeUp", this::handleSizeUp);
      this.actions.put("SizeDown", this::handleSizeDown);
      this.actions.put("TogglePublic", this::handleTogglePublic);
      this.actions.put("SavePassword", this::handleSavePassword);
      this.actions.put("Leave", this::handleLeave);
      this.actions.put("JoinParty", this::handleJoinParty);
      this.actions.put("ConfirmJoinWithPassword", this::handleConfirmJoinWithPassword);
      this.actions.put("CancelJoin", (ctx) -> {
         this.joiningPartyId = null;
         this.joinPasswordInput = "";
      });
      this.actions.put("CreateParty", this::handleCreateParty);
      this.actions.put("AcceptInvite", this::handleAcceptInvite);
      this.actions.put("DeclineInvite", (ctx) -> {
         ctx.getManager().declineInvite(this.playerRef.getUuid());
      });
      this.actions.put("InvitePlayer", this::handleInvitePlayer);
      this.actions.put("StartRename", this::handleStartRename);
      this.actions.put("CancelRename", (ctx) -> {
         this.isRenaming = false;
         this.renameInput = "";
      });
      this.actions.put("SaveRename", this::handleSaveRename);
      this.actions.put("Teleport", this::handleTeleport);
      this.actions.put("TeleportAll", this::handleTeleportAll);
      this.actions.put("ToggleHudEnabled", this::handleToggleHudEnabled);
      this.actions.put("ToggleShowOnlyOnline", this::handleToggleShowOnlyOnline);
      this.actions.put("SetHudSide", this::handleSetHudSide);
      this.actions.put("SetHudMode", this::handleSetHudMode);
      this.actions.put("SetInviteMode", this::handleSetInviteMode);
      this.actions.put("SendChat", this::handleSendChat);
      this.actions.put("StatsPrevMember", this::handleStatsPrevMember);
      this.actions.put("StatsNextMember", this::handleStatsNextMember);
      this.actions.put("LbCategory", (ctx) -> {
         this.leaderboardCategory = ctx.getIntArg(1);
      });
   }

   public void close() {
      unregisterGui(this.playerRef.getUuid());
      super.close();
   }

   public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store, @NonNullDecl PartyBrowserGui.GuiData data) {
      super.handleDataEvent(ref, store, data);
      if (data.close != null) {
         this.close();
      } else {
         boolean needsRefresh = false;
         if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery;
            needsRefresh = true;
         }

         if (data.renameInput != null) {
            this.renameInput = data.renameInput;
         }

         if (data.passwordInput != null) {
            this.passwordInput = data.passwordInput;
         }

         if (data.joinPasswordInput != null) {
            this.joinPasswordInput = data.joinPasswordInput;
         }

         if (data.chatInput != null) {
            this.chatInput = data.chatInput;
         }

         if (data.tab != null) {
            this.activeTab = Integer.parseInt(data.tab);
            this.searchQuery = "";
            this.confirmingKickIndex = -1;
            this.confirmingDisband = false;
            this.confirmingTransferIndex = -1;
            this.isRenaming = false;
            this.renameInput = "";
            this.passwordInput = "";
            needsRefresh = true;
         }

         if (data.action != null) {
            this.handleAction(data.action, ref, store);
            needsRefresh = true;
         }

         if (needsRefresh) {
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            this.build(ref, commandBuilder, eventBuilder, store);
            this.sendUpdate(commandBuilder, eventBuilder, true);
         }

      }
   }

   private void handleAction(String action, Ref<EntityStore> ref, Store<EntityStore> store) {
      String[] parts = action.split(":");
      String cmd = parts[0];
      PartyBrowserGui.GuiAction handler = (PartyBrowserGui.GuiAction)this.actions.get(cmd);
      if (handler != null) {
         try {
            PartyBrowserGui.ActionContext ctx = new PartyBrowserGui.ActionContext(this, parts, ref, store);
            handler.execute(ctx);
         } catch (IllegalStateException var8) {
         }
      }

   }

   private void handleKick(PartyBrowserGui.ActionContext ctx) {
      if (ctx.isLeader() && ctx.getParty() != null) {
         int index = ctx.getIntArg(1);
         if (this.confirmingKickIndex == index) {
            UUID[] members = ctx.getParty().getMembers();
            if (index >= 0 && index < members.length) {
               ctx.getManager().kickPlayer(members[index], ctx.getParty());
            }

            this.confirmingKickIndex = -1;
         } else {
            this.confirmingKickIndex = index;
         }

      }
   }

   private void handleTransfer(PartyBrowserGui.ActionContext ctx) {
      if (ctx.isLeader() && ctx.getParty() != null) {
         int index = ctx.getIntArg(1);
         if (this.confirmingTransferIndex == index) {
            UUID[] members = ctx.getParty().getMembers();
            if (index >= 0 && index < members.length) {
               UUID newLeader = members[index];
               UUID oldLeader = ctx.getParty().getLeader();
               ctx.getParty().removeMember(newLeader);
               ctx.getParty().setLeader(newLeader);
               ctx.getParty().addMember(oldLeader);
               ctx.getManager().markDirty();
            }

            this.confirmingTransferIndex = -1;
         } else {
            this.confirmingTransferIndex = index;
         }

      }
   }

   private void handleDisband(PartyBrowserGui.ActionContext ctx) {
      if (ctx.isLeader() && ctx.getParty() != null) {
         if (this.confirmingDisband) {
            ctx.getManager().disbandParty(ctx.getParty());
            NotificationHelper.sendSuccess(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.party_disbanded"));
            this.close();
         } else {
            this.confirmingDisband = true;
         }

      }
   }

   private void handleTogglePvp(PartyBrowserGui.ActionContext ctx) {
      if (ctx.isLeader() && ctx.getParty() != null) {
         ctx.getParty().setPvpEnabled(!ctx.getParty().isPvpEnabled());
         ctx.getManager().markDirty();
      }
   }

   private void handleSizeUp(PartyBrowserGui.ActionContext ctx) {
      if (ctx.isLeader() && ctx.getParty() != null) {
         int currentMax = ctx.getParty().getMaxSize();
         int serverMax = PartyProConfig.getInstance().getMaxPartySize();
         if (currentMax < serverMax) {
            ctx.getParty().setMaxSize(currentMax + 1);
            ctx.getManager().markDirty();
         }

      }
   }

   private void handleSizeDown(PartyBrowserGui.ActionContext ctx) {
      if (ctx.isLeader() && ctx.getParty() != null) {
         int currentMax = ctx.getParty().getMaxSize();
         int currentMembers = ctx.getParty().getTotalMemberCount();
         if (currentMax > 2 && currentMax > currentMembers) {
            ctx.getParty().setMaxSize(currentMax - 1);
            ctx.getManager().markDirty();
         }

      }
   }

   private void handleTogglePublic(PartyBrowserGui.ActionContext ctx) {
      if (ctx.isLeader() && ctx.getParty() != null) {
         ctx.getParty().setPublic(!ctx.getParty().isPublic());
         ctx.getManager().markDirty();
      }
   }

   private void handleSavePassword(PartyBrowserGui.ActionContext ctx) {
      if (ctx.isLeader() && ctx.getParty() != null) {
         ctx.getParty().setPassword(this.passwordInput);
         ctx.getManager().markDirty();
         if (this.passwordInput.isEmpty()) {
            NotificationHelper.sendSuccess(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.password_removed"));
         } else {
            NotificationHelper.sendSuccess(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.password_set"));
         }

         this.passwordInput = "";
      }
   }

   private void handleLeave(PartyBrowserGui.ActionContext ctx) {
      if (ctx.getParty() != null) {
         ctx.getManager().leaveParty(this.playerRef, ctx.getParty());
      }
   }

   private void handleJoinParty(PartyBrowserGui.ActionContext ctx) {
      if (ctx.getParty() == null) {
         UUID partyId = ctx.getUuidArg(1);
         PartyInfo targetParty = ctx.getManager().getPartyById(partyId);
         if (targetParty == null) {
            NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.party_not_found"));
         } else if (!targetParty.isPublic() && !targetParty.hasPassword()) {
            NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.party_private"));
         } else if (targetParty.hasPassword()) {
            this.joiningPartyId = partyId;
            this.joinPasswordInput = "";
         } else {
            PartyManager.JoinResult result = ctx.getManager().joinParty(this.playerRef.getUuid(), targetParty);
            switch(result) {
            case SUCCESS:
               NotificationHelper.sendSuccess(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.party_joined", targetParty.getName()));
               PartyHudManager.refreshHud(this.playerRef.getUuid());
               break;
            case PARTY_FULL:
               NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.party_full"));
               break;
            case ALREADY_IN_PARTY:
            case ALREADY_IN_OTHER_PARTY:
               NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.already_in_party"));
            }

         }
      }
   }

   private void handleConfirmJoinWithPassword(PartyBrowserGui.ActionContext ctx) {
      if (ctx.getParty() == null && this.joiningPartyId != null) {
         PartyInfo targetParty = ctx.getManager().getPartyById(this.joiningPartyId);
         if (targetParty == null) {
            NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.party_not_found"));
            this.joiningPartyId = null;
         } else if (!targetParty.checkPassword(this.joinPasswordInput)) {
            NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.wrong_password"));
         } else {
            PartyManager.JoinResult result = ctx.getManager().joinParty(this.playerRef.getUuid(), targetParty);
            switch(result) {
            case SUCCESS:
               NotificationHelper.sendSuccess(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.party_joined", targetParty.getName()));
               PartyHudManager.refreshHud(this.playerRef.getUuid());
               break;
            case PARTY_FULL:
               NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.party_full"));
               break;
            case ALREADY_IN_PARTY:
            case ALREADY_IN_OTHER_PARTY:
               NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.already_in_party"));
            }

            this.joiningPartyId = null;
            this.joinPasswordInput = "";
         }
      }
   }

   private void handleCreateParty(PartyBrowserGui.ActionContext ctx) {
      if (ctx.getParty() == null) {
         String playerName = ctx.getManager().getPlayerNameTracker().getPlayerName(this.playerRef.getUuid());
         String partyName = playerName + "'s Party";
         ctx.getManager().createParty(this.playerRef, partyName);
         NotificationHelper.sendSuccess(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.party_created"));
      }
   }

   private void handleAcceptInvite(PartyBrowserGui.ActionContext ctx) {
      PartyInvite result = ctx.getManager().acceptInvite(this.playerRef);
      if (result != null) {
         PartyInfo party = ctx.getManager().getPartyById(result.partyId());
         if (party != null) {
            NotificationHelper.sendSuccess(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.party_joined", party.getName()));
            PartyStatsTracker.getInstance().onMemberJoined(party.getId());
            PartyHudManager.refreshHud(this.playerRef.getUuid());
         }
      }

   }

   private void handleInvitePlayer(PartyBrowserGui.ActionContext ctx) {
      PartyInfo myParty = ctx.getParty();
      if (ctx.isLeader() && myParty != null && myParty.canAddMember()) {
         int cooldownRemaining = CooldownManager.getInstance().getInviteCooldownRemaining(this.playerRef.getUuid(), PartyProConfig.getInstance().getInviteCooldown());
         if (cooldownRemaining > 0) {
            NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.cooldown_invite", cooldownRemaining));
         } else {
            UUID targetPlayerId = ctx.getUuidArg(1);
            if (ctx.getManager().getPartyFromPlayer(targetPlayerId) == null) {
               PlayerRef targetPlayerRef = Universe.get().getPlayer(targetPlayerId);
               if (targetPlayerRef != null && targetPlayerRef.isValid()) {
                  String targetName = ctx.getManager().getPlayerNameTracker().getPlayerName(targetPlayerId);
                  PartyHudManager.HudSettings invitedSettings = PartyHudManager.getSettings(targetPlayerId);
                  if (invitedSettings.inviteMode == PartyHudManager.InviteMode.AUTO_DECLINE) {
                     NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.invite_auto_declined", targetName));
                  } else {
                     CooldownManager.getInstance().setInviteCooldown(this.playerRef.getUuid());
                     if (invitedSettings.inviteMode == PartyHudManager.InviteMode.AUTO_ACCEPT) {
                        PartyManager.JoinResult joinResult = ctx.getManager().joinParty(targetPlayerId, myParty);
                        if (joinResult == PartyManager.JoinResult.SUCCESS) {
                           NotificationHelper.sendSuccess(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.invite_sent", targetName));
                           NotificationHelper.sendSuccess(targetPlayerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.party_joined", myParty.getName()));
                           PartyHudManager.refreshHud(targetPlayerId);
                        }

                     } else {
                        ctx.getManager().invitePlayerToParty(targetPlayerRef, myParty, this.playerRef);
                        NotificationHelper.sendSuccess(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.invite_sent", targetName));
                        if (PartyProConfig.getInstance().isShowInvitePopup()) {
                           PartyInvite invite = ctx.getManager().getPendingInvite(targetPlayerId);
                           if (invite != null) {
                              Ref targetRef = targetPlayerRef.getReference();
                              if (targetRef != null && targetRef.isValid()) {
                                 try {
                                    Store targetStore = targetRef.getStore();
                                    Player targetPlayer = (Player)targetStore.getComponent(targetRef, Player.getComponentType());
                                    if (targetPlayer != null) {
                                       if (IdleTracker.getInstance().isIdleLongEnough(targetPlayerId)) {
                                          World targetWorld = targetPlayer.getWorld();
                                          if (targetWorld != null) {
                                             targetWorld.execute(() -> {
                                                if (targetRef.isValid()) {
                                                   PartyInviteGui inviteGui = new PartyInviteGui(targetPlayerRef, invite, myParty);
                                                   targetPlayer.getPageManager().openCustomPage(targetRef, targetStore, inviteGui);
                                                }

                                             });
                                          }
                                       } else {
                                          String senderName = ctx.getManager().getPlayerNameTracker().getPlayerName(this.playerRef.getUuid());
                                          NotificationHelper.sendInfo(targetPlayerRef, LanguageManager.getInstance().get("title.party_invite"), LanguageManager.getInstance().get("notify.invite_received", senderName, myParty.getName()));
                                       }
                                    }
                                 } catch (IllegalStateException var13) {
                                 }
                              }
                           }
                        } else {
                           String senderName = ctx.getManager().getPlayerNameTracker().getPlayerName(this.playerRef.getUuid());
                           NotificationHelper.sendInfo(targetPlayerRef, LanguageManager.getInstance().get("title.party_invite"), LanguageManager.getInstance().get("notify.invite_received", senderName, myParty.getName()));
                        }

                     }
                  }
               } else {
                  NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.player_not_found"));
               }
            }
         }
      }
   }

   private void handleStartRename(PartyBrowserGui.ActionContext ctx) {
      if (ctx.isLeader() && ctx.getParty() != null) {
         this.isRenaming = true;
         this.renameInput = ctx.getParty().getName();
      }
   }

   private void handleSaveRename(PartyBrowserGui.ActionContext ctx) {
      if (ctx.isLeader() && ctx.getParty() != null) {
         if (!this.renameInput.isEmpty() && this.renameInput.length() <= 32) {
            ctx.getParty().setName(this.renameInput);
            ctx.getManager().markDirty();
         }

         this.isRenaming = false;
         this.renameInput = "";
      }
   }

   private void handleTeleport(PartyBrowserGui.ActionContext ctx) {
      if (ctx.getParty() != null && PartyProConfig.getInstance().isTeleportEnabled()) {
         int index = ctx.getIntArg(1);
         UUID[] members = ctx.getParty().getMembers();
         if (index >= 0 && index < members.length) {
            this.teleportToPlayer(members[index], ctx.getRef(), ctx.getStore(), ctx.getManager());
         }

      }
   }

   private void handleTeleportAll(PartyBrowserGui.ActionContext ctx) {
      if (ctx.getParty() != null && PartyProConfig.getInstance().isTeleportEnabled()) {
         int index = ctx.getIntArg(1);
         UUID[] allMembers = ctx.getParty().getAllPartyMembers();
         if (index >= 0 && index < allMembers.length) {
            UUID targetPlayerId = allMembers[index];
            if (!targetPlayerId.equals(this.playerRef.getUuid())) {
               this.teleportToPlayer(targetPlayerId, ctx.getRef(), ctx.getStore(), ctx.getManager());
            }
         }

      }
   }

   private void handleToggleHudEnabled(PartyBrowserGui.ActionContext ctx) {
      PartyHudManager.HudSettings settings = PartyHudManager.getSettings(this.playerRef.getUuid());
      settings.enabled = !settings.enabled;
      PartyHudManager.setSettings(this.playerRef.getUuid(), settings);
   }

   private void handleToggleShowOnlyOnline(PartyBrowserGui.ActionContext ctx) {
      PartyHudManager.HudSettings settings = PartyHudManager.getSettings(this.playerRef.getUuid());
      settings.showOnlyOnline = !settings.showOnlyOnline;
      PartyHudManager.setSettings(this.playerRef.getUuid(), settings);
   }

   private void handleSetHudSide(PartyBrowserGui.ActionContext ctx) {
      PartyHudManager.HudSettings settings = PartyHudManager.getSettings(this.playerRef.getUuid());
      String side = ctx.getStringArg(1);
      settings.setHudSide(side);
      PartyHudManager.setSettings(this.playerRef.getUuid(), settings);
      IPartyHud hud = PartyHudManager.getHud(this.playerRef.getUuid());
      if (hud != null) {
         hud.stopAutoUpdate();
         PartyHudManager.unregisterHud(this.playerRef.getUuid());
         PartyPlugin.getInstance().createHudForPlayer(this.playerRef);
      }

   }

   private void handleSetHudMode(PartyBrowserGui.ActionContext ctx) {
      PartyHudManager.HudSettings settings = PartyHudManager.getSettings(this.playerRef.getUuid());
      String modeStr = ctx.getStringArg(1);
      settings.hudMode = PartyHudManager.HudMode.valueOf(modeStr);
      PartyHudManager.setSettings(this.playerRef.getUuid(), settings);
      PartyHudManager.unregisterHud(this.playerRef.getUuid());
      PartyPlugin.getInstance().createHudForPlayer(this.playerRef);
   }

   private void handleSetInviteMode(PartyBrowserGui.ActionContext ctx) {
      PartyHudManager.HudSettings settings = PartyHudManager.getSettings(this.playerRef.getUuid());
      String modeStr = ctx.getStringArg(1);
      settings.inviteMode = PartyHudManager.InviteMode.valueOf(modeStr);
      PartyHudManager.setSettings(this.playerRef.getUuid(), settings);
   }

   private void handleSendChat(PartyBrowserGui.ActionContext ctx) {
      if (ctx.getParty() != null) {
         if (this.chatInput != null && !this.chatInput.trim().isEmpty()) {
            String senderName = ctx.getManager().getPlayerNameTracker().getPlayerName(this.playerRef.getUuid());
            String message = this.chatInput.trim();
            PartyBrowserGui.ChatMessage chatMsg = new PartyBrowserGui.ChatMessage(this.playerRef.getUuid(), senderName, message);
            addChatMessage(ctx.getParty().getId(), chatMsg);
            UUID[] allMembers = ctx.getParty().getAllPartyMembers();
            UUID[] var6 = allMembers;
            int var7 = allMembers.length;

            for(int var8 = 0; var8 < var7; ++var8) {
               UUID memberId = var6[var8];
               PlayerRef memberRef = Universe.get().getPlayer(memberId);
               if (memberRef != null && memberRef.isValid()) {
                  memberRef.sendMessage(Message.join(new Message[]{Message.raw("[Party] ").color(Color.CYAN), Message.raw(senderName).color(Color.GREEN), Message.raw(": ").color(Color.GRAY), Message.raw(message).color(Color.WHITE)}));
               }
            }

            this.chatInput = "";
         }
      }
   }

   private void handleStatsPrevMember(PartyBrowserGui.ActionContext ctx) {
      if (ctx.getParty() != null) {
         int memberCount = ctx.getParty().getTotalMemberCount();
         --this.statsMemberIndex;
         if (this.statsMemberIndex < -1) {
            this.statsMemberIndex = memberCount - 1;
         }

      }
   }

   private void handleStatsNextMember(PartyBrowserGui.ActionContext ctx) {
      if (ctx.getParty() != null) {
         int memberCount = ctx.getParty().getTotalMemberCount();
         ++this.statsMemberIndex;
         if (this.statsMemberIndex >= memberCount) {
            this.statsMemberIndex = -1;
         }

      }
   }

   private void teleportToPlayer(UUID targetPlayerId, Ref<EntityStore> ref, Store<EntityStore> store, PartyManager manager) {
      int cooldownRemaining = CooldownManager.getInstance().getTeleportCooldownRemaining(this.playerRef.getUuid(), PartyProConfig.getInstance().getTeleportCooldown());
      if (cooldownRemaining > 0) {
         NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.cooldown_teleport", cooldownRemaining));
      } else if (!PartyProConfig.getInstance().isTeleportEnabled()) {
         NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.teleport_disabled"));
      } else {
         Ref myRef = this.playerRef.getReference();
         if (myRef != null && myRef.isValid()) {
            Store myStore = myRef.getStore();
            World world = ((EntityStore)myStore.getExternalData()).getWorld();
            world.execute(() -> {
               try {
                  Entity targetEntity = world.getEntity(targetPlayerId);
                  if (targetEntity == null) {
                     NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.player_not_found"));
                     return;
                  }

                  Ref targetRef = targetEntity.getReference();
                  if (targetRef == null || !targetRef.isValid()) {
                     NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.target_not_in_world"));
                     return;
                  }

                  Store targetStore = targetRef.getStore();
                  TransformComponent targetTransform = (TransformComponent)targetStore.getComponent(targetRef, TransformComponent.getComponentType());
                  if (targetTransform == null) {
                     NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.target_not_in_world"));
                     return;
                  }

                  Vector3d pos = targetTransform.getPosition();
                  Vector3f rot = targetTransform.getRotation();
                  Teleport teleport = new Teleport(world, pos, rot);
                  myStore.addComponent(myRef, Teleport.getComponentType(), teleport);
                  String targetName = manager.getPlayerNameTracker().getPlayerName(targetPlayerId);
                  CooldownManager.getInstance().setTeleportCooldown(this.playerRef.getUuid());
                  PartyStatsTracker.getInstance().onTeleportUsed(this.playerRef.getUuid());
                  NotificationHelper.sendSuccess(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("success.teleporting", targetName));
               } catch (Exception var14) {
                  NotificationHelper.sendError(this.playerRef, LanguageManager.getInstance().get("title.party"), LanguageManager.getInstance().get("error.target_not_in_world"));
               }

            });
            this.close();
         }
      }
   }

   public void build(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl UICommandBuilder cmd, @NonNullDecl UIEventBuilder evt, @NonNullDecl Store<EntityStore> store) {
      this.lastRef = ref;
      this.lastStore = store;
      cmd.append("Pages/PartyPro/PartyBrowser.ui");
      PartyManager manager = PartyManager.getInstance();
      PartyInfo myParty = manager.getPartyFromPlayer(this.playerRef.getUuid());
      LanguageManager lang = LanguageManager.getInstance();
      cmd.set("#MyPartyTab.Text", lang.get("gui.tab_my_party"));
      cmd.set("#PartiesTab.Text", lang.get("gui.tab_parties"));
      cmd.set("#InvitesTab.Text", lang.get("gui.tab_invites"));
      cmd.set("#PlayersTab.Text", lang.get("gui.tab_players"));
      cmd.set("#ChatTab.Text", lang.get("gui.tab_chat"));
      cmd.set("#StatsTab.Text", lang.get("gui.tab_stats"));
      cmd.set("#SettingsTab.Text", lang.get("gui.tab_settings"));
      cmd.set("#CloseButton.Text", lang.get("gui.btn_close"));
      cmd.set("#CreatePartyButton.Text", lang.get("gui.btn_create_party"));
      cmd.set("#RenameButton.Text", lang.get("gui.btn_rename"));
      cmd.set("#SaveNameButton.Text", lang.get("gui.btn_save"));
      cmd.set("#CancelRenameButton.Text", lang.get("gui.btn_cancel"));
      cmd.set("#SavePasswordButton.Text", lang.get("gui.btn_save"));
      cmd.set("#LeaveButton.Text", lang.get("gui.btn_leave"));
      cmd.set("#NoPartiesLabel.Text", lang.get("gui.no_public_parties"));
      cmd.set("#NoInvitesLabel.Text", lang.get("gui.no_pending_invites"));
      cmd.set("#NoPlayersLabel.Text", lang.get("gui.no_players_found"));
      cmd.set("#SendChatButton.Text", lang.get("gui.btn_send"));
      cmd.set("#ConfirmJoinButton.Text", lang.get("gui.btn_join"));
      cmd.set("#CancelJoinButton.Text", lang.get("gui.btn_cancel"));
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Close", "true"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#MyPartyTab", EventData.of("Tab", "0"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#PartiesTab", EventData.of("Tab", "1"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#InvitesTab", EventData.of("Tab", "2"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#PlayersTab", EventData.of("Tab", "3"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#ChatTab", EventData.of("Tab", "4"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#StatsTab", EventData.of("Tab", "5"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#SettingsTab", EventData.of("Tab", "7"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#LeaderboardTab", EventData.of("Tab", "6"), false);
      cmd.set("#MyPartyTab.Disabled", this.activeTab == 0);
      cmd.set("#PartiesTab.Disabled", this.activeTab == 1);
      cmd.set("#InvitesTab.Disabled", this.activeTab == 2);
      cmd.set("#PlayersTab.Disabled", this.activeTab == 3);
      cmd.set("#ChatTab.Disabled", this.activeTab == 4);
      cmd.set("#StatsTab.Disabled", this.activeTab == 5);
      cmd.set("#LeaderboardTab.Disabled", this.activeTab == 6);
      cmd.set("#SettingsTab.Disabled", this.activeTab == 7);
      evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput", EventData.of("@SearchQuery", "#SearchInput.Value"), false);
      cmd.set("#SearchInput.Value", this.searchQuery);
      cmd.set("#SearchContainer.Visible", this.activeTab == 3);
      evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PartiesSearchInput", EventData.of("@SearchQuery", "#PartiesSearchInput.Value"), false);
      cmd.set("#PartiesSearchInput.Value", this.searchQuery);
      cmd.set("#PartiesSearchContainer.Visible", this.activeTab == 1);
      cmd.set("#MyPartyContent.Visible", this.activeTab == 0);
      cmd.set("#PartiesContent.Visible", this.activeTab == 1);
      cmd.set("#InvitesContent.Visible", this.activeTab == 2);
      cmd.set("#PlayersContent.Visible", this.activeTab == 3);
      cmd.set("#ChatContent.Visible", this.activeTab == 4);
      cmd.set("#StatsContent.Visible", this.activeTab == 5);
      cmd.set("#LeaderboardContent.Visible", this.activeTab == 6);
      cmd.set("#SettingsContent.Visible", this.activeTab == 7);
      boolean showPasswordOverlay = this.joiningPartyId != null;
      cmd.set("#PasswordOverlay.Visible", showPasswordOverlay);
      if (showPasswordOverlay) {
         PartyInfo joiningParty = manager.getPartyById(this.joiningPartyId);
         if (joiningParty != null) {
            cmd.set("#JoinPartyName.Text", joiningParty.getName());
         }

         cmd.set("#JoinPasswordInput.Value", this.joinPasswordInput);
         evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#JoinPasswordInput", EventData.of("@JoinPasswordInput", "#JoinPasswordInput.Value"), false);
         evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmJoinButton", EventData.of("Action", "ConfirmJoinWithPassword:0"), false);
         evt.addEventBinding(CustomUIEventBindingType.Activating, "#CancelJoinButton", EventData.of("Action", "CancelJoin:0"), false);
         evt.addEventBinding(CustomUIEventBindingType.Activating, "#PasswordBlocker", EventData.of("Action", "CancelJoin:0"), false);
      }

      switch(this.activeTab) {
      case 0:
         this.buildMyPartyTab(cmd, evt, myParty);
         break;
      case 1:
         this.buildPartiesTab(cmd, evt, myParty);
         break;
      case 2:
         this.buildInvitesTab(cmd, evt);
         break;
      case 3:
         this.buildPlayersList(cmd, evt, myParty);
         break;
      case 4:
         this.buildChatTab(cmd, evt, myParty);
         break;
      case 5:
         this.buildStatsTab(cmd, evt, myParty);
         break;
      case 6:
         this.buildLeaderboardTab(cmd, evt);
         break;
      case 7:
         this.buildSettingsTab(cmd, evt);
      }

   }

   private void buildMyPartyTab(UICommandBuilder cmd, UIEventBuilder evt, PartyInfo myParty) {
      PlayerNameTracker nameTracker = PartyManager.getInstance().getPlayerNameTracker();
      if (myParty == null) {
         cmd.set("#NoPartySection.Visible", true);
         cmd.set("#PartyDetails.Visible", false);
         evt.addEventBinding(CustomUIEventBindingType.Activating, "#CreatePartyButton", EventData.of("Action", "CreateParty:0"), false);
      } else {
         cmd.set("#NoPartySection.Visible", false);
         cmd.set("#PartyDetails.Visible", true);
         boolean isLeader = myParty.isLeader(this.playerRef.getUuid());
         if (this.isRenaming) {
            cmd.set("#MyPartyName.Visible", false);
            cmd.set("#RenameInput.Visible", true);
            cmd.set("#RenameInput.Value", this.renameInput);
            cmd.set("#RenameButton.Visible", false);
            cmd.set("#SaveNameButton.Visible", true);
            cmd.set("#CancelRenameButton.Visible", true);
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#RenameInput", EventData.of("@RenameInput", "#RenameInput.Value"), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#SaveNameButton", EventData.of("Action", "SaveRename:0"), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#CancelRenameButton", EventData.of("Action", "CancelRename:0"), false);
         } else {
            cmd.set("#MyPartyName.Visible", true);
            cmd.set("#MyPartyName.Text", myParty.getName());
            cmd.set("#RenameInput.Visible", false);
            cmd.set("#RenameButton.Visible", isLeader);
            cmd.set("#SaveNameButton.Visible", false);
            cmd.set("#CancelRenameButton.Visible", false);
            if (isLeader) {
               evt.addEventBinding(CustomUIEventBindingType.Activating, "#RenameButton", EventData.of("Action", "StartRename:0"), false);
            }
         }

         int var10002 = myParty.getTotalMemberCount();
         cmd.set("#MyPartySize.Text", var10002 + "/" + myParty.getMaxSize());
         cmd.set("#MyPartyLeader.Text", nameTracker.getPlayerName(myParty.getLeader()));
         cmd.set("#DisbandButton.Visible", isLeader);
         cmd.set("#LeaveButton.Visible", !isLeader);
         if (isLeader) {
            if (this.confirmingDisband) {
               cmd.set("#DisbandButton.Text", LanguageManager.getInstance().get("gui.btn_confirm"));
               evt.addEventBinding(CustomUIEventBindingType.Activating, "#DisbandButton", EventData.of("Action", "Disband:1"), false);
               evt.addEventBinding(CustomUIEventBindingType.MouseExited, "#DisbandButton", EventData.of("Action", "CancelDisband:0"), false);
            } else {
               cmd.set("#DisbandButton.Text", LanguageManager.getInstance().get("gui.btn_disband"));
               evt.addEventBinding(CustomUIEventBindingType.Activating, "#DisbandButton", EventData.of("Action", "Disband:0"), false);
            }
         }

         evt.addEventBinding(CustomUIEventBindingType.Activating, "#LeaveButton", EventData.of("Action", "Leave:0"), false);
         cmd.set("#PartySettingsSection.Visible", isLeader);
         if (isLeader) {
            int serverMax = PartyProConfig.getInstance().getMaxPartySize();
            cmd.set("#PartySizeValue.Text", String.valueOf(myParty.getMaxSize()));
            cmd.set("#PartySizeMax.Text", "/ " + serverMax);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#SizeUp", EventData.of("Action", "SizeUp:0"), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#SizeDown", EventData.of("Action", "SizeDown:0"), false);
            cmd.set("#PvpSetting #CheckBox.Value", myParty.isPvpEnabled());
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PvpSetting #CheckBox", EventData.of("Action", "TogglePvp:0"), false);
            cmd.set("#PublicSetting #CheckBox.Value", myParty.isPublic());
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PublicSetting #CheckBox", EventData.of("Action", "TogglePublic:0"), false);
            cmd.set("#PasswordInput.Value", this.passwordInput.isEmpty() ? myParty.getPassword() : this.passwordInput);
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PasswordInput", EventData.of("@PasswordInput", "#PasswordInput.Value"), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#SavePasswordButton", EventData.of("Action", "SavePassword:0"), false);
         }

         cmd.clear("#MyMemberList");
         UUID[] allMembers = myParty.getAllPartyMembers();
         UUID myUuid = this.playerRef.getUuid();

         for(int i = 0; i < allMembers.length; ++i) {
            UUID memberUuid = allMembers[i];
            boolean isMemberLeader = myParty.isLeader(memberUuid);
            boolean isMe = memberUuid.equals(myUuid);
            cmd.append("#MyMemberList", "Pages/PartyPro/MemberEntry.ui");
            String memberName = nameTracker.getPlayerName(memberUuid);
            cmd.set("#MyMemberList[" + i + "] #MemberName.Text", memberName);
            boolean showTeleport = !isMe && PartyProConfig.getInstance().isTeleportEnabled();
            cmd.set("#MyMemberList[" + i + "] #TeleportButton.Visible", showTeleport);
            if (showTeleport) {
               evt.addEventBinding(CustomUIEventBindingType.Activating, "#MyMemberList[" + i + "] #TeleportButton", EventData.of("Action", "TeleportAll:" + i), false);
            }

            if (isLeader && !isMemberLeader && !isMe) {
               int memberIndex = i - 1;
               cmd.set("#MyMemberList[" + i + "] #KickButton.Visible", true);
               cmd.set("#MyMemberList[" + i + "] #TransferButton.Visible", true);
               if (this.confirmingKickIndex == memberIndex) {
                  cmd.set("#MyMemberList[" + i + "] #KickButton.Text", LanguageManager.getInstance().get("gui.btn_sure"));
                  evt.addEventBinding(CustomUIEventBindingType.Activating, "#MyMemberList[" + i + "] #KickButton", EventData.of("Action", "Kick:" + memberIndex), false);
                  evt.addEventBinding(CustomUIEventBindingType.MouseExited, "#MyMemberList[" + i + "] #KickButton", EventData.of("Action", "CancelKick:" + memberIndex), false);
               } else {
                  cmd.set("#MyMemberList[" + i + "] #KickButton.Text", LanguageManager.getInstance().get("gui.btn_kick"));
                  evt.addEventBinding(CustomUIEventBindingType.Activating, "#MyMemberList[" + i + "] #KickButton", EventData.of("Action", "Kick:" + memberIndex), false);
               }

               if (this.confirmingTransferIndex == memberIndex) {
                  cmd.set("#MyMemberList[" + i + "] #TransferButton.Text", LanguageManager.getInstance().get("gui.btn_sure"));
                  evt.addEventBinding(CustomUIEventBindingType.Activating, "#MyMemberList[" + i + "] #TransferButton", EventData.of("Action", "Transfer:" + memberIndex), false);
                  evt.addEventBinding(CustomUIEventBindingType.MouseExited, "#MyMemberList[" + i + "] #TransferButton", EventData.of("Action", "CancelTransfer:" + memberIndex), false);
               } else {
                  cmd.set("#MyMemberList[" + i + "] #TransferButton.Text", LanguageManager.getInstance().get("gui.btn_promote"));
                  evt.addEventBinding(CustomUIEventBindingType.Activating, "#MyMemberList[" + i + "] #TransferButton", EventData.of("Action", "Transfer:" + memberIndex), false);
               }
            } else {
               cmd.set("#MyMemberList[" + i + "] #KickButton.Visible", false);
               cmd.set("#MyMemberList[" + i + "] #TransferButton.Visible", false);
            }
         }

      }
   }

   private void buildInvitesTab(UICommandBuilder cmd, UIEventBuilder evt) {
      PartyManager manager = PartyManager.getInstance();
      PlayerNameTracker nameTracker = manager.getPlayerNameTracker();
      PartyInvite invite = manager.getPendingInvite(this.playerRef.getUuid());
      cmd.clear("#InviteList");
      if (invite == null) {
         cmd.set("#NoInvitesLabel.Visible", true);
      } else {
         cmd.set("#NoInvitesLabel.Visible", false);
         PartyInfo party = manager.getPartyById(invite.partyId());
         if (party == null) {
            cmd.set("#NoInvitesLabel.Visible", true);
         } else {
            cmd.append("#InviteList", "Pages/PartyPro/InviteEntry.ui");
            cmd.set("#InviteList[0] #PartyName.Text", party.getName());
            cmd.set("#InviteList[0] #InvitedBy.Text", LanguageManager.getInstance().get("gui.label_from", nameTracker.getPlayerName(invite.sender())));
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#InviteList[0] #AcceptButton", EventData.of("Action", "AcceptInvite:0"), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#InviteList[0] #DeclineButton", EventData.of("Action", "DeclineInvite:0"), false);
         }
      }
   }

   private void buildPartiesTab(UICommandBuilder cmd, UIEventBuilder evt, PartyInfo myParty) {
      PartyManager manager = PartyManager.getInstance();
      PlayerNameTracker nameTracker = manager.getPlayerNameTracker();
      HashMap<String, PartyInfo> parties = manager.getParties();
      boolean alreadyInParty = myParty != null;
      cmd.clear("#PartyList");
      int i = 0;
      Iterator var9 = parties.values().iterator();

      while(true) {
         PartyInfo party;
         do {
            do {
               do {
                  if (!var9.hasNext()) {
                     cmd.set("#NoPartiesLabel.Visible", i == 0);
                     return;
                  }

                  party = (PartyInfo)var9.next();
               } while(!party.isPublic() && !party.hasPassword());
            } while(!party.canAddMember());
         } while(!this.searchQuery.isEmpty() && !party.getName().toLowerCase().contains(this.searchQuery.toLowerCase()));

         cmd.append("#PartyList", "Pages/PartyPro/PartyListEntry.ui");
         cmd.set("#PartyList[" + i + "] #PartyName.Text", party.getName());
         cmd.set("#PartyList[" + i + "] #PartyLeader.Text", LanguageManager.getInstance().get("gui.label_leader", nameTracker.getPlayerName(party.getLeader())));
         cmd.set("#PartyList[" + i + "] #PartySize.Text", LanguageManager.getInstance().get("gui.label_size", party.getTotalMemberCount(), PartyProConfig.getInstance().getMaxPartySize()));
         cmd.set("#PartyList[" + i + "] #PasswordIcon.Visible", party.hasPassword());
         cmd.set("#PartyList[" + i + "] #JoinButton.Visible", !alreadyInParty);
         if (!alreadyInParty) {
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#PartyList[" + i + "] #JoinButton", EventData.of("Action", "JoinParty:" + party.getId().toString()), false);
         }

         ++i;
      }
   }

   private void buildPlayersList(UICommandBuilder cmd, UIEventBuilder evt, PartyInfo myParty) {
      PartyManager manager = PartyManager.getInstance();
      PlayerNameTracker nameTracker = manager.getPlayerNameTracker();
      boolean isLeader = myParty != null && myParty.isLeader(this.playerRef.getUuid());
      boolean canInvite = isLeader && myParty.canAddMember();
      cmd.clear("#PlayerList");
      HashMap<UUID, String> knownPlayers = nameTracker.getNames();
      int i = 0;
      Iterator var10 = knownPlayers.entrySet().iterator();

      while(true) {
         UUID pUuid;
         String playerName;
         boolean isOnline;
         do {
            do {
               do {
                  if (!var10.hasNext()) {
                     cmd.set("#NoPlayersLabel.Visible", i == 0);
                     return;
                  }

                  Entry<UUID, String> entry = (Entry)var10.next();
                  pUuid = (UUID)entry.getKey();
                  playerName = (String)entry.getValue();
               } while(pUuid.equals(this.playerRef.getUuid()));

               PlayerRef pRef = Universe.get().getPlayer(pUuid);
               isOnline = pRef != null && pRef.isValid();
            } while(!isOnline);
         } while(!this.searchQuery.isEmpty() && !playerName.toLowerCase().contains(this.searchQuery.toLowerCase()));

         PartyInfo theirParty = manager.getPartyFromPlayer(pUuid);
         boolean inParty = theirParty != null;
         boolean inMyParty = myParty != null && myParty.isLeaderOrMember(pUuid);
         cmd.append("#PlayerList", "Pages/PartyPro/PlayerListEntry.ui");
         cmd.set("#PlayerList[" + i + "] #PlayerName.Text", playerName);
         if (inMyParty) {
            cmd.set("#PlayerList[" + i + "] #PlayerStatus.Text", LanguageManager.getInstance().get("gui.status_in_your_party"));
            cmd.set("#PlayerList[" + i + "] #PlayerStatus.Style.TextColor", "#66ff66");
            cmd.set("#PlayerList[" + i + "] #InviteButton.Visible", false);
         } else if (inParty) {
            cmd.set("#PlayerList[" + i + "] #PlayerStatus.Text", LanguageManager.getInstance().get("gui.status_in_a_party"));
            cmd.set("#PlayerList[" + i + "] #PlayerStatus.Style.TextColor", "#ff8866");
            cmd.set("#PlayerList[" + i + "] #InviteButton.Visible", false);
         } else {
            cmd.set("#PlayerList[" + i + "] #PlayerStatus.Text", LanguageManager.getInstance().get("gui.status_available"));
            cmd.set("#PlayerList[" + i + "] #PlayerStatus.Style.TextColor", "#88ff88");
            cmd.set("#PlayerList[" + i + "] #InviteButton.Visible", canInvite);
            cmd.set("#PlayerList[" + i + "] #InviteButton.Text", LanguageManager.getInstance().get("gui.btn_invite"));
            cmd.set("#PlayerList[" + i + "] #InviteButton.Disabled", false);
            if (canInvite) {
               evt.addEventBinding(CustomUIEventBindingType.Activating, "#PlayerList[" + i + "] #InviteButton", EventData.of("Action", "InvitePlayer:" + pUuid.toString()), false);
            }
         }

         ++i;
      }
   }

   private void buildChatTab(UICommandBuilder cmd, UIEventBuilder evt, PartyInfo myParty) {
      cmd.clear("#ChatMessages");
      if (myParty == null) {
         cmd.set("#NoChatLabel.Visible", true);
         cmd.set("#NoChatLabel.Text", LanguageManager.getInstance().get("gui.chat_join_party"));
         cmd.set("#ChatInput.Visible", false);
         cmd.set("#SendChatButton.Visible", false);
      } else {
         cmd.set("#ChatInput.Visible", true);
         cmd.set("#SendChatButton.Visible", true);
         cmd.set("#ChatInput.Value", this.chatInput);
         evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ChatInput", EventData.of("@ChatInput", "#ChatInput.Value"), false);
         evt.addEventBinding(CustomUIEventBindingType.Activating, "#SendChatButton", EventData.of("Action", "SendChat:0"), false);
         List<PartyBrowserGui.ChatMessage> messages = getChatHistory(myParty.getId());
         if (messages.isEmpty()) {
            cmd.set("#NoChatLabel.Visible", true);
            cmd.set("#NoChatLabel.Text", LanguageManager.getInstance().get("gui.chat_no_messages"));
         } else {
            cmd.set("#NoChatLabel.Visible", false);

            for(int i = 0; i < messages.size(); ++i) {
               PartyBrowserGui.ChatMessage msg = (PartyBrowserGui.ChatMessage)messages.get(i);
               cmd.append("#ChatMessages", "Pages/PartyPro/ChatMessage.ui");
               cmd.set("#ChatMessages[" + i + "] #SenderName.Text", msg.senderName + ":");
               cmd.set("#ChatMessages[" + i + "] #MessageText.Text", msg.message);
            }

         }
      }
   }

   private void buildLeaderboardTab(UICommandBuilder cmd, UIEventBuilder evt) {
      LanguageManager lang = LanguageManager.getInstance();
      PartyManager manager = PartyManager.getInstance();
      PartyStatsManager statsManager = PartyStatsManager.getInstance();
      if (statsManager.isResetEnabled()) {
         cmd.set("#ResetCountdownBar.Visible", true);
         long daysUntilReset = statsManager.getDaysUntilReset();
         String countdownText;
         if (daysUntilReset == 0L) {
            countdownText = lang.get("gui.lb_reset_today");
         } else if (daysUntilReset == 1L) {
            countdownText = lang.get("gui.lb_reset_tomorrow");
         } else {
            countdownText = lang.get("gui.lb_reset_in_days", String.valueOf(daysUntilReset));
         }

         cmd.set("#ResetCountdownLabel.Text", countdownText);
      } else {
         cmd.set("#ResetCountdownBar.Visible", false);
      }

      cmd.set("#LbSortByLabel.Text", lang.get("gui.lb_sort_by"));
      cmd.set("#NoLeaderboardLabel.Text", lang.get("gui.lb_no_parties"));
      cmd.set("#LbCatKills.Disabled", this.leaderboardCategory == 0);
      cmd.set("#LbCatDamage.Disabled", this.leaderboardCategory == 1);
      cmd.set("#LbCatBlocks.Disabled", this.leaderboardCategory == 2);
      cmd.set("#LbCatDistance.Disabled", this.leaderboardCategory == 3);
      cmd.set("#LbCatTime.Disabled", this.leaderboardCategory == 4);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#LbCatKills", EventData.of("Action", "LbCategory:0"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#LbCatDamage", EventData.of("Action", "LbCategory:1"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#LbCatBlocks", EventData.of("Action", "LbCategory:2"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#LbCatDistance", EventData.of("Action", "LbCategory:3"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#LbCatTime", EventData.of("Action", "LbCategory:4"), false);
      String[] headers = new String[]{lang.get("gui.lb_cat_kills"), lang.get("gui.lb_cat_damage"), lang.get("gui.lb_cat_blocks"), lang.get("gui.lb_cat_distance"), lang.get("gui.lb_cat_time")};
      cmd.set("#LeaderboardStatHeader.Text", headers[this.leaderboardCategory]);
      List<PartyInfo> parties = new ArrayList(manager.getParties().values());
      parties.sort((a, b) -> {
         PartyStats statsA = statsManager.getStats(a.getId());
         PartyStats statsB = statsManager.getStats(b.getId());
         int var10000;
         switch(this.leaderboardCategory) {
         case 0:
            var10000 = Integer.compare(statsB.getMobKills() + statsB.getPlayerKills(), statsA.getMobKills() + statsA.getPlayerKills());
            break;
         case 1:
            var10000 = Long.compare(statsB.getDamageDealt(), statsA.getDamageDealt());
            break;
         case 2:
            var10000 = Integer.compare(statsB.getBlocksBroken() + statsB.getBlocksPlaced(), statsA.getBlocksBroken() + statsA.getBlocksPlaced());
            break;
         case 3:
            var10000 = Double.compare(statsB.getDistanceTraveledBlocks(), statsA.getDistanceTraveledBlocks());
            break;
         case 4:
            var10000 = Long.compare(statsB.getTimeTogetherMs(), statsA.getTimeTogetherMs());
            break;
         default:
            var10000 = 0;
         }

         return var10000;
      });
      cmd.clear("#LeaderboardList");
      if (parties.isEmpty()) {
         cmd.set("#NoLeaderboardLabel.Visible", true);
      } else {
         cmd.set("#NoLeaderboardLabel.Visible", false);
         int count = Math.min(parties.size(), 20);

         for(int i = 0; i < count; ++i) {
            PartyInfo party = (PartyInfo)parties.get(i);
            PartyStats stats = statsManager.getStats(party.getId());
            cmd.append("#LeaderboardList", "Pages/PartyPro/LeaderboardEntry.ui");
            cmd.set("#LeaderboardList[" + i + "] #Rank.Text", String.valueOf(i + 1));
            cmd.set("#LeaderboardList[" + i + "] #PartyName.Text", party.getName());
            cmd.set("#LeaderboardList[" + i + "] #MemberCount.Text", String.valueOf(party.getTotalMemberCount()));
            String var10000;
            switch(this.leaderboardCategory) {
            case 0:
               var10000 = String.valueOf(stats.getMobKills() + stats.getPlayerKills());
               break;
            case 1:
               var10000 = this.formatNumber(stats.getDamageDealt());
               break;
            case 2:
               var10000 = this.formatNumber((long)(stats.getBlocksBroken() + stats.getBlocksPlaced()));
               break;
            case 3:
               var10000 = this.formatDistance(stats.getDistanceTraveledBlocks());
               break;
            case 4:
               var10000 = stats.getTimeTogetherFormatted();
               break;
            default:
               var10000 = "0";
            }

            String statValue = var10000;
            cmd.set("#LeaderboardList[" + i + "] #StatValue.Text", statValue);
            if (i == 0) {
               cmd.set("#LeaderboardList[" + i + "] #Rank.Style.TextColor", "#ffd700");
            } else if (i == 1) {
               cmd.set("#LeaderboardList[" + i + "] #Rank.Style.TextColor", "#c0c0c0");
            } else if (i == 2) {
               cmd.set("#LeaderboardList[" + i + "] #Rank.Style.TextColor", "#cd7f32");
            }
         }

      }
   }

   private void buildSettingsTab(UICommandBuilder cmd, UIEventBuilder evt) {
      PartyHudManager.HudSettings settings = PartyHudManager.getSettings(this.playerRef.getUuid());
      cmd.set("#HudEnabledSetting #CheckBox.Value", settings.enabled);
      evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#HudEnabledSetting #CheckBox", EventData.of("Action", "ToggleHudEnabled:0"), false);
      cmd.set("#ShowOnlyOnlineSetting #CheckBox.Value", settings.showOnlyOnline);
      evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ShowOnlyOnlineSetting #CheckBox", EventData.of("Action", "ToggleShowOnlyOnline:0"), false);
      LanguageManager lang = LanguageManager.getInstance();
      boolean isLeft = settings.isHudOnLeft();
      cmd.set("#HudPositionLeft.Disabled", isLeft);
      cmd.set("#HudPositionRight.Disabled", !isLeft);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#HudPositionLeft", EventData.of("Action", "SetHudSide:LEFT"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#HudPositionRight", EventData.of("Action", "SetHudSide:RIGHT"), false);
      boolean isCompact = settings.isCompactMode();
      cmd.set("#HudModeNormal.Disabled", !isCompact);
      cmd.set("#HudModeCompact.Disabled", isCompact);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#HudModeNormal", EventData.of("Action", "SetHudMode:NORMAL"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#HudModeCompact", EventData.of("Action", "SetHudMode:COMPACT"), false);
      cmd.set("#InviteModeNormal.Text", lang.get("gui.invite_mode_normal"));
      cmd.set("#InviteModeAccept.Text", lang.get("gui.invite_mode_accept"));
      cmd.set("#InviteModeDecline.Text", lang.get("gui.invite_mode_decline"));
      PartyHudManager.InviteMode mode = settings.inviteMode;
      cmd.set("#InviteModeNormal.Disabled", mode == PartyHudManager.InviteMode.NORMAL);
      cmd.set("#InviteModeAccept.Disabled", mode == PartyHudManager.InviteMode.AUTO_ACCEPT);
      cmd.set("#InviteModeDecline.Disabled", mode == PartyHudManager.InviteMode.AUTO_DECLINE);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#InviteModeNormal", EventData.of("Action", "SetInviteMode:NORMAL"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#InviteModeAccept", EventData.of("Action", "SetInviteMode:AUTO_ACCEPT"), false);
      evt.addEventBinding(CustomUIEventBindingType.Activating, "#InviteModeDecline", EventData.of("Action", "SetInviteMode:AUTO_DECLINE"), false);
      String var10000;
      switch(mode) {
      case NORMAL:
         var10000 = lang.get("gui.invite_mode_normal_desc");
         break;
      case AUTO_ACCEPT:
         var10000 = lang.get("gui.invite_mode_accept_desc");
         break;
      case AUTO_DECLINE:
         var10000 = lang.get("gui.invite_mode_decline_desc");
         break;
      default:
         throw new MatchException((String)null, (Throwable)null);
      }

      String description = var10000;
      cmd.set("#InviteModeDescription.Text", description);
   }

   private void buildStatsTab(UICommandBuilder cmd, UIEventBuilder evt, PartyInfo myParty) {
      LanguageManager lang = LanguageManager.getInstance();
      PlayerNameTracker nameTracker = PartyManager.getInstance().getPlayerNameTracker();
      if (myParty == null) {
         cmd.set("#NoStatsLabel.Visible", true);
         cmd.set("#NoStatsLabel.Text", lang.get("gui.stats_join_party"));
         cmd.set("#StatsContainer.Visible", false);
         cmd.set("#StatsMemberSelector.Visible", false);
      } else {
         cmd.set("#NoStatsLabel.Visible", false);
         cmd.set("#StatsContainer.Visible", true);
         cmd.set("#StatsMemberSelector.Visible", true);
         PartyStats partyStats = PartyStatsManager.getInstance().getStats(myParty.getId());
         UUID[] allMembers = myParty.getAllPartyMembers();
         if (this.statsMemberIndex >= allMembers.length) {
            this.statsMemberIndex = -1;
         }

         evt.addEventBinding(CustomUIEventBindingType.Activating, "#StatsPrevMember", EventData.of("Action", "StatsPrevMember:0"), false);
         evt.addEventBinding(CustomUIEventBindingType.Activating, "#StatsNextMember", EventData.of("Action", "StatsNextMember:0"), false);
         String currentMemberName;
         if (this.statsMemberIndex == -1) {
            currentMemberName = lang.get("gui.stats_all_members");
         } else {
            currentMemberName = nameTracker.getPlayerName(allMembers[this.statsMemberIndex]);
         }

         cmd.set("#StatsCurrentMember.Text", currentMemberName);
         int mobKills;
         int playerKills;
         int deaths;
         int blocksPlaced;
         int blocksBroken;
         int pingsSent;
         int teleportsUsed;
         int itemsCollected;
         int itemsCrafted;
         long damageDealt;
         long damageTaken;
         double distanceTraveled;
         if (this.statsMemberIndex == -1) {
            mobKills = partyStats.getMobKills();
            playerKills = partyStats.getPlayerKills();
            deaths = partyStats.getDeaths();
            damageDealt = partyStats.getDamageDealt();
            damageTaken = partyStats.getDamageTaken();
            blocksPlaced = partyStats.getBlocksPlaced();
            blocksBroken = partyStats.getBlocksBroken();
            distanceTraveled = partyStats.getDistanceTraveledBlocks();
            pingsSent = partyStats.getPingsSent();
            teleportsUsed = partyStats.getTeleportsUsed();
            itemsCollected = partyStats.getItemsCollected();
            itemsCrafted = partyStats.getItemsCrafted();
         } else {
            PlayerStats playerStats = partyStats.getPlayerStats(allMembers[this.statsMemberIndex]);
            mobKills = playerStats.getMobKills();
            playerKills = playerStats.getPlayerKills();
            deaths = playerStats.getDeaths();
            damageDealt = playerStats.getDamageDealt();
            damageTaken = playerStats.getDamageTaken();
            blocksPlaced = playerStats.getBlocksPlaced();
            blocksBroken = playerStats.getBlocksBroken();
            distanceTraveled = playerStats.getDistanceTraveledBlocks();
            pingsSent = playerStats.getPingsSent();
            teleportsUsed = playerStats.getTeleportsUsed();
            itemsCollected = playerStats.getItemsCollected();
            itemsCrafted = playerStats.getItemsCrafted();
         }

         cmd.set("#StatMobKills.Text", String.valueOf(mobKills));
         cmd.set("#StatPlayerKills.Text", String.valueOf(playerKills));
         cmd.set("#StatDeaths.Text", String.valueOf(deaths));
         cmd.set("#StatDamageDealt.Text", this.formatNumber(damageDealt));
         cmd.set("#StatDamageTaken.Text", this.formatNumber(damageTaken));
         cmd.set("#StatBlocksPlaced.Text", this.formatNumber((long)blocksPlaced));
         cmd.set("#StatBlocksBroken.Text", this.formatNumber((long)blocksBroken));
         cmd.set("#StatDistanceTraveled.Text", this.formatDistance(distanceTraveled));
         cmd.set("#StatPingsSent.Text", String.valueOf(pingsSent));
         cmd.set("#StatTeleportsUsed.Text", String.valueOf(teleportsUsed));
         cmd.set("#StatTimeTogether.Text", partyStats.getTimeTogetherFormatted());
         cmd.set("#StatItemsCollected.Text", this.formatNumber((long)itemsCollected));
         cmd.set("#StatItemsCrafted.Text", this.formatNumber((long)itemsCrafted));
         cmd.set("#StatMembersJoined.Text", String.valueOf(partyStats.getMembersJoined()));
         cmd.set("#StatMembersLeft.Text", String.valueOf(partyStats.getMembersLeft()));
      }
   }

   private String formatNumber(long number) {
      if (number >= 1000000L) {
         return String.format("%.1fM", (double)number / 1000000.0D);
      } else {
         return number >= 1000L ? String.format("%.1fK", (double)number / 1000.0D) : String.valueOf(number);
      }
   }

   private String formatDistance(double blocks) {
      return blocks >= 1000.0D ? String.format("%.1fkm", blocks / 1000.0D) : String.format("%.0fm", blocks);
   }

   public static class GuiData {
      public static final BuilderCodec<PartyBrowserGui.GuiData> CODEC;
      private String action;
      private String tab;
      private String searchQuery;
      private String renameInput;
      private String passwordInput;
      private String joinPasswordInput;
      private String chatInput;
      private String close;

      static {
         CODEC = ((Builder)((Builder)((Builder)((Builder)((Builder)((Builder)((Builder)((Builder)BuilderCodec.builder(PartyBrowserGui.GuiData.class, PartyBrowserGui.GuiData::new).addField(new KeyedCodec("Action", Codec.STRING), (d, s) -> {
            d.action = s;
         }, (d) -> {
            return d.action;
         })).addField(new KeyedCodec("Tab", Codec.STRING), (d, s) -> {
            d.tab = s;
         }, (d) -> {
            return d.tab;
         })).addField(new KeyedCodec("@SearchQuery", Codec.STRING), (d, s) -> {
            d.searchQuery = s;
         }, (d) -> {
            return d.searchQuery;
         })).addField(new KeyedCodec("@RenameInput", Codec.STRING), (d, s) -> {
            d.renameInput = s;
         }, (d) -> {
            return d.renameInput;
         })).addField(new KeyedCodec("@PasswordInput", Codec.STRING), (d, s) -> {
            d.passwordInput = s;
         }, (d) -> {
            return d.passwordInput;
         })).addField(new KeyedCodec("@JoinPasswordInput", Codec.STRING), (d, s) -> {
            d.joinPasswordInput = s;
         }, (d) -> {
            return d.joinPasswordInput;
         })).addField(new KeyedCodec("@ChatInput", Codec.STRING), (d, s) -> {
            d.chatInput = s;
         }, (d) -> {
            return d.chatInput;
         })).addField(new KeyedCodec("Close", Codec.STRING), (d, s) -> {
            d.close = s;
         }, (d) -> {
            return d.close;
         })).build();
      }
   }

   @FunctionalInterface
   interface GuiAction {
      void execute(PartyBrowserGui.ActionContext var1);
   }

   class ActionContext {
      private final String[] parts;
      private final Ref<EntityStore> ref;
      private final Store<EntityStore> store;
      private final PartyManager manager;
      private final PartyInfo party;
      private final boolean isLeader;

      ActionContext(final PartyBrowserGui param1, String[] param2, Ref param3, Store param4) {
         Objects.requireNonNull(this$0);
         super();
         this.parts = parts;
         this.ref = ref;
         this.store = store;
         this.manager = PartyManager.getInstance();
         this.party = this.manager.getPartyFromPlayer(this$0.playerRef.getUuid());
         this.isLeader = this.party != null && this.party.isLeader(this$0.playerRef.getUuid());
      }

      public int getIntArg(int index) {
         return Integer.parseInt(this.parts[index]);
      }

      public String getStringArg(int index) {
         return this.parts[index];
      }

      public UUID getUuidArg(int index) {
         return UUID.fromString(this.parts[index]);
      }

      public boolean isLeader() {
         return this.isLeader;
      }

      public PartyInfo getParty() {
         return this.party;
      }

      public PartyManager getManager() {
         return this.manager;
      }

      public Ref<EntityStore> getRef() {
         return this.ref;
      }

      public Store<EntityStore> getStore() {
         return this.store;
      }
   }

   public static class ChatMessage {
      public final UUID senderId;
      public final String senderName;
      public final String message;
      public final long timestamp;

      public ChatMessage(UUID senderId, String senderName, String message) {
         this.senderId = senderId;
         this.senderName = senderName;
         this.message = message;
         this.timestamp = System.currentTimeMillis();
      }
   }
}
