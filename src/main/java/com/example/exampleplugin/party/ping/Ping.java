package com.example.exampleplugin.party.ping;

import com.hypixel.hytale.math.vector.Vector3d;
import java.util.UUID;
import javax.annotation.Nonnull;

public class Ping {
   private static final float DEFAULT_DURATION = 30.0F;
   @Nonnull
   private final UUID pingId;
   @Nonnull
   private final UUID ownerUuid;
   @Nonnull
   private final String ownerName;
   @Nonnull
   private final UUID partyId;
   @Nonnull
   private final Vector3d position;
   @Nonnull
   private final String worldName;
   private final int ownerIndex;
   private final long createdAt;
   private final float duration;

   public Ping(@Nonnull UUID ownerUuid, @Nonnull String ownerName, @Nonnull UUID partyId, @Nonnull Vector3d position, @Nonnull String worldName, int ownerIndex) {
      this(ownerUuid, ownerName, partyId, position, worldName, ownerIndex, 30.0F);
   }

   public Ping(@Nonnull UUID ownerUuid, @Nonnull String ownerName, @Nonnull UUID partyId, @Nonnull Vector3d position, @Nonnull String worldName, int ownerIndex, float duration) {
      this.pingId = UUID.randomUUID();
      this.ownerUuid = ownerUuid;
      this.ownerName = ownerName;
      this.partyId = partyId;
      this.position = position;
      this.worldName = worldName;
      this.ownerIndex = ownerIndex;
      this.createdAt = System.currentTimeMillis();
      this.duration = duration;
   }

   @Nonnull
   public UUID getPingId() {
      return this.pingId;
   }

   @Nonnull
   public UUID getOwnerUuid() {
      return this.ownerUuid;
   }

   @Nonnull
   public String getOwnerName() {
      return this.ownerName;
   }

   @Nonnull
   public UUID getPartyId() {
      return this.partyId;
   }

   @Nonnull
   public Vector3d getPosition() {
      return this.position;
   }

   @Nonnull
   public String getWorldName() {
      return this.worldName;
   }

   public int getOwnerIndex() {
      return this.ownerIndex;
   }

   public long getCreatedAt() {
      return this.createdAt;
   }

   public float getDuration() {
      return this.duration;
   }

   public boolean isExpired() {
      long now = System.currentTimeMillis();
      return (float)(now - this.createdAt) > this.duration * 1000.0F;
   }

   public float getRemainingTime() {
      long now = System.currentTimeMillis();
      float elapsed = (float)(now - this.createdAt) / 1000.0F;
      return Math.max(0.0F, this.duration - elapsed);
   }

   public double getDistanceFrom(@Nonnull Vector3d otherPos) {
      double dx = this.position.x - otherPos.x;
      double dy = this.position.y - otherPos.y;
      double dz = this.position.z - otherPos.z;
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }
}
