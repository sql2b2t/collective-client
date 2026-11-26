package me.collective.client.modules.render

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.events.HudRenderEvent
import me.collective.client.modules.client.FontsModule
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.render.RenderUtils
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerState
import me.collective.client.utils.inventory.PotionUtils
import java.awt.Color
import java.util.ArrayList
import java.util.Comparator
import java.util.Locale
import net.minecraft.client.gui.Font.DisplayMode
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.util.ARGB
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector4f

internal class ESP2DRenderer(private val module: ESPModule) : ClientAccess {
   private val targets: MutableList<Entity> = ArrayList()
   private val projection: Matrix4f = Matrix4f()
   private val screenBounds: Vector4f = Vector4f()
   private val corners: Array<Vector4f> = Array(8) { Vector4f() }

   internal fun render(event: HudRenderEvent, entities: Iterable<Entity>) {
      val camera = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().position()
      this.targets.clear()
      for (entity in entities) {
         if (this.module.renderHelper.shouldRenderEntity(entity) && entity !is ThrownEnderpearl) {
            this.targets.add(entity)
         }
      }
      this.targets.sortWith(Comparator.comparingDouble<Entity> { entity -> -entity.distanceToSqr(camera) })
      val view = Matrix4f(event.poseStack!!.last().pose())
      RenderUtils.applyCameraRotation(view)
      RenderUtils.WORLD_PROJECTION_MATRIX.mul(view, this.projection)
      val poses: PoseStack = event.poseStack!!
      poses.pushPose()
      try {
         val scale = 1.0F / ClientAccess.collectiveMinecraft.getWindow().guiScale
         poses.scale(scale, scale, scale)
         for (entity in this.targets) this.renderEntity(event, poses, entity, camera)
         GuiDrawUtils.flush()
      } finally {
         poses.popPose()
      }
   }

   private fun renderEntity(event: HudRenderEvent, poses: PoseStack, entity: Entity, camera: Vec3) {
      var bounds: AABB = BoundingBoxUtils.getRenderBounds(entity, event.partialTick)
      if (entity is Player) bounds = bounds.inflate(0.125, 0.075, 0.125).move(0.0, 0.1, 0.0)
      else if (entity is ThrownExperienceBottle) bounds = bounds.move(0.0, 0.2, 0.0)
      if (!projectBounds(this.projection, bounds, camera, ClientAccess.collectiveMinecraft.getWindow().getScreenWidth(),
         ClientAccess.collectiveMinecraft.getWindow().getScreenHeight(), this.corners, this.screenBounds)) return
      if (entity is ItemEntity || entity is ThrownExperienceBottle) squareBounds(this.screenBounds)
      val color: Int = this.module.renderHelper.getBaseEntityColor(entity).getRGB()
      val deathTicks = if (entity is LivingEntity) entity.deathTime else 0
      val lifeOpacity = lifecycleOpacity(entity.tickCount, deathTicks, event.partialTick)
      val distance = camera.distanceTo(Vec3(Mth.clamp(camera.x, bounds.minX, bounds.maxX),
         Mth.clamp(camera.y, bounds.minY, bounds.maxY), Mth.clamp(camera.z, bounds.minZ, bounds.maxZ)))
      val distanceOpacity = 0.25F + Mth.clamp((distance.toFloat() - 0.5F) / 1.5F, 0.0F, 1.0F) * 0.75F
      val opacity = lifeOpacity * distanceOpacity
      val outline = ARGB.multiplyAlpha(ARGB.color(this.module.entityOutlineSetting.getValue(), color), opacity)
      val shadow = when (this.module.entityShadowSetting.getValue()) {
         ESPModule.ShadowMode.BLACK -> outline and 0xFF000000.toInt()
         ESPModule.ShadowMode.DARK -> ColorUtils.darken(Color(outline, true), 0.25F).getRGB()
         ESPModule.ShadowMode.NONE -> 0
      }
      val fill = ARGB.multiplyAlpha(ARGB.color(this.module.entityFillSetting.getValue(), color), opacity)
      val tint = ARGB.multiplyAlpha(ARGB.color(this.module.entityTintSetting.getValue(), color), opacity)
      val left = this.screenBounds.x
      val top = this.screenBounds.y
      val right = this.screenBounds.z
      val bottom = this.screenBounds.w
      drawFill(poses, this.screenBounds, fill, tint)
      val length: Float = this.module.entityLengthSetting.getValue()!!
      drawBox(poses, this.screenBounds, 3.0F, length, shadow)
      drawBox(poses, this.screenBounds, 1.0F, length, outline)
      val health: Float = if (entity is PlayerState) entity.`collective$getHealthFactor`()
         else if (entity is LivingEntity) EntityUtils.getHealthFraction(entity) else 0.0F
      if (entity is LivingEntity && this.module.healthBarSetting.getValue()!!) {
         val healthColor = if (this.module.healthColorSetting.getValue()!!)
            ColorUtils.withAlpha(Color(ColorUtils.getHealthColor(health.toDouble()), true), Math.round(255.0F * lifeOpacity)).getRGB() else outline
         GuiDrawUtils.fillRect(poses, left - 5.0F, top - 1.0F, left - 1.0F, bottom + 1.0F, shadow)
         GuiDrawUtils.fillRect(poses, left - 4.0F, top, left - 2.0F, bottom, ColorUtils.withAlpha(Color(healthColor, true), Math.round(50.0F * lifeOpacity)))
         GuiDrawUtils.fillRect(poses, left - 4.0F, bottom - (bottom - top) * health, left - 2.0F, bottom, healthColor)
      }
      this.drawInformation(poses, entity, health, (Math.round(255.0F * lifeOpacity) shl 24) or 0xFFFFFF)
   }

