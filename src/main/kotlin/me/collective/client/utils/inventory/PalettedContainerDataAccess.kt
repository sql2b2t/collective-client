package me.collective.client.utils.inventory

import net.minecraft.util.BitStorage
import net.minecraft.world.level.chunk.Palette
import net.minecraft.world.level.chunk.PalettedContainer

interface PalettedContainerDataAccess {
   fun `collective$storage`(): BitStorage

   fun `collective$palette`(): Palette<*>

   companion object {
      @JvmStatic
      fun getStorage(container: PalettedContainer<*>): BitStorage {
         val data = (container as PalettedContainerAccess).`collective$data`()
         return data.`collective$storage`()
      }

      @JvmStatic
      fun getPalette(container: PalettedContainer<*>): Palette<*> {
         val data = (container as PalettedContainerAccess).`collective$data`()
         return data.`collective$palette`()
      }
   }
}
