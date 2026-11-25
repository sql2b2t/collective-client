package me.collective.client.utils.world

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB

data class HoleInfo(
   @get:JvmName("safety") val safety: Safety,
   @get:JvmName("pos") val pos: BlockPos,
   @get:JvmName("bounds") val bounds: AABB,
   @get:JvmName("trapped") val trapped: Boolean
) {

   enum class Safety {
      SAFE,
      UNSAFE
   }
}
