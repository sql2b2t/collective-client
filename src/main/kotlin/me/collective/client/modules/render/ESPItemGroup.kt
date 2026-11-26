package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.render.FontRenderer
import me.collective.client.utils.combat.BoundingBoxUtils
import java.util.TreeMap
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class ESPItemGroup(itemEntity: ItemEntity) : ClientAccess {
   private val itemCounts: MutableMap<String, Int> = TreeMap<String, Int>(Comparator.reverseOrder())
   private val groupingBounds: AABB
   private var renderBounds: AABB
   private var maximumTextWidth: Float = 0.0f

   init {
      val partialTick: Float = ClientAccess.collectiveMinecraft.deltaTracker.getGameTimeDeltaPartialTick(true)
      this.groupingBounds = AABB.ofSize(itemEntity.getPosition(partialTick), 5.0, 5.0, 5.0)
      this.renderBounds = BoundingBoxUtils.getRenderBounds(itemEntity, partialTick)
      this.tryAddItem(itemEntity)
   }

   fun getGroupingBounds(): AABB {
      return this.groupingBounds
   }

   fun getRenderBounds(): AABB {
      return this.renderBounds
   }

   fun tryAddItem(itemEntity: ItemEntity): Boolean {
      if (!this.groupingBounds.intersects(itemEntity.boundingBox)) {
         return false
      }
      val partialTick: Float = ClientAccess.collectiveMinecraft.deltaTracker.getGameTimeDeltaPartialTick(true)
      val itemName: String = itemEntity.name.string
      this.itemCounts.merge(itemName, itemEntity.item.count, Integer::sum)
      val formattedTextWidth: Float = FontRenderer.INSTANCE.getWidth(formatItemCount(itemName, this.itemCounts[itemName]!!))
      this.maximumTextWidth = Math.max(formattedTextWidth, this.maximumTextWidth)
      val movement = Vec3(
         itemEntity.x - itemEntity.xo,
         itemEntity.y - itemEntity.yo,
         itemEntity.z - itemEntity.zo
      )

      if (movement.lengthSqr() < 0.0001) {
         this.renderBounds = this.renderBounds.minmax(BoundingBoxUtils.getRenderBounds(itemEntity, partialTick))
      }
      return true
   }

   fun getItemCounts(): Map<String, Int> {
      return this.itemCounts
   }

   fun getMaximumTextWidth(): Float {
      return this.maximumTextWidth
   }

   companion object {

      @JvmStatic
      fun formatItemCount(name: String, count: Int): String {
         return if (count <= 1) name else name + " x" + count
      }

   }
}
