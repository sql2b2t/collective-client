package me.collective.client.managers;

import me.collective.client.utils.movement.PlayerMotionPredictor;

import me.collective.client.utils.ElapsedTimer;
import me.collective.client.ClientAccess;
import me.collective.client.events.ClientLevelRemoveEntityEvent;
import me.collective.client.events.EventHandler;
import me.collective.client.events.LocalPlayerTickEvent;
import me.collective.client.events.MinecraftDisconnectEvent;
import me.collective.client.events.MinecraftRunTickEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MovementPredictionManager implements ClientAccess {
   private final Map<Integer, PlayerMotionPredictor> playerPredictions = Collections.synchronizedMap(new HashMap<>());
   private final ElapsedTimer predictionTimer = new ElapsedTimer();
   private Vec3 previousPlayerPosition;

   public Vec3 getAdjustedLocalMotion() {
      if (this.previousPlayerPosition == null) {
         return Vec3.ZERO;
      }
      LocalPlayer player = ClientAccess.collectiveMinecraft.player;
      Vec3 motion = player.position().subtract(this.previousPlayerPosition);
      if (player.isSprinting() && !player.isFallFlying()) {
         motion = motion.multiply((double)0.9F, 1.0D, (double)0.9F);
      }
      return motion;
   }

   public Vec3 getPredictedEyePosition() {
      return ClientAccess.collectiveMinecraft.player.getEyePosition().add(this.getAdjustedLocalMotion());
   }

   public MovementPredictionManager() {
      ClientAccess.eventBus.subscribe(this);
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.previousPlayerPosition = new Vec3(
         ClientAccess.collectiveMinecraft.player.xo,
         ClientAccess.collectiveMinecraft.player.yo,
         ClientAccess.collectiveMinecraft.player.zo
      );
   }

   @EventHandler
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      if (this.isWorldUnavailable() || !this.predictionTimer.hasElapsedAndReset(25L)) {
         return;
      }

      for (AbstractClientPlayer player : ClientAccess.collectiveMinecraft.level.players()) {
         this.playerPredictions.computeIfAbsent(player.getId(), ignored -> new PlayerMotionPredictor(player))
            .updatePredictions();
      }
   }

   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      this.playerPredictions.clear();
   }

   public synchronized AABB getPredictedBoundingBox(Player player, int tickIndex) {
      PlayerMotionPredictor prediction = this.playerPredictions.get(player.getId());
      if (prediction == null) {
         return player.getBoundingBox();
      }
      List<AABB> positions = prediction.getPredictedBoxes();
      return positions.isEmpty()
         ? player.getBoundingBox()
         : positions.get(Math.max(0, Math.min(tickIndex, positions.size() - 1)));
   }

   @EventHandler
   private void onClientLevelRemoveEntityEvent(ClientLevelRemoveEntityEvent event) {
      synchronized (this.playerPredictions) {
         this.playerPredictions.remove(event.getId());
      }
   }

}
