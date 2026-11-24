package me.collective.client.managers;

import me.collective.client.modules.misc.SwingModule.HandChoice;

import me.collective.client.utils.combat.BoundingBoxUtils;
import me.collective.client.utils.entity.SwingAction;
import me.collective.client.utils.network.PacketSender;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.events.PacketEvent;
import me.collective.client.events.CrystalPlacementEvent;
import me.collective.client.events.EventHandler;
import me.collective.client.events.LocalPlayerTickEvent;
import me.collective.client.events.bus.EventBus;
import me.collective.client.modules.client.AntiCheatModule;
import me.collective.client.modules.misc.SwingModule;
import me.collective.client.settings.Setting;

import me.collective.mixin.LivingEntityAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

public final class SwingInteractionManager implements ClientAccess {
   private static AntiCheatModule antiCheat;
   private static SwingModule swingModule;
   private final List<PendingCrystalPlacement> pendingCrystals;

   public SwingInteractionManager() {
      List<PendingCrystalPlacement> pending = new ArrayList<>(initialCrystalCapacity());
      this.pendingCrystals = Collections.synchronizedList(pending);
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   private static int initialCrystalCapacity() {
      return 10;
   }

   private static AntiCheatModule antiCheat() {
      if (antiCheat == null) {
         antiCheat = ClientContext.getModule(AntiCheatModule.class);
      }
      return antiCheat;
   }

   private static SwingModule swingModule() {
      if (swingModule == null) {
         swingModule = ClientContext.getModule(SwingModule.class);
      }
      return swingModule;
   }

   public boolean tryTrackCrystalPlacement(BlockPos position) {
      if (this.isCrystalPlacementLimited()) {
         return false;
      }

      this.pendingCrystals.add(new PendingCrystalPlacement(position, System.currentTimeMillis() + 300L));
      ClientAccess.eventBus.post(new CrystalPlacementEvent(position));
      return true;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.pendingCrystals.removeIf(pending -> {
         long expiresAt = pending.expiresAtMillis();
         return expiresAt <= System.currentTimeMillis();
      });
   }

   public void swing(
      InteractionHand hand, SwingAction action, boolean packetOnly
   ) {
      AntiCheatModule antiCheatSnapshot = antiCheat();
      if (!action.isEnabled(antiCheatSnapshot)) {
         if (!packetOnly) {
            this.swingClientSide(hand);
         }
      } else {
         if (packetOnly) {
            PacketSender.sendSwing(hand);
         } else {
            LocalPlayer player = ClientAccess.collectiveMinecraft.player;
            player.swing(hand);
         }
      }
   }

   public void swing(InteractionHand hand, SwingAction action) {
      boolean packetOnly = false;
      this.swing(hand, action, packetOnly);
   }

   public void swingPacket(InteractionHand hand, SwingAction action) {
      boolean packetOnly = true;
      this.swing(hand, action, packetOnly);
   }

   public void swingClientSide(InteractionHand hand) {
      if (ClientAccess.collectiveMinecraft.player.swinging) {
         int swingTime = ClientAccess.collectiveMinecraft.player.swingTime;
         int swingDuration = ((LivingEntityAccessor)ClientAccess.collectiveMinecraft.player)
            .getSwingDuration();

         if (swingTime < swingDuration / 2
            && ClientAccess.collectiveMinecraft.player.swingTime >= 0) {
            return;
         }
      }

      ClientAccess.collectiveMinecraft.player.swingTime = -1;
      ClientAccess.collectiveMinecraft.player.swinging = true;
      SwingModule moduleSnapshot = swingModule();
      if (moduleSnapshot.isToggled()) {
         Setting<HandChoice> handSetting = swingModule().handSetting;
         SwingModule.HandChoice handChoice = handSetting.getValue();
         if (handChoice.getHand() != null) {
            LocalPlayer player = ClientAccess.collectiveMinecraft.player;
            Setting<HandChoice> overrideSetting = swingModule().handSetting;
            SwingModule.HandChoice overrideChoice = overrideSetting.getValue();
            player.swingingArm = overrideChoice.getHand();
            return;
         }
      }

      ClientAccess.collectiveMinecraft.player.swingingArm = hand;
   }

   public boolean isCrystalPlacementLimited() {
      AntiCheatModule antiCheatSnapshot = antiCheat();
      if (!antiCheatSnapshot.isPaperPlacement()) {
         return false;
      }

      int pendingCount = this.pendingCrystals.size();

      return pendingCount >= 9;
   }

   @EventHandler
   private void onPacketReceive(
      PacketEvent.Receive event
   ) {
      PacketEvent.Receive packetEvent = event;
      if (packetEvent.getPacket() instanceof ClientboundAddEntityPacket packet) {
         if (packet.getType() == EntityType.END_CRYSTAL) {
            EntityType spawnTypeSnapshot = packet.getType();
            double spawnXSnapshot = packet.getX();
            double spawnYSnapshot = packet.getY();
            double spawnZ = packet.getZ();
            AABB spawnBoundsSnapshot = spawnTypeSnapshot.getSpawnAABB(spawnXSnapshot, spawnYSnapshot, spawnZ);
            double inset = -BoundingBoxUtils.blockCornerInset;
            AABB crystalBounds = spawnBoundsSnapshot.inflate(inset);
            BlockableEventLoop<?> executor = ClientAccess.collectiveMinecraft;
            Runnable removePlacement = () -> this.pendingCrystals.removeIf(pending -> {
               AABB placementBounds = new AABB(capturePlacementPosition(pending));
               return crystalBounds.intersects(placementBounds);
            });
            executor.execute(removePlacement);
         }
      }
   }

   private static BlockPos capturePlacementPosition(PendingCrystalPlacement pending) {
      return pending.position();
   }

   private record PendingCrystalPlacement(
      BlockPos position,
      long expiresAtMillis
   ) {
   }
}
