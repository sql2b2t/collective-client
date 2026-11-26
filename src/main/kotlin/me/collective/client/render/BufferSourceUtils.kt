package me.collective.client.render

import me.collective.client.render.MultiBufferSource

object BufferSourceUtils {
   @JvmStatic
   fun cleanup(source: MultiBufferSource?) {
      if (source is BufferSourceCleanup) {
         source.`collective$cleanAll`()
      }
   }
}