   private fun drawInformation(poses: PoseStack, entity: Entity, health: Float, textColor: Int) {
      val font: FontRenderer = FontRenderer.INSTANCE
      val scale = if (FontsModule.INSTANCE!!.isToggled()) 1.0F else 1.6F
      val center = (this.screenBounds.x + this.screenBounds.z) * 0.5F
      if (this.module.nameSetting.getValue()!!) {
         val name: Component = if (entity is Player) Component.literal(entity.getGameProfile().name())
            else if (entity is ThrownExperienceBottle) Component.literal("Exp") else entity.getName()
         drawText(poses, name, center, this.screenBounds.y - 2.0F - font.getHeight() * scale, scale, 0.5F, textColor)
      }
      val below: MutableList<Component> = ArrayList()
      if (this.module.distanceSetting.getValue()!!) {
         val distance = ClientAccess.collectiveMinecraft.player!!.distanceTo(entity)
         below.add(Component.literal(String.format(Locale.ROOT, "%.1f%s", if (distance > 1000.0) distance / 1000.0 else distance, if (distance > 1000.0) "km" else "m")))
      }
      if (entity is LivingEntity) {
         if (this.module.heldItemSetting.getValue()!! && !entity.getMainHandItem().isEmpty()) below.add(entity.getMainHandItem().getHoverName())
         if (this.module.effectsSetting.getValue()!!) {
            var y = this.screenBounds.y
            val effects: MutableList<String> = ArrayList()
            val activeEffects = PotionUtils.getEffectsByParticleColor(entity)
            if (activeEffects.contains(MobEffects.RESISTANCE) && activeEffects.contains(MobEffects.SLOWNESS)) effects.add("Resistance")
            if (activeEffects.contains(MobEffects.STRENGTH)) effects.add("Strength")
            if (activeEffects.contains(MobEffects.WEAKNESS)) effects.add("Weak")
            for (effect in effects) {
               drawText(poses, Component.literal(effect), this.screenBounds.z + 3.0F, y, scale, 0.0F, textColor)
               y += font.getHeight() * scale
            }
         }
         if (this.module.healthBarSetting.getValue()!! && this.module.healthTextSetting.getValue()!!) {
            val text = Component.literal(Integer.toString(Math.round(entity.getHealth() + entity.getAbsorptionAmount())))
            drawText(poses, text, this.screenBounds.x - 6.0F,
               this.screenBounds.y + (this.screenBounds.w - this.screenBounds.y) * (1.0F - health), scale, 1.0F, textColor)
         }
      }
      var y = this.screenBounds.w + 2.0F
      for (line in below) {
         drawText(poses, line, center, y, scale, 0.5F, textColor)
         y += font.getHeight() * scale
      }
   }

