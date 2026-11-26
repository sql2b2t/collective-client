package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.render.LineMeshRenderer
import me.collective.client.utils.math.Vec3Utils

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.phys.Vec3

class LineTracerRenderer(module: TracersModule) : TracerRenderer(module) {

   override fun render(poseStack: PoseStack, unusedTickDelta: Float) {
      LineMeshRenderer.begin(poseStack, this.module.lineWidthSetting.value!!)
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val players = level.players().iterator()

      while (players.hasNext()) {
         val player = players.next() as AbstractClientPlayer

         if (!this.module.shouldSkip(player)) {
            val geometryModule: TracersModule = this.module

            val endpoints = geometryModule.getEndpoints(player)
            val hitboxSetting = this.module.hitboxSetting
            val hitbox = hitboxSetting.value!!
            val configuredHitboxFraction: Float = hitbox.heightFraction
            val start: Vec3 = endpoints.start
            val capturedEnd: Vec3 = endpoints.end
            val hitboxPosition: Vec3 = Vec3Utils.lerp(
               configuredHitboxFraction, start, capturedEnd
            )
            val color = this.module.getColor(player, hitboxPosition)
            LineMeshRenderer.addTracer(hitboxPosition, color)
            val stemSetting = this.module.stemSetting
            val drawStem = stemSetting.value
            if (drawStem!!) {

               val stemStart: Vec3 = endpoints.start
               LineMeshRenderer.addLine(
                  stemStart, endpoints.end, color
               )
            }
         }
      }

      LineMeshRenderer.draw()
   }

}
