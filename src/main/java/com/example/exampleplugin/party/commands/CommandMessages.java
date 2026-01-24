package com.example.exampleplugin.party.commands;

import com.hypixel.hytale.server.core.Message;
import java.awt.Color;

public class CommandMessages {
   public static final Message NOT_IN_PARTY;
   public static final Message NOT_IN_A_PARTY;
   public static final Message ALREADY_IN_A_PARTY;
   public static final Message PARTY_FULL;
   public static final Message PLAYER_NOT_FOUND;
   public static final Message NOT_PARTY_LEADER;
   public static final Message CANNOT_INVITE_SELF;
   public static final Message PLAYER_ALREADY_IN_PARTY;
   public static final Message NO_PENDING_INVITE;
   public static final Message INVITE_EXPIRED;
   public static final Message PARTY_INVITE_EXPIRED;
   public static final Message CANNOT_KICK_LEADER;
   public static final Message PLAYER_NOT_IN_YOUR_PARTY;
   public static final Message PLAYER_NOT_IN_PARTY;
   public static final Message CANNOT_TP_TO_SELF;
   public static final Message TARGET_NOT_IN_WORLD;
   public static final Message TELEPORT_DISABLED;
   public static final Message PARTY_CREATED;
   public static final Message PARTY_DISBANDED;
   public static final Message PARTY_LEFT;
   public static final Message PARTY_OWNER_TRANSFERRED;
   public static final Message PARTY_INVITE_SENT;
   public static final Message PARTY_INVITE_RECEIVED;
   public static final Message PARTY_JOINED;
   public static final Message PLAYER_JOINED_PARTY;
   public static final Message INVITE_DECLINED;
   public static final Message PARTY_INVITE_DECLINED;
   public static final Message PLAYER_KICKED;
   public static final Message YOU_WERE_KICKED;
   public static final Message TELEPORTING_TO_PLAYER;
   public static final Message PARTY_INFO_HEADER;

   static {
      NOT_IN_PARTY = Message.raw("You are not in a party!").color(Color.RED).bold(true);
      NOT_IN_A_PARTY = Message.raw("You are not in a party!").color(Color.RED).bold(true);
      ALREADY_IN_A_PARTY = Message.raw("You are already in a party!").color(Color.RED).bold(true);
      PARTY_FULL = Message.raw("The party is full!").color(Color.RED).bold(true);
      PLAYER_NOT_FOUND = Message.raw("Player not found!").color(Color.RED).bold(true);
      NOT_PARTY_LEADER = Message.raw("You are not the party leader!").color(Color.RED).bold(true);
      CANNOT_INVITE_SELF = Message.raw("You cannot invite yourself!").color(Color.RED).bold(true);
      PLAYER_ALREADY_IN_PARTY = Message.raw("This player is already in a party!").color(Color.RED).bold(true);
      NO_PENDING_INVITE = Message.raw("You have no pending invite!").color(Color.RED).bold(true);
      INVITE_EXPIRED = Message.raw("The invite has expired!").color(Color.RED).bold(true);
      PARTY_INVITE_EXPIRED = Message.raw("The invite has expired!").color(Color.RED).bold(true);
      CANNOT_KICK_LEADER = Message.raw("You cannot kick the party leader!").color(Color.RED).bold(true);
      PLAYER_NOT_IN_YOUR_PARTY = Message.raw("This player is not in your party!").color(Color.RED).bold(true);
      PLAYER_NOT_IN_PARTY = Message.raw("This player is not in your party!").color(Color.RED).bold(true);
      CANNOT_TP_TO_SELF = Message.raw("You cannot teleport to yourself!").color(Color.RED).bold(true);
      TARGET_NOT_IN_WORLD = Message.raw("Target player is not in a world!").color(Color.RED).bold(true);
      TELEPORT_DISABLED = Message.raw("Teleport is disabled!").color(Color.RED).bold(true);
      PARTY_CREATED = Message.raw("Party created!").color(Color.GREEN).bold(true);
      PARTY_DISBANDED = Message.raw("Party disbanded!").color(Color.GREEN).bold(true);
      PARTY_LEFT = Message.raw("You left the party!").color(Color.GREEN).bold(true);
      PARTY_OWNER_TRANSFERRED = Message.raw("Party ownership transferred!").color(Color.GREEN).bold(true);
      PARTY_INVITE_SENT = Message.raw("Invite sent!").color(Color.GREEN).bold(true);
      PARTY_INVITE_RECEIVED = Message.raw("You received a party invite!").color(Color.CYAN).bold(true);
      PARTY_JOINED = Message.raw("You joined the party!").color(Color.GREEN).bold(true);
      PLAYER_JOINED_PARTY = Message.raw("A player joined your party!").color(Color.GREEN).bold(true);
      INVITE_DECLINED = Message.raw("Invite declined!").color(Color.YELLOW).bold(true);
      PARTY_INVITE_DECLINED = Message.raw("Invite declined!").color(Color.YELLOW).bold(true);
      PLAYER_KICKED = Message.raw("Player kicked from party!").color(Color.GREEN).bold(true);
      YOU_WERE_KICKED = Message.raw("You were kicked from the party!").color(Color.RED).bold(true);
      TELEPORTING_TO_PLAYER = Message.raw("Teleporting...").color(Color.GREEN).bold(true);
      PARTY_INFO_HEADER = Message.raw("=== Party Info ===").color(Color.YELLOW).bold(true);
   }
}
