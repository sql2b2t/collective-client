package me.collective.client.managers

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import java.util.function.Supplier
import me.collective.client.ClientAccess
import kotlin.math.max

class RenderTargetRegistry private constructor() : ClientAccess {
   private class ScaledTarget(val target: RenderTarget, val scale: Int)

   companion object {
      private val renderTargets = ObjectArrayList<ScaledTarget>()

      @JvmStatic
      @JvmOverloads
      fun createRenderTarget(name: String, scale: Int = 1): RenderTarget {
         val window = ClientAccess.collectiveMinecraft.window
         val target = TextureTarget(name, max(1, window.width / scale), max(1, window.height / scale), true, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM)
         renderTargets.add(ScaledTarget(target, scale))
         return target
      }

      @JvmStatic
      fun createLazyRenderTarget(name: String): Supplier<RenderTarget> {
         val cachedTarget = arrayOfNulls<RenderTarget>(1)
         return Supplier {
            if (cachedTarget[0] == null) {
               cachedTarget[0] = createRenderTarget(name)
            }
            cachedTarget[0]!!
         }
      }

      @JvmStatic
      fun resizeRenderTargets(width: Int, height: Int) {
         renderTargets.forEach { entry ->
            entry.target.resize(max(1, width / entry.scale), max(1, height / entry.scale))
         }
      }
   }
}
