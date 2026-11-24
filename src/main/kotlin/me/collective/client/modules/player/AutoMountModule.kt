package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.EventHandler
import me.collective.client.managers.RotationManager
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.util.concurrent.TimeUnit
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.PlayerRideable
import net.minecraft.world.entity.animal.equine.AbstractHorse
import net.minecraft.world.entity.monster.Strider
import net.minecraft.world.entity.vehicle.VehicleEntity
import net.minecraft.world.phys.Vec3

class AutoMountModule : Module("AutoMount", "Mounts rideable entities automatically.", ModuleCategory.PLAYER) {
   private val rangeSetting: Setting<Double>
   private val delaySetting: Setting<Float>
   private val rotateSetting: Setting<Boolean>
   private val mountTimer: ElapsedTimer

   @EventHandler
   private fun onRotationRequest(event: RotationRequestEvent) {
      if (ClientAccess.collectiveMinecraft.player!!.isPassenger) {
         return
      }
      val timerSnapshot = this.mountTimer
      val configuredDelaySnapshot = this.delaySetting.value!!

      val minimumDelay = 0.1f

      val minimumElapsedDelay: Double = Math.max(configuredDelaySnapshot, minimumDelay).toDouble()
      val delayUnit = TimeUnit.SECONDS

      if (!timerSnapshot.hasElapsed(minimumElapsedDelay, delayUnit)) {
         return
      }
      if (ClientAccess.collectiveMinecraft.player!!.isShiftKeyDown) {
         return
      }

      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      for (entity in level.entitiesForRendering()) {
         val distance = ClientAccess.collectiveMinecraft.player!!.distanceTo(entity)
         val range = this.rangeSetting.value!!
         if (distance > range) {
            continue
         }
         if (entity is LivingEntity) {
            if (entity.isBaby) {
               continue
            }
         }

         if (entity is Mob) {
            if (!entity.isSaddled && entity !is AbstractHorse) {
               continue
            }
         }

         if (entity !is PlayerRideable && entity !is VehicleEntity && entity !is Strider) {
            continue
         }
         val interactionHand = InteractionHand.MAIN_HAND

         BlockInteractionUtils.interactWithEntity(entity, interactionHand)
         if (this.rotateSetting.value!!) {
            val rotationManagerSnapshot = ClientContext.rotationManager

            val boundingBoxCenter: Vec3 = entity.boundingBox.center

            val rotations = RotationManager.getRotationsToPosition(
               boundingBoxCenter
            )

            rotationManagerSnapshot.requestRotation(rotations, 5)
         }

         timerSnapshot.reset()
         break

      }

   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      if (event.packet !is ClientboundSetPassengersPacket) {
         return
      }
      val passengersPacket = event.packet as ClientboundSetPassengersPacket
      // Событие постится с netty-потока: чтение состояния игрока и сброс
      // таймера переносим на главный поток клиента (гонка с тиком).
      ClientAccess.collectiveMinecraft.execute {
         val delayOption = this.delaySetting

         if (!delayOption.isAtMinimum()) {
            return@execute
         }
         val player = ClientAccess.collectiveMinecraft.player ?: return@execute
         val vehicle = player.vehicle ?: return@execute
         val packetVehicleId = passengersPacket.vehicle
         if (packetVehicleId != vehicle.id) {
            return@execute
         }

         for (passengerId in passengersPacket.passengers) {
            if (passengerId == player.id) {
               return@execute
            }
         }

         this.mountTimer.clear()
      }
   }

   init {
      this.rangeSetting = this.add(NumberSetting<Double>("Range", 3.0, 0.1, 6.0).withUnit("m"))
      this.delaySetting = this.add(NumberSetting<Float>("Delay", 1.0f, 0.0f, 5.0f).withUnit("s"))
      val defaultRotate = true
      this.rotateSetting = this.add(BooleanSetting("Rotate", defaultRotate))
      this.mountTimer = ElapsedTimer()
   }

}
