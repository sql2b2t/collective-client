package me.collective.client.modules.render

import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import me.collective.client.ClientAccess
import me.collective.client.events.ConnectScreenConnectEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.render.ClientRenderTypes
import me.collective.client.render.ColorUtils
import me.collective.client.render.MeshBuffer
import me.collective.client.render.MeshDrawContext
import me.collective.client.render.RenderUtils
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import java.awt.Color
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap
import net.minecraft.client.CameraType
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.AbstractArrow
import net.minecraft.world.entity.projectile.ThrownTrident
import net.minecraft.world.entity.projectile.WitherSkull
import net.minecraft.world.entity.projectile.ThrownEnderpearl
import net.minecraft.world.entity.projectile.ThrownExperienceBottle
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

class TrajectoriesModule : Module("Trajectories", "Draws trajectories for projectiles and bows.", ModuleCategory.RENDER) {
   private val simulator: ProjectileTrajectorySimulator = ProjectileTrajectorySimulator()
   private val lineWidthSetting: Setting<Float>
   private val onlyOwnSetting: Setting<Boolean>
   private val predictSetting: Setting<Boolean>
   private val airborneSetting: Setting<Boolean>
   private val trailSetting: Setting<Boolean>
   private val trailLengthSetting: Setting<Int>
   private val colorSetting: Setting<Color>
   internal val bowSetting: Setting<Boolean>
   internal val crossbowSetting: Setting<Boolean>
   internal val tridentSetting: Setting<Boolean>
   internal val pearlsSetting: Setting<Boolean>
   internal val expSetting: Setting<Boolean>
   internal val othersSetting: Setting<Boolean>
   private val trailsByEntityId: MutableMap<Int, MutableList<Vec3>> = LinkedHashMap()
   private val trailSampleTimer: ElapsedTimer = ElapsedTimer()
   private var trailLevel: ClientLevel? = null
   var previousPlayerVelocity: Vec3 = Vec3.ZERO
      private set
   private var previousBowPower: Float = 0.0f

   init {
      this.lineWidthSetting = this.add(NumberSetting<Float>("LineWidth", 1.0F, 0.1F, 5.0F))
      this.onlyOwnSetting = this.add(BooleanSetting("OnlyOwn", true))
      this.predictSetting = this.add(BooleanSetting("Predict", true))
      this.airborneSetting = this.add(BooleanSetting("Airborne", false).markVisibilityDependency())
      this.trailSetting = this.add(BooleanSetting("Trail", true).dependsOn(this.airborneSetting))
      this.trailLengthSetting = this.add(NumberSetting<Int>("TrailLength", 100, 5, 400)
         .dependsOn(this.airborneSetting, this.trailSetting))
      this.colorSetting = this.add(ColorSetting("Color", Color(-1312974337, true)))
      val targets: Setting<Boolean> = this.add(BooleanSetting("Targets", false).markAsGroup().markVisibilityDependency())
      this.bowSetting = this.add(BooleanSetting("Bow", true).dependsOn(targets))
      this.crossbowSetting = this.add(BooleanSetting("Crossbow", true).dependsOn(targets))
      this.tridentSetting = this.add(BooleanSetting("Trident", true).dependsOn(targets))
      this.pearlsSetting = this.add(BooleanSetting("Pearls", true).dependsOn(targets))
      this.expSetting = this.add(BooleanSetting("Exp", true).dependsOn(targets))
      this.othersSetting = this.add(BooleanSetting("Others", true).dependsOn(targets))
      this.trailSetting.setChangeCallback(this.trailsByEntityId::clear)
      this.trailLengthSetting.setChangeCallback(this.trailsByEntityId::clear)
      this.airborneSetting.setChangeCallback(this.trailsByEntityId::clear)
      this.setDrawn(false)
   }

