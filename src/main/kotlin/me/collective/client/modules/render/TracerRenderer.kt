package me.collective.client.modules.render

import me.collective.client.ClientAccess

import com.mojang.blaze3d.vertex.PoseStack

abstract class TracerRenderer(protected val module: TracersModule) : ClientAccess {

   abstract fun render(poseStack: PoseStack, tickDelta: Float)

}