   private companion object {
      private fun drawText(poses: PoseStack, text: Component, x: Float, y: Float, scale: Float, alignment: Float, color: Int) {
         poses.pushPose()
         try {
            poses.translate(x, y, 0.0F)
            poses.scale(scale, scale, 1.0F)
            FontRenderer.INSTANCE.drawWithShadow(poses, text, -FontRenderer.INSTANCE.getWidth(text) * alignment, 0.0F, color, DisplayMode.NORMAL)
         } finally {
            poses.popPose()
         }
      }

      private fun lifecycleOpacity(ageTicks: Int, deathTicks: Int, partialTick: Float): Float {
         var opacity = Mth.clamp((ageTicks - 1 + partialTick) / 6.0F, 0.0F, 1.0F)
         if (deathTicks > 0) opacity *= 1.0F - Mth.clamp((deathTicks - 1 + partialTick) / 8.0F, 0.0F, 1.0F)
         return opacity * opacity * (3.0F - 2.0F * opacity)
      }

      private fun squareBounds(bounds: Vector4f) {
         val centerX = (bounds.x + bounds.z) * 0.5F
         val centerY = (bounds.y + bounds.w) * 0.5F
         val radius = Math.max(bounds.z - bounds.x, bounds.w - bounds.y) * 0.5F
         bounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
      }

      private fun drawFill(poses: PoseStack, bounds: Vector4f, fill: Int, tint: Int) {
         GuiDrawUtils.fillRect(poses, bounds.x, bounds.y, bounds.z, bounds.w, fill)
         val x = (bounds.x + 3.0F).toInt()
         val y = (bounds.y + 3.0F).toInt()
         val width = (bounds.z - bounds.x - 7.0F).toInt()
         val height = (bounds.w - bounds.y - 7.0F).toInt()
         if (width <= 0 || height <= 0) return
         val upper = height / 2
         val lower = height - upper
         val transparent = tint and 0xFFFFFF
         GuiDrawUtils.fillGradientRect(poses.last().pose(), x.toFloat(), y.toFloat(), width.toFloat(), upper.toFloat(), tint, tint, transparent, transparent)
         GuiDrawUtils.fillGradientRect(poses.last().pose(), x.toFloat(), (y + upper).toFloat(), width.toFloat(), lower.toFloat(), transparent, transparent, tint, tint)
      }

      private fun drawBox(poses: PoseStack, bounds: Vector4f, width: Float, length: Float, color: Int) {
         if ((color ushr 24) == 0 || width <= 0.0F) return
         val x = bounds.x
         val y = bounds.y
         val right = bounds.z
         val bottom = bounds.w
         val half = width * 0.5F
         val horizontal = (right - x) * length * 0.5F
         val vertical = (bottom - y) * length * 0.5F
         for (sideX in 0 until 2) {
            val edgeX = if (sideX == 0) x else right
            val endX = if (edgeX == x) x + horizontal else right - horizontal
            for (sideY in 0 until 2) {
               val edgeY = if (sideY == 0) y else bottom
               val endY = if (edgeY == y) y + vertical else bottom - vertical
               GuiDrawUtils.fillRect(poses, Math.min(edgeX, endX) - half, edgeY - half, Math.max(edgeX, endX) + half, edgeY + half, color)
               GuiDrawUtils.fillRect(poses, edgeX - half, Math.min(edgeY, endY) - half, edgeX + half, Math.max(edgeY, endY) + half, color)
            }
         }
      }

      private fun projectBounds(projection: Matrix4f, box: AABB, camera: Vec3, width: Int, height: Int, corners: Array<Vector4f>, result: Vector4f): Boolean {
         if (width <= 0 || height <= 0) return false
         for (index in 0 until 8) {
            corners[index].set(((if (index and 1 == 0) box.minX else box.maxX) - camera.x).toFloat(),
               ((if (index and 2 == 0) box.minY else box.maxY) - camera.y).toFloat(),
               ((if (index and 4 == 0) box.minZ else box.maxZ) - camera.z).toFloat(), 1.0F)
            projection.transform(corners[index])
            if (!corners[index].isFinite()) return false
         }
         result.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
         var visible = false
         for (index in 0 until 8) {
            val point = corners[index]
            if (point.w > 0.0001F && point.z + point.w >= 0.0F) {
               includePoint(point.x, point.y, point.w, width, height, result)
               visible = true
            }
            var axis = 1
            while (axis <= 4) {
               val otherIndex = index xor axis
               if (otherIndex > index) {
                  val other = corners[otherIndex]
                  val distance = point.z + point.w
                  val otherDistance = other.z + other.w
                  if ((distance < 0.0F) != (otherDistance < 0.0F)) {
                     val fraction = distance / (distance - otherDistance)
                     val w = Mth.lerp(fraction, point.w, other.w)
                     if (w > 0.0001F) {
                        includePoint(Mth.lerp(fraction, point.x, other.x), Mth.lerp(fraction, point.y, other.y), w, width, height, result)
                        visible = true
                     }
                  }
               }
               axis = axis shl 1
            }
         }
         if (!visible || !result.x.isFinite() || !result.y.isFinite() || !result.z.isFinite() || !result.w.isFinite() ||
         result.z < 0.0F || result.x > width || result.w < 0.0F || result.y > height) return false
         result.set(Mth.clamp(result.x, 0.0F, width.toFloat()), Mth.clamp(result.y, 0.0F, height.toFloat()),
            Mth.clamp(result.z, 0.0F, width.toFloat()), Mth.clamp(result.w, 0.0F, height.toFloat()))
         return result.z > result.x && result.w > result.y
      }

      private fun includePoint(x: Float, y: Float, w: Float, width: Int, height: Int, result: Vector4f) {
         val screenX = (x / w + 1.0F) * width * 0.5F
         val screenY = (1.0F - y / w) * height * 0.5F
         result.x = Math.min(result.x, screenX)
         result.y = Math.min(result.y, screenY)
         result.z = Math.max(result.z, screenX)
         result.w = Math.max(result.w, screenY)
      }
   }
}
