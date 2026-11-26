package me.collective.client.utils.inventory

import com.mojang.blaze3d.vertex.VertexConsumer

class NoOpVertexConsumer : VertexConsumer {
   override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
      return this
   }

   override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
      return this
   }

   override fun setColor(argb: Int): VertexConsumer {
      return this
   }

   override fun setUv(u: Float, v: Float): VertexConsumer {
      return this
   }

   override fun setUv1(u: Int, v: Int): VertexConsumer {
      return this
   }

   override fun setUv2(u: Int, v: Int): VertexConsumer {
      return this
   }

   override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer {
      return this
   }

   override fun setLineWidth(width: Float): VertexConsumer {
      return this
   }

   companion object {
      @JvmField
      val INSTANCE = NoOpVertexConsumer()
   }
}