   private fun shouldTraceEntity(entity: Entity): Boolean {
      val localPlayer: Player = ClientAccess.collectiveMinecraft.player!!
      if (entity is Player) {
         return this.predictSetting.getValue()!!
            && (entity === localPlayer || !this.onlyOwnSetting.getValue()!!)
      }
      if (entity !is Projectile || entity is WitherSkull ||
         !this.airborneSetting.getValue()!!) return false
      if (this.onlyOwnSetting.getValue()!! && entity.getOwner() !== localPlayer) return false
      if (entity is ThrownEnderpearl) return this.pearlsSetting.getValue()!!
      if (entity is ThrownExperienceBottle) return this.expSetting.getValue()!!
      if (entity is ThrownTrident) return this.tridentSetting.getValue()!!
      if (entity is AbstractArrow) return this.bowSetting.getValue()!! || this.crossbowSetting.getValue()!!
      return this.othersSetting.getValue()!!
   }

   private fun getLaunchHand(player: Player): InteractionHand {
      if (player.isUsingItem() && this.simulator.isEnabledProjectileItem(player.getUseItem().getItem())) {
         return player.getUsedItemHand()
      }
      return if (this.simulator.isEnabledProjectileItem(player.getMainHandItem().getItem()))
         InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND
   }

   @EventHandler
   private fun onWorldRender(event: WorldRenderEvent.Main) {
      if (this.isWorldUnavailable()) return
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      this.refreshTrailLevel(level)
      val sampleTrail: Boolean = this.trailSampleTimer.hasElapsedAndReset(25L)
      val paths: MutableList<TrajectoryPath> = ArrayList()
      for (entity in level.entitiesForRendering()) {
         if (!this.shouldTraceEntity(entity)) continue
         val predicted = entity is Player
         if (entity is Projectile) {
            if (!this.simulator.initializeFromProjectile(entity, event.partialTick.toDouble())) continue
         } else if (entity is Player) {
            val stack: ItemStack = entity.getItemInHand(this.getLaunchHand(entity))
            if (!this.simulator.initializeFromItem(entity, stack, 0.0, true, event.partialTick.toDouble())) continue
         }
         val path = TrajectoryPath(entity, predicted)
         path.simulate()
         if (!predicted && this.trailSetting.getValue()!!) {
            path.trail = this.sampleTrail(entity, sampleTrail, event.partialTick)
         }
         paths.add(path)
      }
      if (paths.isEmpty()) return
      val color: Color = this.colorSetting.getValue()!!
      val lineWidth: Float = this.lineWidthSetting.getValue()!!
      val buffer: BufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL)
      val previousRenderType: RenderType = MeshDrawContext.getRenderType()
      try {
         for (path in paths) path.render(event, buffer, color, lineWidth)
      } finally {
         try {
            MeshDrawContext.setRenderType(ClientRenderTypes.worldLines())
            MeshBuffer.draw(buffer)
         } finally {
            MeshDrawContext.setRenderType(previousRenderType)
         }
      }
   }

   private fun sampleTrail(entity: Entity, sample: Boolean, partialTick: Float): List<Vec3> {
      val id = entity.getId()
      var trail = this.trailsByEntityId[id]
      if (trail == null) {
         if (this.trailsByEntityId.size >= MAX_TRACKED_PROJECTILES) {
            this.trailsByEntityId.remove(this.trailsByEntityId.keys.iterator().next())
         }
         trail = ArrayList()
         this.trailsByEntityId[id] = trail
      }
      if (sample) trail.add(entity.getPosition(partialTick))
      val limit = Math.max(5, Math.min(400, this.trailLengthSetting.getValue()!!))
      while (trail.size > limit) trail.removeFirst()
      return trail
   }

   private fun refreshTrailLevel(level: ClientLevel) {
      if (this.trailLevel !== level) {
         this.trailsByEntityId.clear()
         this.trailLevel = level
      }
      this.trailsByEntityId.keys.removeIf { id: Int -> level.getEntity(id) == null }
   }

   override fun onDisable() {
      this.trailsByEntityId.clear()
      this.trailLevel = null
      this.previousPlayerVelocity = Vec3.ZERO
      this.previousBowPower = 0.0F
   }

   @EventHandler
   private fun onConnectScreenConnectEvent(event: ConnectScreenConnectEvent) {
      this.onDisable()
   }

   @EventHandler
   private fun onLocalPlayerPreTick(event: LocalPlayerPreTickEvent) {
      if (this.isWorldUnavailable()) return
      val player: Player = ClientAccess.collectiveMinecraft.player!!
      this.previousPlayerVelocity = player.getDeltaMovement()
      this.previousBowPower = if (player.isUsingItem() && player.getUseItem().getItem() is BowItem)
         BowItem.getPowerForTime(player.getTicksUsingItem()) else 0.0F
      this.refreshTrailLevel(ClientAccess.collectiveMinecraft.level!!)
   }

   fun getPreviousBowPower(): Float {
      return this.previousBowPower
   }

   private inner class TrajectoryPath(
      val source: Entity,
      val predicted: Boolean
   ) {
      private val points: MutableList<Vec3> = ArrayList()
      var trail: List<Vec3> = Collections.emptyList()
      var blockHitBounds: AABB? = null
      var hitEntity: Entity? = null

      fun simulate() {
         this.appendPosition()
         for (tick in 0 until MAX_SIMULATION_TICKS) {
            val hit: HitResult? = this@TrajectoriesModule.simulator.step()
            if (hit != null) {
               if (hit is BlockHitResult && hit.getType() == HitResult.Type.BLOCK) {
                  this.blockHitBounds = AABB(hit.getLocation(), hit.getLocation())
                  val axis: Direction.Axis = hit.getDirection().getAxis()
                  this.blockHitBounds = this.blockHitBounds!!.inflate(if (axis == Direction.Axis.X) 0.0 else 0.25,
                     if (axis == Direction.Axis.Y) 0.0 else 0.25, if (axis == Direction.Axis.Z) 0.0 else 0.25)
                  this.points.add(hit.getLocation())
               } else if (hit is EntityHitResult) {
                  this.hitEntity = hit.getEntity()
                  this.points.add(hit.getLocation())
               }
               break
            }
            this.appendPosition()
         }
      }

      private fun appendPosition() {
         this.points.add(Vec3(this@TrajectoriesModule.simulator.position.x,
            this@TrajectoriesModule.simulator.position.y, this@TrajectoriesModule.simulator.position.z))
      }

      fun render(event: WorldRenderEvent.Main, buffer: BufferBuilder, color: Color, lineWidth: Float) {
         var poses: PoseStack = event.poseStack!!
         if (this.predicted && this.source === ClientAccess.collectiveMinecraft.player!! &&
         ClientAccess.collectiveMinecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            poses = PoseStack()
            RenderUtils.applyInverseViewBobbing(poses, event.partialTick)
            RenderUtils.applyCameraRotation(poses)
         }
         val last = this.drawPolyline(poses, buffer, this.trail, null, color.getRGB(), lineWidth)
         this.drawPolyline(poses, buffer, this.points, last, color.getRGB(), lineWidth)
         if (this.blockHitBounds != null) {
            WorldBoxRenderer.fillBox(poses, this.blockHitBounds!!, ColorUtils.withAlpha(color, 60))
            WorldBoxRenderer.outlineBox(poses, this.blockHitBounds!!, color, lineWidth)
         }
         if (this.hitEntity != null && this.source === ClientAccess.collectiveMinecraft.player) {
            val bounds: AABB = BoundingBoxUtils.getRenderBounds(this.hitEntity!!, event.partialTick)
            WorldBoxRenderer.fillBox(poses, bounds, ColorUtils.withAlpha(color, 60))
         }
      }

      private fun drawPolyline(poses: PoseStack, buffer: BufferBuilder, path: List<Vec3>, previous: Vec3?, color: Int, lineWidth: Float): Vec3? {
         var previous = previous
         for (point in path) {
            if (previous != null) RenderUtils.INSTANCE.addWorldLine(poses, buffer, previous, point, lineWidth, color, color)
            previous = point
         }
         return previous
      }
   }

   companion object {
      private const val MAX_SIMULATION_TICKS = 400
      private const val MAX_TRACKED_PROJECTILES = 256
   }
}
