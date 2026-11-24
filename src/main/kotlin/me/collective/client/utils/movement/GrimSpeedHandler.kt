package me.collective.client.utils.movement

import me.collective.client.events.EventStage
import me.collective.client.utils.world.WorldUtils

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.commands.FakeplayerCommand
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.movement.SpeedModeHandler
import me.collective.client.modules.movement.SpeedModule

import net.minecraft.core.Direction.Axis
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.vehicle.boat.Boat
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.AnimationStatus
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

open class GrimSpeedHandler(speedModule: SpeedModule) : SpeedModeHandler(speedModule) {
   private var lastPlayerBounds: AABB? = null

   private fun intersectsNearbyEntity(entity: Entity): Boolean {
      val strictBounds = antiCheatModule.is2b2tEnabled()
      val entityBounds = entity.boundingBox
      val playerBounds = lastPlayerBounds!!
      val expandedPlayerBounds = if (strictBounds)
         playerBounds.inflate(0.4F.toDouble(), 0.2F.toDouble(), 0.4F.toDouble())
      else
         playerBounds.inflate(1.0)
      return entityBounds.intersects(expandedPlayerBounds)
   }

   override fun onSendPosition(event: LocalPlayerSendPositionEvent) {
      if (EventStage.Post == event.stage) {
         val player = ClientAccess.collectiveMinecraft.player ?: return
         lastPlayerBounds = player.boundingBox
      }
   }

   override fun onMove(event: LocalPlayerMoveEvent) {
      if (lastPlayerBounds == null || !MovementPhysics.isLocalPlayerMoving() ||
         !ClientContext.serverStateManager.hasElapsedSinceSetback(100L)
      ) {
         return
      }
      var collisionWeight = 0
      for (blockEntity in WorldUtils.getLoadedBlockEntities()) {
         if (blockEntity is ShulkerBoxBlockEntity) {
            val expandedPlayerBounds = lastPlayerBounds!!.inflate(0.5)
            val shulkerBounds = blockEntity.getBoundingBox(Blocks.SHULKER_BOX.defaultBlockState())
               .move(blockEntity.blockPos)
            if (expandedPlayerBounds.intersects(shulkerBounds) &&
         blockEntity.animationStatus != AnimationStatus.CLOSED &&
         !antiCheatModule.is2b2tEnabled()
            ) {
               collisionWeight += 4
            }
         }
      }
      val collisionBoost = if (antiCheatModule.is2b2tEnabled()) 0.06F else 0.08F
      val level = ClientAccess.collectiveMinecraft.level ?: return
      for (entity in level.entitiesForRendering()) {
         if (entity !is FakeplayerCommand.SimulatedPlayer &&
         entity !== ClientAccess.collectiveMinecraft.player!! &&
         entity.isPushable && intersectsNearbyEntity(entity)
         ) {
            if (entity is Boat) {
               collisionWeight += 4
            }
            collisionWeight++
         }
      }
      if (collisionWeight != 0) {

         val horizontalDirection = event.velocity.normalize().with(Axis.Y, 0.0)
         val velocity = event.velocity
         val boost = collisionBoost * collisionWeight
         event.setVelocity(velocity.add(horizontalDirection.scale(boost.toDouble())))
      }
   }

   companion object {
      private lateinit var antiCheatModule: AntiCheatModule

      @JvmStatic
      fun bindModules(modules: ModuleRegistry) {
         antiCheatModule = modules.getModule(AntiCheatModule::class.java) as AntiCheatModule
      }
   }
}
