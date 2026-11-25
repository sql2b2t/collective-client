package me.collective.client.render

import net.minecraft.client.renderer.MultiBufferSource

object BufferSourceUtils {
   @JvmStatic
   fun cleanup(source: MultiBufferSource?) {
      if (source is BufferSourceCleanup) {
         source.`collective$cleanAll`()
      }
   }
}
